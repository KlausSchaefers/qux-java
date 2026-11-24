package com.qux.rest;

import com.qux.auth.ITokenService;

import com.qux.model.AgentClient;
import com.qux.model.AgentGrant;
import com.qux.model.AgentToken;
import com.qux.model.User;
import com.qux.services.AgentTokenService;
import com.qux.util.DB;
import com.qux.util.rest.MongoREST;
import com.qux.util.OAuthConfig;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The OAuth 2.1 authorization server for agents, so any agent can connect to
 * quant-ux by itself: the user logs in on a page of the web app and approves
 * the agent, the agent gets a token of its own (see AgentToken).
 *
 * Flows:
 *  - Authorization code + PKCE (RFC 6749, RFC 7636, OAuth 2.1), with dynamic
 *    client registration (RFC 7591) and the metadata (RFC 8414, RFC 9728).
 *    This is what MCP clients (Claude Code, Cursor, VS Code...) do on their
 *    own when a remote MCP server answers 401.
 *
 * Agents that cannot do this use a personal access token (AgentTokenREST).
 *
 * The consent page is in the Vue app (agent/authorize.html). It calls the
 * request routes with the JWT of the logged in user, an agent token is
 * never accepted there.
 */
public class OAuthREST extends MongoREST {

	public static final String GRANT_CODE = "authorization_code";

	public static final String GRANT_REFRESH = "refresh_token";

	public static final String AUTHORIZE_PAGE = "/#/agent/authorize.html?request=";

	private static final Set<String> FORBIDDEN_SCHEMES = new HashSet<>(Arrays.asList("javascript", "data", "file", "vbscript", "about", "blob"));

	private static final int MAX_REDIRECT_URIS = 10;

	private static final int MAX_APP_IDS = 1000;

	private final String grant_db, client_db, token_db;

	private final AgentTokenService tokens;

	public OAuthREST(ITokenService tokenService, MongoClient db, AgentTokenService tokens) {
		super(tokenService, db, AgentGrant.class);
		this.grant_db = DB.getTable(AgentGrant.class);
		this.client_db = DB.getTable(AgentClient.class);
		this.token_db = DB.getTable(AgentToken.class);
		this.tokens = tokens;
	}

	/********************************************************************************************
	 * Metadata
	 ********************************************************************************************/

	/**
	 * RFC 8414
	 */
	public void authorizationServerMetadata(RoutingContext event) {
		String issuer = OAuthConfig.getIssuer(event);
		JsonObject metadata = new JsonObject()
				.put("issuer", issuer)
				.put("authorization_endpoint", issuer + "/oauth/authorize")
				.put("token_endpoint", issuer + "/oauth/token")
				.put("registration_endpoint", issuer + "/oauth/register")
				.put("scopes_supported", new JsonArray(AgentToken.SCOPES_SUPPORTED))
				.put("response_types_supported", new JsonArray().add("code"))
				.put("grant_types_supported", new JsonArray().add(GRANT_CODE).add(GRANT_REFRESH))
				.put("code_challenge_methods_supported", new JsonArray().add("S256"))
				.put("token_endpoint_auth_methods_supported", new JsonArray().add("none"))
				.put("authorization_response_iss_parameter_supported", true);
		returnPublicJson(event, metadata);
	}

	/********************************************************************************************
	 * Dynamic client registration (RFC 7591)
	 ********************************************************************************************/

