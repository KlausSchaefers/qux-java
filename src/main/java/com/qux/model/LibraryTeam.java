package com.qux.model;

import com.qux.acl.Acl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LibraryTeam extends Model{
	
	public static final String PERMISSION = "permission";

	public static final String LIB_ID = "libID";
	
	public static final String USER_ID = "userID";
	
	public static JsonObject create(String userID, String appID, int permission){
		return new JsonObject()
			.put(USER_ID, userID)
			.put(LIB_ID, appID)
			.put(PERMISSION, permission);
	}
	
	public static JsonObject create(User user, String appID, int permission){
		return create(user.getId(), appID, permission);
	}

	public static JsonObject canRead(User user, String libID) {
		 return new JsonObject()
		 	.put(LIB_ID, libID)
	    	.put(USER_ID , user.getId())
	    	.put(PERMISSION, new JsonObject().put("$gte", Acl.READ));
    			
	}

	public static JsonObject canWrite(User user, String libID) {
		 return new JsonObject()
		 	.put(LIB_ID, libID)
	    	.put(USER_ID , user.getId())
	    	.put(PERMISSION, new JsonObject().put("$gte", Acl.WRITE));
   			
	}
	
	public static JsonObject isOwner(User user, String libID) {
		 return new JsonObject()
		 	.put(LIB_ID, libID)
	    	.put(USER_ID , user.getId())
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
	
	public static JsonObject findByLib(String libID){
		 return new JsonObject()
		 	.put(LIB_ID,libID);
	}

	public static JsonObject findByUserAndLib(String userID, String libID) {
		return new JsonObject()
			.put(USER_ID,  userID)
			.put(LIB_ID,  libID);
	}
	
	public static JsonObject findByLibIds(JsonArray ids){
		 return new JsonObject()
		 	.put(LIB_ID, new JsonObject().put("$in", ids));
	}

}
