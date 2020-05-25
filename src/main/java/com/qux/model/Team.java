package com.qux.model;

import com.qux.acl.Acl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Team extends AppPart {
	
	public static final String PERMISSION = "permission";
	
	public static JsonObject create(String userID, String appID, int permission){
		return new JsonObject()
			.put(USER_ID, userID)
			.put(APP_ID, appID)
			.put(PERMISSION, permission);
	}
	
	public static JsonObject create(User user, String appID, int permission){
		return create(user.getId(), appID, permission);
	}

	public static JsonObject canRead(User user, String appID) {
		 return new JsonObject()
		 	.put(Team.APP_ID, appID)
	    	.put(Team.USER_ID , user.getId())
	    	.put(PERMISSION, new JsonObject().put("$gte", Acl.READ));
    			
	}

	public static JsonObject canWrite(User user, String appID) {
		 return new JsonObject()
		 	.put(Team.APP_ID, appID)
	    	.put(Team.USER_ID , user.getId())
	    	.put(PERMISSION, new JsonObject().put("$gte", Acl.WRITE));
   			
	}
	
	public static JsonObject isOwner(User user, String appID) {
		 return new JsonObject()
		 	.put(Team.APP_ID, appID)
	    	.put(Team.USER_ID , user.getId())
	    	.put(PERMISSION, new JsonObject().put("$gte", Acl.OWNER));
   			
	}
	
 	public static JsonObject findByUser(User user){
		return new JsonObject()
			.put(USER_ID,  user.getId())
			.put(PERMISSION, new JsonObject().put("$gte", Acl.READ));
		
	}
 	
	public static JsonObject findByOwnedUser(User user){
		return new JsonObject()
			.put(USER_ID,  user.getId())
			.put(PERMISSION, new JsonObject().put("$gte", Acl.OWNER));
	}

 	public static JsonObject findByUserAndApp(String user, String appID){
		return new JsonObject()
			.put(USER_ID,  user)
			.put(APP_ID,  appID);		
	}
 	
 	public static JsonObject findByUserAndApp(User user, String appID){
		return new JsonObject()
			.put(USER_ID,  user.getId())
			.put(APP_ID,  appID);		
	}
 	
 	
 	public static JsonObject findByAppIds(JsonArray ids){
		 return new JsonObject()
		 	.put("appID", new JsonObject().put("$in", ids));
	}
}