	public void register(RoutingContext event) {
		JsonObject req = getJson(event);
		if (req == null) {
			registrationError(event, "invalid_client_metadata", "The request must be JSON");
			return;
		}

		String authMethod = req.getString("token_endpoint_auth_method", "none");
		if (!"none".equals(authMethod)) {
			registrationError(event, "invalid_client_metadata", "Only public clients (token_endpoint_auth_method=none) are supported");
			return;
		}

		JsonArray redirectUris = req.getJsonArray("redirect_uris", new JsonArray());
		if (redirectUris.isEmpty() || redirectUris.size() > MAX_REDIRECT_URIS) {
			registrationError(event, "invalid_redirect_uri", "Between 1 and " + MAX_REDIRECT_URIS + " redirect_uris are needed");
			return;
		}
		for (Object uri : redirectUris) {
			if (!(uri instanceof String) || !isValidRedirectUri((String) uri)) {
				registrationError(event, "invalid_redirect_uri", "Not a valid redirect uri: " + uri);
				return;
			}
		}

		String name = toClientName(req.getString("client_name"));

		long now = System.currentTimeMillis();
		String clientID = "qux_client_" + AgentTokenService.randomId();
		JsonObject client = new JsonObject()
				.put(AgentClient.CLIENT_ID, clientID)
				.put(AgentClient.CLIENT_NAME, name)
				.put(AgentClient.REDIRECT_URIS, redirectUris)
				.put(AgentClient.CLIENT_URI, req.getString("client_uri"))
				.put(AgentClient.FIELD_CREATED, now);

		mongo.insert(client_db, client, res -> {
			if (res.succeeded()) {
				logger.info("register() > new client " + clientID + " > " + name);
				JsonObject result = new JsonObject()
						.put("client_id", clientID)
						.put("client_id_issued_at", now / 1000)
						.put("client_name", name)
						.put("redirect_uris", redirectUris)
						.put("grant_types", new JsonArray().add(GRANT_CODE).add(GRANT_REFRESH))
						.put("response_types", new JsonArray().add("code"))
						.put("token_endpoint_auth_method", "none");
				event.response().setStatusCode(201);
				returnNoStore(event, result);
			} else {
				logger.error("register() > could not save client", res.cause());
				event.response().setStatusCode(500).end();
			}
		});
	}

	/********************************************************************************************
	 * Authorize: validate, keep the request, send the user to the consent page
	 ********************************************************************************************/

	public void authorize(RoutingContext event) {
		String clientID = param(event, "client_id");
		String redirectUri = param(event, "redirect_uri");

		loadClient(clientID, client -> {
			/*
			 * Without a known client and redirect uri, we must not redirect
			 * anywhere (RFC 6749 4.1.2.1), so the error is shown here.
			 */
			if (client == null) {
				authorizePageError(event, "Unknown client_id. The agent has to register first.");
				return;
			}
			String redirect = matchRedirectUri(client, redirectUri);
			if (redirect == null) {
				authorizePageError(event, "The redirect_uri is not registered for this client.");
				return;
			}

			String state = param(event, "state");
			if (!"code".equals(param(event, "response_type"))) {
				redirectError(event, redirect, state, "unsupported_response_type", "Only response_type=code is supported");
				return;
			}
			String challenge = param(event, "code_challenge");
			if (challenge == null || challenge.length() < 43 || !"S256".equals(param(event, "code_challenge_method"))) {
				redirectError(event, redirect, state, "invalid_request", "PKCE with code_challenge_method=S256 is required");
				return;
			}
			String resource = param(event, "resource");
			if (resource != null && !isValidResource(resource)) {
				redirectError(event, redirect, state, "invalid_target", "The resource must be an absolute http(s) URL");
				return;
			}

			long now = System.currentTimeMillis();
			String requestID = AgentTokenService.randomId();
			JsonObject grant = new JsonObject()
					.put(AgentGrant.REQUEST_ID, requestID)
					.put(AgentGrant.CLIENT_ID, client.getString(AgentClient.CLIENT_ID))
					.put(AgentGrant.REDIRECT_URI, redirect)
					.put(AgentGrant.CODE_CHALLENGE, challenge)
					.put(AgentGrant.STATE, state)
					.put(AgentGrant.RESOURCE, resource)
					.put(AgentGrant.SCOPES, AgentToken.toScopes(param(event, "scope")))
					.put(AgentGrant.STATUS, AgentGrant.STATUS_PENDING)
					.put(AgentGrant.FIELD_CREATED, now);
			setExpires(grant, now + AgentGrant.REQUEST_TTL);

			mongo.insert(grant_db, grant, res -> {
				if (res.succeeded()) {
					event.response()
							.putHeader("Location", OAuthConfig.getIssuer(event) + AUTHORIZE_PAGE + requestID)
							.putHeader("Cache-Control", "no-store")
							.setStatusCode(302)
							.end();
				} else {
					logger.error("authorize() > could not save grant", res.cause());
					redirectError(event, redirect, state, "server_error", "Could not save the request");
				}
			});
		});
	}

	/**
	 * The request as the consent page shows it.
	 */
	public void getRequest(RoutingContext event) {
		User user = getWebUser(event);
		if (user == null) {
			return;
		}
		mongo.findOne(grant_db, AgentGrant.findByRequestID(getId(event, "requestID")), null, res -> {
			JsonObject grant = res.succeeded() ? res.result() : null;
			if (grant == null || AgentGrant.isExpired(grant, System.currentTimeMillis())) {
				returnJsonError(event, 404, "request.not.found");
				return;
			}
			loadClient(grant.getString(AgentGrant.CLIENT_ID), client -> returnJson(event, toConsent(grant, client)));
		});
	}

