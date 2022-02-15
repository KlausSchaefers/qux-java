package com.qux.util.rest;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoUtil {
	
	private Logger logger = LoggerFactory.getLogger(MongoUtil.class);
	
	public static final String JS_CHANGE_ADD ="add";
	
	public static final String JS_CHANGE_UPDATE ="update";
	
	public static final String JS_CHANGE_DELETE ="delete";
	
	public JsonObject changeToMongo(JsonArray changes){
		JsonObject set = new JsonObject();
		JsonObject unset = new JsonObject();
		
		/**
		 * loop over all changes
		 */
		int size = changes.size();
		for(int i=0; i < size; i++){
			JsonObject change = changes.getJsonObject(i);
			createChange(set, unset, change);
		}
		
		/**
		 * compile final result!
		 */
		JsonObject result = new JsonObject();
		if(!set.isEmpty()){
			result.put("$set", set);
		}
		
		if(!unset.isEmpty()){
			result.put("$unset", unset);
		}
		return result;
		
	}



	private void createChange(JsonObject set, JsonObject unset, JsonObject change) {
		this.logger.debug("changeToMongo() > "); 
		
		if(change.containsKey("type") && change.containsKey("name") ){
			
			String type = change.getString("type");
			String name = change.getString("name");
			Object object = getObject(change);
			String parent = getParent(change);

			switch (type){
				
				case JS_CHANGE_ADD : 
					if(parent!= null){
						set.put(parent +"." + name,object);
					} else {
						set.put(name,object);
					}
					break;
					
				case JS_CHANGE_UPDATE : 
					if(parent!= null){
						set.put(parent +"." + name,object);
					} else {
						set.put(name,object);
					}
					break;
					
					
				case JS_CHANGE_DELETE : 
					if(parent!= null){
						unset.put(parent +"." + name,"");
					} else {
						unset.put(name,"");
					}
					break;
					
					
				default:
					logger.error("changeToMongo() > Not supported change type " + type);
					break;
					
			}
			
		} else {
			this.logger.error("createChange", "Wrong JSON " + change.encodePrettily());
		}
	}
	
	
	private Object getObject(JsonObject change){
		if(change.containsKey("object")){
			return change.getValue("object");
		}
		return null;
	}
	
	private String getParent(JsonObject change){
		if(change.containsKey("parent")){
			return change.getString("parent");
		}
		return null;
	}

}
