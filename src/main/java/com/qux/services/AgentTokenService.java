package com.qux.services;

import com.qux.acl.Acl;
import com.qux.model.AgentToken;
import com.qux.model.Team;
import com.qux.util.DB;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * Creates, hashes and revokes the tokens of agents. See AgentToken.
 *
 * Tokens are 32 random bytes, base64url, with a prefix (qux_at_, qux_rt_,
 * qux_pat_) so secret scanners and the AgentACL can tell them apart
 * from the JWT of the web app. Only their SHA-256 is stored.
 */
public class AgentTokenService {

	private static final Logger logger = LoggerFactory.getLogger(AgentTokenService.class);

	private static final SecureRandom random = new SecureRandom();

	private final MongoClient mongo;

	private final String token_db, team_db;

	public AgentTokenService(MongoClient mongo) {
		this.mongo = mongo;
		this.token_db = DB.getTable(AgentToken.class);
		this.team_db = DB.getTable(Team.class);
	}

	/*********************************************************************
	 *  Crypto
	 *********************************************************************/

	public static String randomToken(String prefix) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String randomId() {
		byte[] bytes = new byte[16];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String hash(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : bytes) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * PKCE S256 (RFC 7636): BASE64URL(SHA256(verifier)) == challenge
	 */
	public static boolean verifyPKCE(String verifier, String challenge) {
		if (verifier == null || challenge == null || verifier.length() < 43 || verifier.length() > 128) {
			return false;
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
			String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
			return MessageDigest.isEqual(computed.getBytes(StandardCharsets.US_ASCII), challenge.getBytes(StandardCharsets.US_ASCII));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * A BSON date, which the TTL indexes need.
	 */
	public static JsonObject toMongoDate(long ms) {
		return new JsonObject().put("$date", Instant.ofEpochMilli(ms).toString());
	}

	public static boolean isAgentToken(String token) {
		return token != null && (token.startsWith(AgentToken.PREFIX_ACCESS) || token.startsWith(AgentToken.PREFIX_PAT));
	}

	/*********************************************************************
	 *  Issue
	 *********************************************************************/

	/**
	 * Whether the user may read every app of the list (a Team entry), so a
	 * token is never made for an app of someone else. null means all apps of
	 * the user and is fine. The ACL is checked again on every call of the
	 * token, this only keeps such tokens from being issued.
	 */
	public void hasAccessToApps(String userID, JsonArray appIDs, Handler<Boolean> handler) {
		if (appIDs == null || appIDs.isEmpty()) {
			handler.handle(true);
			return;
		}
		Set<String> wanted = new HashSet<>();
		for (Object id : appIDs) {
			if (!(id instanceof String) || ((String) id).isEmpty()) {
				handler.handle(false);
				return;
			}
			wanted.add((String) id);
		}
		JsonObject query = new JsonObject()
				.put(Team.USER_ID, userID)
				.put(Team.APP_ID, new JsonObject().put("$in", new JsonArray(new ArrayList<>(wanted))))
				.put(Team.PERMISSION, new JsonObject().put("$gte", Acl.READ));
		mongo.find(team_db, query, res -> {
			if (res.failed()) {
				logger.error("hasAccessToApps() > could not load team", res.cause());
				handler.handle(false);
				return;
			}
			Set<String> found = new HashSet<>();
			for (JsonObject team : res.result()) {
				found.add(team.getString(Team.APP_ID));
			}
			handler.handle(found.containsAll(wanted));
		});
	}

	/**
	 * Connects an agent: a new row with an access and a refresh token.
	 *
	 * @return the OAuth token response (RFC 6749 5.1)
	 */
	public void issueOAuth(String userID, String clientID, String name, JsonArray appIDs, JsonArray scopes, String resource,
			Handler<AsyncResult<JsonObject>> handler) {

		long now = System.currentTimeMillis();
		String access = randomToken(AgentToken.PREFIX_ACCESS);
		String refresh = randomToken(AgentToken.PREFIX_REFRESH);

		JsonObject row = new JsonObject()
				.put(AgentToken.USER_ID, userID)
				.put(AgentToken.CLIENT_ID, clientID)
				.put(AgentToken.NAME, name)
				.put(AgentToken.KIND, AgentToken.KIND_OAUTH)
				.put(AgentToken.TOKEN_HASH, hash(access))
				.put(AgentToken.REFRESH_HASH, hash(refresh))
				.put(AgentToken.PREFIX, access.substring(0, AgentToken.PREFIX_ACCESS.length() + 4))
				.put(AgentToken.APP_IDS, appIDs)
				.put(AgentToken.SCOPES, scopes)
				.put(AgentToken.RESOURCE, resource)
				.put(AgentToken.FIELD_CREATED, now)
				.put(AgentToken.EXPIRES_AT, now + AgentToken.ACCESS_TTL)
				.put(AgentToken.REFRESH_EXPIRES_AT, now + AgentToken.REFRESH_TTL)
				.put(AgentToken.LAST_USED_AT, 0L)
				.put(AgentToken.REVOKED_AT, 0L)
				.put(AgentToken.EXPIRE_AT_DATE, toMongoDate(now + AgentToken.REFRESH_TTL + AgentToken.KEEP_AFTER_EXPIRY));

		mongo.insert(token_db, row, res -> {
			if (res.succeeded()) {
				handler.handle(Future.succeededFuture(toTokenResponse(access, refresh, scopes)));
			} else {
				logger.error("issueOAuth() > could not save token", res.cause());
				handler.handle(Future.failedFuture(res.cause()));
			}
		});
	}

	/**
	 * A refresh: a new access and refresh token in the same row, so the row
	 * stays the one connected agent. Refresh tokens rotate (OAuth 2.1 for
	 * public clients), the old one is kept as previousRefreshHash to detect
	 * a replay. Only one of two requests with the same refresh token wins.
	 *
	 * @return the token response, null when another request was faster
	 */
	public void rotate(JsonObject row, String refreshHash, Handler<AsyncResult<JsonObject>> handler) {
		long now = System.currentTimeMillis();
		String access = randomToken(AgentToken.PREFIX_ACCESS);
		String refresh = randomToken(AgentToken.PREFIX_REFRESH);

		JsonObject query = new JsonObject()
				.put("_id", row.getString("_id"))
				.put(AgentToken.REFRESH_HASH, refreshHash)
				.put(AgentToken.REVOKED_AT, 0L);
		JsonObject set = new JsonObject()
				.put(AgentToken.TOKEN_HASH, hash(access))
				.put(AgentToken.REFRESH_HASH, hash(refresh))
				.put(AgentToken.PREVIOUS_REFRESH_HASH, refreshHash)
				.put(AgentToken.PREFIX, access.substring(0, AgentToken.PREFIX_ACCESS.length() + 4))
				.put(AgentToken.EXPIRES_AT, now + AgentToken.ACCESS_TTL)
				.put(AgentToken.REFRESH_EXPIRES_AT, now + AgentToken.REFRESH_TTL)
				.put(AgentToken.EXPIRE_AT_DATE, toMongoDate(now + AgentToken.REFRESH_TTL + AgentToken.KEEP_AFTER_EXPIRY));

		mongo.findOneAndUpdate(token_db, query, new JsonObject().put("$set", set), res -> {
			if (res.failed()) {
				logger.error("rotate() > could not save token", res.cause());
				handler.handle(Future.failedFuture(res.cause()));
			} else if (res.result() == null) {
				handler.handle(Future.succeededFuture(null));
			} else {
				handler.handle(Future.succeededFuture(toTokenResponse(access, refresh, AgentToken.getScopes(row))));
			}
		});
	}

	private static JsonObject toTokenResponse(String access, String refresh, JsonArray scopes) {
		return new JsonObject()
				.put("access_token", access)
				.put("token_type", "Bearer")
				.put("expires_in", AgentToken.ACCESS_TTL / 1000)
				.put("refresh_token", refresh)
				.put("scope", AgentToken.toScopeString(scopes));
	}

	/**
	 * Creates a personal access token, shown once to the user.
	 */
	public void issuePAT(String userID, String name, JsonArray appIDs, JsonArray scopes, int days,
			Handler<AsyncResult<JsonObject>> handler) {

		long now = System.currentTimeMillis();
		String token = randomToken(AgentToken.PREFIX_PAT);
		JsonObject row = new JsonObject()
				.put(AgentToken.USER_ID, userID)
				.put(AgentToken.CLIENT_ID, "pat")
				.put(AgentToken.NAME, name)
				.put(AgentToken.KIND, AgentToken.KIND_PAT)
				.put(AgentToken.TOKEN_HASH, hash(token))
				.put(AgentToken.PREFIX, token.substring(0, AgentToken.PREFIX_PAT.length() + 4))
				.put(AgentToken.APP_IDS, appIDs)
				.put(AgentToken.SCOPES, scopes)
				.put(AgentToken.FIELD_CREATED, now)
				.put(AgentToken.EXPIRES_AT, now + days * 24L * 60 * 60 * 1000)
				.put(AgentToken.LAST_USED_AT, 0L)
				.put(AgentToken.REVOKED_AT, 0L)
				.put(AgentToken.EXPIRE_AT_DATE, toMongoDate(now + days * 24L * 60 * 60 * 1000 + AgentToken.KEEP_AFTER_EXPIRY));

		mongo.insert(token_db, row, res -> {
			if (res.succeeded()) {
				row.put("_id", res.result());
				handler.handle(Future.succeededFuture(AgentToken.toPublic(row).put("token", token)));
			} else {
				logger.error("issuePAT() > could not save token", res.cause());
				handler.handle(Future.failedFuture(res.cause()));
			}
		});
	}

	/*********************************************************************
	 *  Revoke
	 *********************************************************************/

	public void revokeAllOfUser(String userID, String reason) {
		if (userID == null || userID.isEmpty()) {
			return;
		}
		revokeWhere(AgentToken.findActiveByUser(userID), reason);
	}

	public void revokeById(String id, String reason) {
		revokeWhere(AgentToken.findById(id).put(AgentToken.REVOKED_AT, 0L), reason);
	}

	public void revokeWhere(JsonObject query, String reason) {
		revokeWhere(query, reason, null);
	}

	public void revokeWhere(JsonObject query, String reason, Handler<Long> handler) {
		mongo.updateCollectionWithOptions(token_db, query, AgentToken.revoke(reason), new UpdateOptions().setMulti(true), res -> {
			if (res.succeeded()) {
				logger.warn("revokeWhere() > revoked " + res.result().getDocModified() + " tokens, reason: " + reason);
				if (handler != null) {
					handler.handle(res.result().getDocModified());
				}
			} else {
				logger.error("revokeWhere() > could not revoke", res.cause());
				if (handler != null) {
					handler.handle(-1L);
				}
			}
		});
	}
}