	public void approveRequest(RoutingContext event) {
		decideRequest(event, true);
	}

	public void denyRequest(RoutingContext event) {
		decideRequest(event, false);
	}

	private void decideRequest(RoutingContext event, boolean approve) {
		User user = getWebUser(event);
		if (user == null) {
			return;
		}
		JsonObject body = getJson(event);
		mongo.findOne(grant_db, AgentGrant.findByRequestID(getId(event, "requestID")), null, res -> {
			JsonObject grant = res.succeeded() ? res.result() : null;
			long now = System.currentTimeMillis();
			if (grant == null || AgentGrant.isExpired(grant, now) || !AgentGrant.STATUS_PENDING.equals(grant.getString(AgentGrant.STATUS))) {
				returnJsonError(event, 404, "request.not.found");
				return;
			}

			String redirect = grant.getString(AgentGrant.REDIRECT_URI);
			String state = grant.getString(AgentGrant.STATE);
			String issuer = OAuthConfig.getIssuer(event);

			if (!approve) {
				JsonObject update = new JsonObject().put("$set", new JsonObject().put(AgentGrant.STATUS, AgentGrant.STATUS_DENIED).put(AgentGrant.USER_ID, user.getId()));
				mongo.findOneAndUpdate(grant_db, AgentGrant.findByIdAndStatus(grant.getString("_id"), AgentGrant.STATUS_PENDING), update, denied -> {
					String url = addParams(redirect, "error", "access_denied", "error_description", "The user denied the request", "state", state, "iss", issuer);
					returnJson(event, new JsonObject().put("redirect", url));
				});
				return;
			}

			JsonArray appIDs = getAppIDs(body);
			tokens.hasAccessToApps(user.getId(), appIDs, allowed -> {
				if (!allowed) {
					logger.error("decideRequest() > " + user.getEmail() + " approved apps without access: " + appIDs);
					returnJsonError(event, 403, "apps.forbidden");
					return;
				}
				String code = AgentTokenService.randomToken("qux_code_");
				JsonObject set = new JsonObject()
						.put(AgentGrant.STATUS, AgentGrant.STATUS_APPROVED)
						.put(AgentGrant.USER_ID, user.getId())
						.put(AgentGrant.APP_IDS, appIDs)
						.put(AgentGrant.SCOPES, AgentToken.narrowScopes(AgentToken.getScopes(grant), getScopes(body)))
						.put(AgentGrant.CODE_HASH, AgentTokenService.hash(code));
				setExpires(set, now + AgentGrant.CODE_TTL);

				mongo.findOneAndUpdate(grant_db, AgentGrant.findByIdAndStatus(grant.getString("_id"), AgentGrant.STATUS_PENDING), new JsonObject().put("$set", set), approved -> {
					if (approved.succeeded() && approved.result() != null) {
						logger.info("approveRequest() > " + user.getEmail() + " approved " + grant.getString(AgentGrant.CLIENT_ID));
						String url = addParams(redirect, "code", code, "state", state, "iss", issuer);
						returnJson(event, new JsonObject().put("redirect", url));
					} else {
						returnJsonError(event, 409, "request.already.decided");
					}
				});
			});
		});
	}

	/********************************************************************************************
	 * Token
	 ********************************************************************************************/

	public void token(RoutingContext event) {
		String grantType = param(event, "grant_type");
		if (GRANT_CODE.equals(grantType)) {
			tokenByCode(event);
		} else if (GRANT_REFRESH.equals(grantType)) {
			tokenByRefresh(event);
		} else {
			tokenError(event, 400, "unsupported_grant_type", "Supported: " + GRANT_CODE + ", " + GRANT_REFRESH);
		}
	}

