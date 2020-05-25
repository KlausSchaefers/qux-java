package com.qux.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class TestSetting extends AppPart {
	
	public static JsonObject createEmpty (User u, String appID) {
		return new JsonObject()
				.put("userID", u.getId())
				.put("appID", appID)
				.put("created", System.currentTimeMillis())
				.put("tasks", new JsonArray());
		
	}

}
