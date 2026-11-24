package com.qux.model;

import io.vertx.core.json.JsonObject;

/**
 * A pending or finished authorization of an agent: the request the user sees
 * on the consent page, and the code the agent exchanges for a token.
 *
 * Created by /oauth/authorize. The user approves it on the consent page,
 * which creates the code (single use, 60 seconds).
 *
 * Codes are stored as SHA-256 only. Mongo removes the rows after expiresAt
 * (TTL index on expireAtDate).
 */
public class AgentGrant extends Model {

	public static final String STATUS_PENDING = "pending";

	public static final String STATUS_APPROVED = "approved";

	public static final String STATUS_DENIED = "denied";

	public static final String STATUS_USED = "used";

	public static final long REQUEST_TTL = 10L * 60 * 1000;

	public static final long CODE_TTL = 60L * 1000;

	public static final String REQUEST_ID = "requestID";

	public static final String CLIENT_ID = "clientID";

	public static final String REDIRECT_URI = "redirectUri";

	public static final String CODE_CHALLENGE = "codeChallenge";

	public static final String STATE = "state";

	public static final String RESOURCE = "resource";

	public static final String STATUS = "status";

	public static final String USER_ID = "userID";

	public static final String APP_IDS = "appIDs";

	/**
	 * The scopes the agent asked for, and after the approval the ones the
	 * user granted (see AgentToken.SCOPES).
	 */
	public static final String SCOPES = "scopes";

	public static final String CODE_HASH = "codeHash";

	public static final String EXPIRES_AT = "expiresAt";

	/**
	 * A Mongo date, only for the TTL index, which needs a BSON date.
	 */
	public static final String EXPIRE_AT_DATE = "expireAtDate";

	public static boolean isExpired(JsonObject grant, long now) {
		return grant == null || grant.getLong(EXPIRES_AT, 0L) <= now;
	}

	public static JsonObject findByRequestID(String requestID) {
		return new JsonObject().put(REQUEST_ID, requestID);
	}

	public static JsonObject findByCodeHash(String hash) {
		return new JsonObject().put(CODE_HASH, hash);
	}

	/**
	 * Only a grant in the given status can move on, so two concurrent
	 * requests (a double click, a replayed code) cannot both win.
	 */
	public static JsonObject findByIdAndStatus(String id, String status) {
		return new JsonObject()
				.put("_id", id)
				.put(STATUS, status);
	}
}