	private void tokenByCode(RoutingContext event) {
		String code = param(event, "code");
		String clientID = param(event, "client_id");
		if (code == null || clientID == null) {
			tokenError(event, 400, "invalid_request", "code and client_id are required");
			return;
		}
		mongo.findOne(grant_db, AgentGrant.findByCodeHash(AgentTokenService.hash(code)), null, res -> {
			JsonObject grant = res.succeeded() ? res.result() : null;
			if (grant == null) {
				tokenError(event, 400, "invalid_grant", "Unknown code");
				return;
			}
			if (AgentGrant.isExpired(grant, System.currentTimeMillis()) || !AgentGrant.STATUS_APPROVED.equals(grant.getString(AgentGrant.STATUS))) {
				tokenError(event, 400, "invalid_grant", "The code expired or was already used");
				return;
			}
			if (!clientID.equals(grant.getString(AgentGrant.CLIENT_ID))) {
				tokenError(event, 400, "invalid_grant", "The code was issued to another client");
				return;
			}
			if (!grant.getString(AgentGrant.REDIRECT_URI).equals(param(event, "redirect_uri"))) {
				tokenError(event, 400, "invalid_grant", "The redirect_uri does not match the one of the authorization request");
				return;
			}
			if (!AgentTokenService.verifyPKCE(param(event, "code_verifier"), grant.getString(AgentGrant.CODE_CHALLENGE))) {
				tokenError(event, 400, "invalid_grant", "PKCE verification failed");
				return;
			}
			String resource = param(event, "resource");
			if (resource != null && grant.getString(AgentGrant.RESOURCE) != null && !resource.equals(grant.getString(AgentGrant.RESOURCE))) {
				tokenError(event, 400, "invalid_target", "The resource does not match the one of the authorization request");
				return;
			}
			useGrant(event, grant, AgentGrant.STATUS_APPROVED);
		});
	}

	/**
	 * Marks the grant as used, if no other request did, and issues the tokens.
	 */
	private void useGrant(RoutingContext event, JsonObject grant, String fromStatus) {
		JsonObject update = new JsonObject().put("$set", new JsonObject().put(AgentGrant.STATUS, AgentGrant.STATUS_USED));
		mongo.findOneAndUpdate(grant_db, AgentGrant.findByIdAndStatus(grant.getString("_id"), fromStatus), update, res -> {
			if (res.failed() || res.result() == null) {
				tokenError(event, 400, "invalid_grant", "The grant was already used");
				return;
			}
			loadClient(grant.getString(AgentGrant.CLIENT_ID), client -> {
				String name = client != null ? toClientName(client.getString(AgentClient.CLIENT_NAME)) : "Agent";
				tokens.issueOAuth(
						grant.getString(AgentGrant.USER_ID),
						grant.getString(AgentGrant.CLIENT_ID),
						name,
						grant.getJsonArray(AgentGrant.APP_IDS),
						AgentToken.getScopes(grant),
						grant.getString(AgentGrant.RESOURCE),
						issued -> {
							if (issued.succeeded()) {
								returnNoStore(event, issued.result());
							} else {
								tokenError(event, 500, "server_error", "Could not issue the token");
							}
						});
			});
		});
	}

	/**
	 * A new access and refresh token for the same agent, see
	 * AgentTokenService.rotate(). The scope stays the one the user granted.
	 */
	private void tokenByRefresh(RoutingContext event) {
		String refresh = param(event, "refresh_token");
		String clientID = param(event, "client_id");
		if (refresh == null || clientID == null) {
			tokenError(event, 400, "invalid_request", "refresh_token and client_id are required");
			return;
		}
		String hash = AgentTokenService.hash(refresh);
		mongo.findOne(token_db, AgentToken.findByRefreshHash(hash), null, res -> {
			JsonObject row = res.succeeded() ? res.result() : null;
			if (row == null) {
				checkReplay(event, hash, clientID);
				return;
			}
			if (!clientID.equals(row.getString(AgentToken.CLIENT_ID)) || !AgentToken.canRefresh(row, System.currentTimeMillis())) {
				tokenError(event, 400, "invalid_grant", "The refresh_token is unknown, expired or revoked");
				return;
			}
			tokens.rotate(row, hash, rotated -> {
				if (rotated.failed()) {
					tokenError(event, 500, "server_error", "Could not issue the token");
				} else if (rotated.result() == null) {
					tokenError(event, 400, "invalid_grant", "The refresh_token was already used");
				} else {
					returnNoStore(event, rotated.result());
				}
			});
		});
	}

	/**
	 * A refresh token that was already replaced was stolen, or the client
	 * lost a response: the agent is disconnected and the user has to approve
	 * again (OAuth 2.1, refresh token rotation).
	 */
	private void checkReplay(RoutingContext event, String hash, String clientID) {
		mongo.findOne(token_db, AgentToken.findByPreviousRefreshHash(hash), null, res -> {
			JsonObject row = res.succeeded() ? res.result() : null;
			if (row != null && clientID.equals(row.getString(AgentToken.CLIENT_ID))) {
				logger.error("checkReplay() > *ATTENTION* refresh token replayed for client " + clientID);
				tokens.revokeById(row.getString("_id"), "refresh_replay");
			}
			tokenError(event, 400, "invalid_grant", "The refresh_token is unknown, expired or revoked");
		});
	}

