package com.qux.acl;

import com.qux.model.AgentToken;
import com.qux.model.User;
import com.qux.services.AgentTokenService;
import com.qux.util.DB;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The ACL of the agent tokens (qux_at_..., qux_pat_...). It follows the
 * pattern of the other ACLs, but has no user: it reads the bearer token of
 * the request and validates it against the token table. REST.getUser() is
 * synchronous and cannot look up a token in Mongo, so on success the user of
 * the token is put into the context (AGENT_USER) and getUser() returns it.
 *
 * The scope each method needs:
 *  - canRead:   apps:read  (GET /rest/ai/*, including the images of an app)
 *  - canCreate: apps:write (POST /rest/ai/apps.json)
 *  - canWrite:  apps:write (POST /rest/ai/apps/:appID/screens.json and .../images.json)
 *  - canDelete: no scope, agents cannot delete
 *
 * Any other Authorization header (the JWT of the web app) passes untouched,
 * the Team ACL of the route decides. Agent tokens do not reach the routes
 * that do not call this ACL: a token is no JWT.
 *
 * When the answer is false, the response (401 or 403) is already sent. If the
 * route has an appID, the apps the token is restricted to are checked too.
 */
public class AgentACL {

	public static final String AGENT_USER = "qux.agent.user";

	public static final String APP_ID = "appID";

	private static final Logger logger = LoggerFactory.getLogger(AgentACL.class);

	// do not write lastUsedAt on every request
	private static final long LAST_USED_RESOLUTION = 60L * 1000;

	private final MongoClient mongo;

	private final String token_db, user_db;

	public AgentACL(MongoClient mongo) {
		this.mongo = mongo;
		this.token_db = DB.getTable(AgentToken.class);
		this.user_db = DB.getTable(User.class);
	}

	public void canCreate(RoutingContext event, Handler<Boolean> handler) {
		check(event, AgentToken.SCOPE_WRITE, handler);
	}

	public void canRead(RoutingContext event, Handler<Boolean> handler) {
		check(event, AgentToken.SCOPE_READ, handler);
	}

	public void canWrite(RoutingContext event, Handler<Boolean> handler) {
		check(event, AgentToken.SCOPE_WRITE, handler);
	}

	public void canDelete(RoutingContext event, Handler<Boolean> handler) {
		check(event, null, handler);
	}

	/**
	 * @param scope the scope the token needs, null when no scope allows it
	 */
	private void check(RoutingContext event, String scope, Handler<Boolean> handler) {
		String token = getBearer(event);
		if (!AgentTokenService.isAgentToken(token)) {
			handler.handle(true);
			return;
		}

		long now = System.currentTimeMillis();
		mongo.findOne(token_db, AgentToken.findByTokenHash(AgentTokenService.hash(token)), null, res -> {
			if (res.failed()) {
				logger.error("check() > could not load token", res.cause());
				event.response().setStatusCode(500).end();
				handler.handle(false);
				return;
			}
			JsonObject row = res.result();
			if (!AgentToken.isActive(row, now)) {
				unauthorized(event, "invalid_token", "The token is unknown, expired or revoked");
				handler.handle(false);
				return;
			}
			loadUser(event, row, scope, now, handler);
		});
	}

	private void loadUser(RoutingContext event, JsonObject row, String scope, long now, Handler<Boolean> handler) {
		String userID = row.getString(AgentToken.USER_ID);
		mongo.findOne(user_db, User.findById(userID), null, res -> {
			JsonObject json = res.succeeded() ? res.result() : null;
			if (json == null || User.STATUS_RETIRED.equals(json.getString("status"))) {
				unauthorized(event, "invalid_token", "The user of the token does not exist anymore");
				handler.handle(false);
				return;
			}

			User user = new User(userID, json.getString("name"), json.getString("lastname"), json.getString("email"), json.getString("role"));
			user.setLastname(json.getString("lastname"));
			user.setAgent(row.getString("_id"), toList(row.getJsonArray(AgentToken.APP_IDS)), toList(AgentToken.getScopes(row)));

			String call = event.request().method() + " " + event.request().path();
			if (scope == null) {
				forbidden(event, "insufficient_scope", "Agent tokens cannot call " + call);
				handler.handle(false);
				return;
			}
			if (!user.hasAgentScope(scope)) {
				forbidden(event, "insufficient_scope", "The token needs the scope " + scope + " for " + call + ". Log in again and approve it.");
				handler.handle(false);
				return;
			}
			String appID = event.pathParam(APP_ID);
			if (appID != null && !user.isAppAllowed(appID)) {
				forbidden(event, "insufficient_scope", "The token has no access to the app " + appID);
				handler.handle(false);
				return;
			}

			touch(row, event, now);
			event.put(AGENT_USER, user);
			handler.handle(true);
		});
	}

	private void touch(JsonObject row, RoutingContext event, long now) {
		if (now - row.getLong(AgentToken.LAST_USED_AT, 0L) < LAST_USED_RESOLUTION) {
			return;
		}
		JsonObject update = new JsonObject().put("$set", new JsonObject()
				.put(AgentToken.LAST_USED_AT, now)
				.put(AgentToken.LAST_IP, getIp(event)));
		mongo.updateCollection(token_db, User.findById(row.getString("_id")), update, res -> {
			if (res.failed()) {
				logger.error("touch() > could not update lastUsedAt", res.cause());
			}
		});
	}

	/*********************************************************************
	 *  Helper
	 *********************************************************************/

	public static String getBearer(RoutingContext event) {
		String header = event.request().getHeader("Authorization");
		if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return header.substring(7).trim();
		}
		return null;
	}

	/**
	 * 401. Without a token there is no error code (RFC 6750 3.1). Agents
	 * find the login through the MCP server, which publishes the metadata.
	 */
	public static void unauthorized(RoutingContext event, String error, String description) {
		String challenge = error != null
				? "Bearer error=\"" + error + "\", error_description=\"" + description + "\""
				: "Bearer";
		event.response()
				.putHeader("WWW-Authenticate", challenge)
				.putHeader("content-type", "application/json")
				.setStatusCode(401)
				.end(new JsonObject().put("error", error != null ? error : "unauthorized").put("error_description", description).encode());
	}

	private static void forbidden(RoutingContext event, String error, String description) {
		logger.warn("forbidden() > " + description);
		event.response()
				.putHeader("WWW-Authenticate", "Bearer error=\"" + error + "\", error_description=\"" + description + "\"")
				.putHeader("content-type", "application/json")
				.setStatusCode(403)
				.end(new JsonObject().put("error", error).put("error_description", description).encode());
	}

	private static String getIp(RoutingContext event) {
		String forwarded = event.request().getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isEmpty()) {
			return forwarded.split(",")[0].trim();
		}
		return event.request().remoteAddress() != null ? event.request().remoteAddress().host() : "";
	}

	private static List<String> toList(JsonArray array) {
		if (array == null) {
			return null;
		}
		List<String> result = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) {
			result.add(array.getString(i));
		}
		return result;
	}
}
