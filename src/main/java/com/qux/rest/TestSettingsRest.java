package com.qux.rest;

import com.qux.model.TestSetting;
import com.qux.model.User;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class TestSettingsRest extends AppPartREST<TestSetting>{

	public TestSettingsRest(MongoClient db, Class<TestSetting> cls, String idParameter) {
		super(db, cls, idParameter);
	}
	
	protected JsonObject createInstance(RoutingContext event, String appID){
		User u = getUser(event);
		return TestSetting.createEmpty(u, getId(event, "appID"));
//		JsonObject  json = new JsonObject()
//			.put("userID", u.getId())
//			.put("appID", )
//			.put("created", System.currentTimeMillis())
//			.put("tasks", new JsonArray());		
//		return json;
	}

}