	/********************************************************************************************
	 * Helper
	 ********************************************************************************************/

	/**
	 * The consent pages may only be used by a logged in user of the web app.
	 * Agent tokens never get here (AgentACL), the old app tokens
	 * (User.TOKEN) are not a user either.
	 */
	private User getWebUser(RoutingContext event) {
		User user = getUser(event);
		if (user == null || user.isGuest() || user.isAgent() || user.hasClaim() || !user.hasRole(User.USER)) {
			returnJsonError(event, 401, "user.not.logged.in");
			return null;
		}
		return user;
	}

	/**
	 * The name of the agent as the user knows it: some clients add the name
	 * the user gave this server, e.g. "Claude Code (quant-ux)".
	 */
	static String toClientName(String name) {
		name = name != null ? name.trim() : "";
		String withoutSuffix = name.replaceAll("\\s*\\([^()]*\\)$", "").trim();
		if (!withoutSuffix.isEmpty()) {
			name = withoutSuffix;
		}
		if (name.isEmpty()) {
			return "Unknown agent";
		}
		return name.length() > 100 ? name.substring(0, 100) : name;
	}

	private JsonObject toConsent(JsonObject grant, JsonObject client) {
		String redirect = grant.getString(AgentGrant.REDIRECT_URI);
		String redirectHost = null;
		if (redirect != null) {
			try {
				URI uri = new URI(redirect);
				redirectHost = uri.getHost() != null ? uri.getHost() : uri.getScheme();
			} catch (Exception e) {
				redirectHost = redirect;
			}
		}
		return new JsonObject()
				.put("client", new JsonObject()
						.put("id", grant.getString(AgentGrant.CLIENT_ID))
						.put("name", client != null ? toClientName(client.getString(AgentClient.CLIENT_NAME)) : "Unknown agent")
						.put("uri", client != null ? client.getString(AgentClient.CLIENT_URI) : null))
				.put("redirectHost", redirectHost)
				.put("resource", grant.getString(AgentGrant.RESOURCE))
				.put("scopes", AgentToken.getScopes(grant))
				.put("expiresAt", grant.getLong(AgentGrant.EXPIRES_AT));
	}

	private void loadClient(String clientID, io.vertx.core.Handler<JsonObject> handler) {
		if (clientID == null || clientID.isEmpty()) {
			handler.handle(null);
			return;
		}
		mongo.findOne(client_db, AgentClient.findByClientID(clientID), null, res -> {
			handler.handle(res.succeeded() ? res.result() : null);
		});
	}

	/**
	 * The redirect uri of the request, if it is one the client registered.
	 */
	static String matchRedirectUri(JsonObject client, String requested) {
		if (requested == null) {
			return null;
		}
		JsonArray registered = client.getJsonArray(AgentClient.REDIRECT_URIS, new JsonArray());
		for (int i = 0; i < registered.size(); i++) {
			if (withoutLoopbackPort(registered.getString(i)).equals(withoutLoopbackPort(requested))) {
				return requested;
			}
		}
		return null;
	}

