package com.qux.model;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class AppPart extends Model {
	
	public static final String APP_ID = "appID";
	
	public static final String USER_ID = "userID";

	public static final String BUS_APPPART_UPDATE = "matc.apppart.update";

	private String appID;
	
	private String userID;

	public String getAppID() {
		return appID;
	}

	public void setAppID(String appID) {
		this.appID = appID;
	}

	public String getUserID() {
		return userID;
	}

	public void setUserID(String userID) {
		this.userID = userID;
	}
	
	
	public static JsonObject isAuthor(String userID, String id){
		 return new JsonObject()
		 	.put("_id",id)
	    	.put("userID", userID);
	}
	
	public static JsonObject findByApp(String appID){
		 return new JsonObject()
		 	.put("appID",appID);
	}
	
	public static void onUpdate(RoutingContext event, String appID) {
		
		JsonObject request = new JsonObject()
				.put(AppPart.APP_ID,  appID);
		
		event.vertx().eventBus().send(AppPart.BUS_APPPART_UPDATE, request);
		
	}
	
	
}
