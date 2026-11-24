package com.qux.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A connected agent (Claude Code, Cursor, a script...): one row per agent and
 * user, the one the user sees in "My account" and can disconnect. It calls
 * the API with its token on behalf of the user, with the scopes the user
 * approved: always apps:read, and apps:write when the agent asked for it.
 *
 * Only the SHA-256 of the tokens is stored, a token itself is shown once.
 * The token never grants more than the user has: appIDs only restricts what
 * the Team ACL allows.
 *
 * Kinds:
 *  - oauth: from the OAuth login, a short access token and a refresh token,
 *           both replaced in this row on every refresh
 *  - pat:   a personal access token created by the user, no refresh
 */
public class AgentToken extends Model {

	public static final String KIND_OAUTH = "oauth";

	public static final String KIND_PAT = "pat";

	public static final String PREFIX_ACCESS = "qux_at_";

	public static final String PREFIX_REFRESH = "qux_rt_";

	public static final String PREFIX_PAT = "qux_pat_";

	/**
	 * Read the apps of the user. Every token has it.
	 */
	public static final String SCOPE_READ = "apps:read";

	/**
	 * Create apps and add screens to them, only when the agent asked for it
	 * and the user approved it.
	 */
	public static final String SCOPE_WRITE = "apps:write";

	public static final List<String> SCOPES_SUPPORTED = Arrays.asList(SCOPE_READ, SCOPE_WRITE);

	public static final long ACCESS_TTL = 60L * 60 * 1000;

	public static final long REFRESH_TTL = 60L * 24 * 60 * 60 * 1000;

	public static final int PAT_DEFAULT_DAYS = 90;

	public static final int PAT_MAX_DAYS = 365;

	public static final String USER_ID = "userID";

	public static final String CLIENT_ID = "clientID";

	public static final String NAME = "name";

	public static final String KIND = "kind";

	public static final String TOKEN_HASH = "tokenHash";

	public static final String REFRESH_HASH = "refreshHash";

	/**
	 * The refresh token before the last refresh. When it comes back, it was
	 * stolen, see OAuthREST.checkReplay().
	 */
	public static final String PREVIOUS_REFRESH_HASH = "previousRefreshHash";

	public static final String PREFIX = "prefix";

	public static final String APP_IDS = "appIDs";

	public static final String RESOURCE = "resource";

	public static final String SCOPES = "scopes";

	public static final String EXPIRES_AT = "expiresAt";

	public static final String REFRESH_EXPIRES_AT = "refreshExpiresAt";

	public static final String LAST_USED_AT = "lastUsedAt";

	public static final String LAST_IP = "lastIp";

	public static final String REVOKED_AT = "revokedAt";

	public static final String REVOKED_REASON = "revokedReason";

	/**
	 * A Mongo date for the TTL index: the rows are kept a while after they
	 * expired, so the user still sees them in the list, then Mongo removes them.
	 */
	public static final String EXPIRE_AT_DATE = "expireAtDate";

	public static final long KEEP_AFTER_EXPIRY = 30L * 24 * 60 * 60 * 1000;

	/**
	 * The scopes of a space separated scope parameter (RFC 6749 3.3).
	 */
	public static JsonArray toScopes(String scope) {
		return toScopes(scope != null ? Arrays.asList(scope.trim().split("\s+")) : new ArrayList<>());
	}

	public static JsonArray toScopes(JsonArray scopes) {
		List<String> list = new ArrayList<>();
		if (scopes != null) {
			for (Object scope : scopes) {
				if (scope instanceof String) {
					list.add((String) scope);
				}
			}
		}
		return toScopes(list);
	}

	/**
	 * The supported ones, in a fixed order. apps:read is always there: an
	 * agent that cannot read cannot do anything.
	 */
	private static JsonArray toScopes(List<String> requested) {
		JsonArray result = new JsonArray();
		for (String scope : SCOPES_SUPPORTED) {
			if (SCOPE_READ.equals(scope) || requested.contains(scope)) {
				result.add(scope);
			}
		}
		return result;
	}