	/**
	 * A loopback uri without its port: a native agent listens on a free
	 * port, a new one each time (RFC 8252 7.3).
	 */
	private static String withoutLoopbackPort(String uri) {
		try {
			URI u = new URI(uri);
			if ("http".equals(u.getScheme()) && isLoopback(u.getHost())) {
				return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), -1, u.getPath(), u.getQuery(), u.getFragment()).toString();
			}
		} catch (Exception e) {
			// not a valid uri, compared as it is
		}
		return uri;
	}

	private static boolean isLoopback(String host) {
		return "127.0.0.1".equals(host) || "localhost".equals(host) || "[::1]".equals(host) || "::1".equals(host);
	}

	/**
	 * https anywhere, http only on loopback, and the private schemes of
	 * native apps (cursor://, vscode://), but never javascript: and friends.
	 */
	static boolean isValidRedirectUri(String uri) {
		try {
			URI u = new URI(uri);
			String scheme = u.getScheme();
			if (scheme == null || u.getRawFragment() != null) {
				return false;
			}
			scheme = scheme.toLowerCase();
			if ("https".equals(scheme)) {
				return u.getHost() != null && !u.getHost().isEmpty();
			}
			if ("http".equals(scheme)) {
				return isLoopback(u.getHost());
			}
			return scheme.matches("^[a-z][a-z0-9+.-]*$") && !FORBIDDEN_SCHEMES.contains(scheme);
		} catch (Exception e) {
			return false;
		}
	}

	private static boolean isValidResource(String resource) {
		try {
			URI u = new URI(resource);
			return u.isAbsolute() && u.getRawFragment() == null && ("https".equals(u.getScheme()) || "http".equals(u.getScheme()));
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * null means all apps of the user (the default), else the selected ones.
	 */
	private static JsonArray getAppIDs(JsonObject body) {
		if (body == null || !(body.getValue(AgentGrant.APP_IDS) instanceof JsonArray)) {
			return null;
		}
		JsonArray result = new JsonArray();
		for (Object id : body.getJsonArray(AgentGrant.APP_IDS)) {
			if (id instanceof String && !result.contains(id) && result.size() < MAX_APP_IDS) {
				result.add(id);
			}
		}
		return result;
	}

	/**
	 * The scopes the user left checked on the consent page, null when the
	 * page does not say (all requested ones).
	 */
	private static JsonArray getScopes(JsonObject body) {
		if (body == null || !(body.getValue(AgentGrant.SCOPES) instanceof JsonArray)) {
			return null;
		}
		return body.getJsonArray(AgentGrant.SCOPES);
	}

	private static void setExpires(JsonObject json, long expiresAt) {
		json.put(AgentGrant.EXPIRES_AT, expiresAt);
		json.put(AgentGrant.EXPIRE_AT_DATE, new JsonObject().put("$date", Instant.ofEpochMilli(expiresAt).toString()));
	}

	/**
	 * A form parameter (the token and revoke endpoints take
	 * application/x-www-form-urlencoded) or a query parameter (authorize).
	 */
	private String param(RoutingContext event, String name) {
		String value = event.request().getFormAttribute(name);
		if (value == null) {
			value = event.request().getParam(name);
		}
		return value == null || value.isEmpty() ? null : value;
	}

	static String addParams(String url, String... params) {
		StringBuilder sb = new StringBuilder(url);
		boolean hasQuery = url.contains("?");
		for (int i = 0; i + 1 < params.length; i += 2) {
			if (params[i + 1] == null) {
				continue;
			}
			sb.append(hasQuery ? '&' : '?');
			hasQuery = true;
			sb.append(params[i]).append('=').append(encode(params[i + 1]));
		}
		return sb.toString();
	}

	private static String encode(String s) {
		try {
			return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
		} catch (Exception e) {
			return s;
		}
	}

	private void redirectError(RoutingContext event, String redirect, String state, String error, String description) {
		event.response()
				.putHeader("Location", addParams(redirect, "error", error, "error_description", description, "state", state, "iss", OAuthConfig.getIssuer(event)))
				.setStatusCode(302)
				.end();
	}

	private void authorizePageError(RoutingContext event, String message) {
		logger.warn("authorizePageError() > " + message);
		event.response()
				.putHeader("content-type", "text/html; charset=utf-8")
				.setStatusCode(400)
				.end("<!doctype html><meta charset=\"utf-8\"><title>Quant-UX</title>"
						+ "<body style=\"font-family:sans-serif;max-width:480px;margin:80px auto;padding:0 16px\">"
						+ "<h1>Could not connect the agent</h1><p>" + message.replace("<", "&lt;") + "</p></body>");
	}

	private void tokenError(RoutingContext event, int status, String error, String description) {
		event.response().setStatusCode(status);
		returnNoStore(event, new JsonObject().put("error", error).put("error_description", description));
	}

	private void registrationError(RoutingContext event, String error, String description) {
		tokenError(event, 400, error, description);
	}

	private void returnJsonError(RoutingContext event, int status, String error) {
		event.response()
				.putHeader("content-type", "application/json")
				.setStatusCode(status)
				.end(new JsonObject().put("error", error).encode());
	}

	private void returnNoStore(RoutingContext event, JsonObject json) {
		event.response()
				.putHeader("content-type", "application/json")
				.putHeader("Cache-Control", "no-store")
				.putHeader("Pragma", "no-cache")
				.end(json.encode());
	}

	private void returnPublicJson(RoutingContext event, JsonObject json) {
		event.response()
				.putHeader("content-type", "application/json")
				.putHeader("Cache-Control", "public, max-age=3600")
				.end(json.encodePrettily());
	}
}
