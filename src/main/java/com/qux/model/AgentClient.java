package com.qux.model;

import io.vertx.core.json.JsonObject;

/**
 * An OAuth client: an agent that registered itself (RFC 7591, dynamic client
 * registration, which MCP clients do on their own).
 *
 * All clients are public clients (they run on the user's machine and cannot
 * keep a secret), so they authenticate with PKCE only.
 */
public class AgentClient extends Model {

	public static final String CLIENT_ID = "clientID";

	public static final String CLIENT_NAME = "clientName";

	public static final String REDIRECT_URIS = "redirectUris";

	public static final String CLIENT_URI = "clientUri";

	public static JsonObject findByClientID(String clientID) {
		return new JsonObject().put(CLIENT_ID, clientID);
	}
}
