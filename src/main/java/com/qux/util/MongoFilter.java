package com.qux.util;


import io.vertx.core.json.JsonObject;

public interface MongoFilter<I,O> {

	public O filter(I input);
	
	/**
	 * Default filter which mapps _id to id. Also removes unwanted fields.
	 */
	public static MongoFilter<JsonObject, JsonObject> Default(String... remove) {
		return new MongoFilter<JsonObject, JsonObject>() {
			@Override
			public JsonObject filter(JsonObject o) {
				o.put("id", o.getString("_id"));
				for (String s : remove){
					o.remove(s);
				}
				return o;
			}
		};
	}
}
