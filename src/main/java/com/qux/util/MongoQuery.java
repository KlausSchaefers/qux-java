package com.qux.util;

import java.util.HashMap;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class MongoQuery {
	
	private HashMap<String, JsonObject> params = new HashMap<>();
	
	private HashMap<String, String> field = new HashMap<>();
	
	private final JsonObject root;

	private MongoQuery(JsonObject root){
		this.root = root;
	};
	
	public static MongoQuery create(String query){
		
		JsonObject json = new JsonObject(query);
		
		MongoQuery result = new MongoQuery(json);

		parse(json, result);
	
		return result;

	}
	
	private static void parse(JsonObject obj, MongoQuery result){
		
		for(String field : obj.fieldNames()){
			
			Object value = obj.getValue(field);
			
			if(value instanceof String){
				
				/**
				 * check if we have somehow an param
				 */
				
				String string = obj.getString(field);
				if(string.startsWith("?")){
					String param = string.substring(1);
					result.params.put(param, obj);
					result.field.put(param, field);
				}
				
			} else if(value instanceof JsonObject){
				
				JsonObject child = obj.getJsonObject(field);
				if(child!=null){
					parse(child, result);
				}
				
			} else if(value instanceof JsonArray){
				
				JsonArray children = obj.getJsonArray(field);
				for(int i =0; i < children.size(); i++){
					JsonObject child = obj.getJsonObject(field);
					if(child!=null){
						parse(child, result);
					}
					
				}
				
			}
			
			
			
			
			
			
			
			
		}
	}
	
	public JsonObject build(){
		return root;
	}

	public MongoQuery set(String param, String value) {
		if(this.field.containsKey(param)){
			String field = this.field.get(param);
			this.params.get(param).put(field, value);
		} else {
			throw new IllegalArgumentException("Param " + param + " not in query!");
		}
	
		return this;
	}
	
	public MongoQuery set(String param, int value) {
		if(this.field.containsKey(param)){
			String field = this.field.get(param);
			this.params.get(param).put(field, value);
		} else {
			throw new IllegalArgumentException("Param " + param + " not in query!");
		}
	
		return this;
	}
	
	public MongoQuery set(String param, boolean value) {
		if(this.field.containsKey(param)){
			String field = this.field.get(param);
			this.params.get(param).put(field, value);
		} else {
			throw new IllegalArgumentException("Param " + param + " not in query!");
		}
	
		return this;
	}
	
	public MongoQuery set(String param, double value) {
		if(this.field.containsKey(param)){
			String field = this.field.get(param);
			this.params.get(param).put(field, value);
		} else {
			throw new IllegalArgumentException("Param " + param + " not in query!");
		}
	
		return this;
	}

}
