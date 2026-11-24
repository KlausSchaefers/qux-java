package com.qux.rest;

import com.qux.auth.ITokenService;
import com.qux.model.AgentToken;
import com.qux.model.User;
import com.qux.services.AgentTokenService;
import com.qux.util.rest.MongoREST;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

/**
 * The agents a user connected, for "My account": list them, revoke one or
 * all, and create a personal access token for agents that cannot do OAuth.
 *
 * Only the logged in user of the web app may call these, never an agent
 * (AgentACL is not used by /rest/agent-tokens, a token is no JWT).
 */
public class AgentTokenREST extends MongoREST {

	private final AgentTokenService tokens;

	public AgentTokenREST(ITokenService tokenService, MongoClient db, AgentTokenService tokens) {
		super(tokenService, db, AgentToken.class);
		this.tokens = tokens;
	}

	public void list(RoutingContext event) {
		User user = getWebUser(event);
		if (user == null) {
			return;
		}
		FindOptions options = new FindOptions().setSort(new JsonObject().put(AgentToken.FIELD_CREATED, -1));
		mongo.findWithOptions(table, AgentToken.findActiveByUser(user.getId(), System.currentTimeMillis()), options, res -> {
			if (res.succeeded()) {
				returnJson(event, toPublic(res.result()));
			} else {
				logger.error("list() > could not load tokens", res.cause());
				returnError(event, 500);
			}
		});
	}

	/**
	 * Body: { name, appIDs?: [..], scopes?: ["apps:read", "apps:write"], days?: 90 }
	 * Returns the token once, in the field "token". appIDs have to be apps the
	 * user can read, else 403.
	 */
	public void create(RoutingContext event) {
		User user = getWebUser(event);
		if (user == null) {
			return;
		}
		JsonObject body = getJson(event);
		if (body == null) {
			body = new JsonObject();
		}
		String name = body.getString("name", "").trim();
		if (name.isEmpty()) {
			returnError(event, "agent.token.name.missing");
			return;
		}
		if (name.length() > 100) {
			name = name.substring(0, 100);
		}

		int days = body.getInteger("days", AgentToken.PAT_DEFAULT_DAYS);
		if (days < 1 || days > AgentToken.PAT_MAX_DAYS) {
			returnError(event, "agent.token.days.invalid");
			return;
		}

		JsonArray appIDs = body.getValue("appIDs") instanceof JsonArray ? body.getJsonArray("appIDs") : null;

		// read only, unless the user asks for more
		JsonArray scopes = AgentToken.toScopes(body.getValue("scopes") instanceof JsonArray ? body.getJsonArray("scopes") : null);

		String tokenName = name;
		tokens.hasAccessToApps(user.getId(), appIDs, allowed -> {
			if (!allowed) {
				logger.error("create() > " + user.getEmail() + " asked for a token for apps without access: " + appIDs);
				returnError(event, 403);
				return;
			}
			tokens.issuePAT(user.getId(), tokenName, appIDs, scopes, days, res -> {
				if (res.succeeded()) {
					logger.info("create() > " + user.getEmail() + " created token " + res.result().getString(AgentToken.PREFIX));
					returnJson(event, res.result());
				} else {
					returnError(event, 500);
				}
			});
		});
	}

	public void revoke(RoutingContext event) {
		User user = getWebUser(event);
		if (user == null) {
			return;
		}
		JsonObject query = AgentToken.findActiveByIdAndUser(getId(event, "tokenID"), user.getId());
		tokens.revokeWhere(query, "revoked_by_user", count -> {
			if (count > 0) {
				returnOk(event, "agent.token.revoked");
			} else if (count == 0) {
				returnError(event, 404);
			} else {
				returnError(event, 500);
			}
		});
	}

	public void revokeAll(RoutingContext event) {
		User user = getWebUser(event);
		if (user == null) {
			return;
		}
		tokens.revokeWhere(AgentToken.findActiveByUser(user.getId()), "revoked_by_user", count -> {
			if (count >= 0) {
				returnOk(event, "agent.token.revoked");
			} else {
				returnError(event, 500);
			}
		});
	}

	private User getWebUser(RoutingContext event) {
		User user = getUser(event);
		if (user == null || user.isGuest() || user.isAgent() || user.hasClaim()) {
			returnError(event, 401);
			return null;
		}
		return user;
	}

	private static JsonArray toPublic(List<JsonObject> rows) {
		JsonArray result = new JsonArray();
		for (JsonObject row : rows) {
			result.add(AgentToken.toPublic(row));
		}
		return result;
	}
}
