package com.qux.model;

import java.util.List;

import io.vertx.core.json.JsonObject;

public class Invitation extends AppPart {
	
	public static final int TEST = 1;

	public static final int READ = 2;
	
	public static final int WRITE = 3;

	
	public static final String HASH = "hash";
	
	public static final String PERMISSION = "permission";

	
	public static JsonObject create(String invID, String appID, int right){
		return new JsonObject()
			.put(HASH, invID)
			.put(APP_ID, appID)
			.put(PERMISSION, right);
	}
	

	public static String getHash(JsonObject obj, int permission){
		
		for(String name : obj.fieldNames()){
			int p = obj.getInteger(name);
			if(p== permission){
				return name;
			}
		}
		
		return null;
	}
	
	/**
	 * We convert the list to a json object to stick with the legacy API
	 */
	public static JsonObject getInvitationFromList(List<JsonObject> list){
		JsonObject inv = new JsonObject();
		for(JsonObject obj : list){
			inv.put(obj.getString(HASH), obj.getInteger(PERMISSION));
		}
		return inv;
	}
	
	public static JsonObject findTestHash(String hash){
		 return new JsonObject()
	    	.put(HASH, hash)
	    	.put(PERMISSION, new JsonObject().put("$gte", Invitation.TEST));
	}
	
	
	
	
	public static JsonObject canTest(String appID, String hash){
		 return new JsonObject()
		 	.put(APP_ID, appID)
	    	.put(HASH, hash)
	    	.put(PERMISSION, new JsonObject().put("$gte", Invitation.TEST));
	}
	
	public static JsonObject canRead(String appID, String hash){
		 return new JsonObject()
		 	.put(APP_ID, appID)
	    	.put(HASH, hash)
	    	.put(PERMISSION, new JsonObject().put("$gte", Invitation.READ));
	}
	
	
	public static JsonObject canWrite(String appID, String hash){
		 return new JsonObject()
		 	.put(APP_ID, appID)
	    	.put(HASH, hash)
	    	.put(PERMISSION, new JsonObject().put("$gte", Invitation.WRITE));
	}
		
	
	public static JsonObject findByHash(String hash){
		 return new JsonObject()
	    	.put(HASH, hash);
	}
}



