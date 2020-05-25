package com.qux.util;

import io.vertx.core.json.JsonObject;

public class JsonPath {
	
	private final JsonObject obj;
	
	public JsonPath(JsonObject o){
		obj = o;
	}
	
	public Object getValue(String path){
		String[] exp = path.split("\\.");
		return getValue(exp);
	}
	
	public Object getValue(String...path){
		
		JsonObject node = obj;
		
		for(int i=0; i < path.length-1 && node!= null; i++){
			node = node.getJsonObject(path[i]);
		}
		
		String  part = path[path.length -1];
		if(node != null && node.containsKey(part))
			return node.getValue(part);
		else {
			return null;
		}
	}
	
	
	public int getInteger(String path){
		String[] exp = path.split("\\.");
		return getInteger(0, exp);
	}
	
	public int getInteger(String path, int defaultValue){
		String[] exp = path.split("\\.");
		return getInteger(defaultValue, exp);
	}
	
	public int getInteger(String...path){
		
		return getInteger(0, path);
	}
	
	public int getInteger(int defaultValue, String...path){
		
		JsonObject node = obj;
		
		for(int i=0; i < path.length-1 && node!= null; i++){
			node = node.getJsonObject(path[i]);
		}
		
		String  part = path[path.length -1];
		if(node != null && node.containsKey(part))
			return node.getInteger(part);
		else {
			return defaultValue;
		}
	}

	public String getString(String path){
		String[] exp = path.split("\\.");
		return getString(exp);
	}
	
	
	public String getString(String...path){
		
		JsonObject node = obj;
		
		for(int i=0; i < path.length-1 && node!= null; i++){
			node = node.getJsonObject(path[i]);
		}
		
		String  part = path[path.length -1];
		if(node != null && node.containsKey(part))
			return node.getString(part);
		else {
			return null;
		}
	}
}