	/**
	 * The scopes of a token or grant. The rows from before the scopes can
	 * only read.
	 */
	public static JsonArray getScopes(JsonObject row) {
		JsonArray scopes = row != null ? row.getJsonArray(SCOPES) : null;
		return scopes != null ? toScopes(scopes) : new JsonArray().add(SCOPE_READ);
	}

	/**
	 * The scopes the user approved: the requested ones the user did not
	 * uncheck on the consent page. null keeps all requested ones.
	 */
	public static JsonArray narrowScopes(JsonArray requested, JsonArray approved) {
		if (approved == null) {
			return requested;
		}
		JsonArray result = new JsonArray();
		for (Object scope : toScopes(approved)) {
			if (requested.contains(scope)) {
				result.add(scope);
			}
		}
		return toScopes(result);
	}

	public static String toScopeString(JsonArray scopes) {
		List<String> list = new ArrayList<>();
		for (Object scope : scopes) {
			list.add(String.valueOf(scope));
		}
		return String.join(" ", list);
	}

	public static boolean isActive(JsonObject token, long now) {
		return token != null
				&& token.getLong(REVOKED_AT, 0L) == 0
				&& token.getLong(EXPIRES_AT, 0L) > now;
	}

	public static boolean canRefresh(JsonObject token, long now) {
		return token != null
				&& token.getLong(REVOKED_AT, 0L) == 0
				&& token.getLong(REFRESH_EXPIRES_AT, 0L) > now;
	}

	/**
	 * The token as the user sees it in the list of connected agents, without
	 * any hash.
	 */
	public static JsonObject toPublic(JsonObject token) {
		return new JsonObject()
				.put("id", token.getString("_id"))
				.put(NAME, token.getString(NAME))
				.put(KIND, token.getString(KIND))
				.put(CLIENT_ID, token.getString(CLIENT_ID))
				.put(PREFIX, token.getString(PREFIX))
				.put(APP_IDS, token.getJsonArray(APP_IDS))
				.put(SCOPES, getScopes(token))
				.put(FIELD_CREATED, token.getLong(FIELD_CREATED, 0L))
				.put(EXPIRES_AT, token.getLong(KIND_OAUTH.equals(token.getString(KIND)) ? REFRESH_EXPIRES_AT : EXPIRES_AT, 0L))
				.put(LAST_USED_AT, token.getLong(LAST_USED_AT, 0L))
				.put(LAST_IP, token.getString(LAST_IP));
	}

	/*********************************************************************
	 *  Queries
	 *********************************************************************/

	public static JsonObject findByTokenHash(String hash) {
		return new JsonObject().put(TOKEN_HASH, hash);
	}

	public static JsonObject findByRefreshHash(String hash) {
		return new JsonObject().put(REFRESH_HASH, hash);
	}

	public static JsonObject findByPreviousRefreshHash(String hash) {
		return new JsonObject().put(PREVIOUS_REFRESH_HASH, hash);
	}

	/**
	 * The agents of the user that are not revoked and can still be used.
	 */
	public static JsonObject findActiveByUser(String userID, long now) {
		return findActiveByUser(userID)
				.put("$or", new JsonArray()
						.add(new JsonObject().put(EXPIRES_AT, new JsonObject().put("$gt", now)))
						.add(new JsonObject().put(REFRESH_EXPIRES_AT, new JsonObject().put("$gt", now))));
	}

	public static JsonObject findActiveByUser(String userID) {
		return new JsonObject()
				.put(USER_ID, userID)
				.put(REVOKED_AT, 0L);
	}

	public static JsonObject findActiveByIdAndUser(String id, String userID) {
		return findActiveByUser(userID).put("_id", id);
	}

	public static JsonObject revoke(String reason) {
		return new JsonObject().put("$set", new JsonObject()
				.put(REVOKED_AT, System.currentTimeMillis())
				.put(REVOKED_REASON, reason));
	}
}
