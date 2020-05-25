package com.qux.model;

import io.vertx.core.json.JsonObject;

public class Library extends Model{

	public static JsonObject findPublicByID(String appID){
 		return new JsonObject()
 			.put("isPublic", true)
 			.put("_id", appID);
 		
	}
 	
 	public static JsonObject findPublic(){
 		return new JsonObject()
 			.put("isPublic", true);
	}
 	
}
