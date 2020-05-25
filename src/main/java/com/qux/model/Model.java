package com.qux.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@SuppressWarnings("deprecation")
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Model {
	
	protected String _id;
	
	protected long created = 0;

	protected long lastUpdate = 0;
	
	
	


	@JsonProperty("_id")
	public String getId() {
		return _id;
	}
	
	@JsonProperty("_id")
	public void setId(String id){
		this._id = id;
	}
	
	public long getCreated() {
		return created;
	}

	public void setCreated(long created) {
		this.created = created;
	}

	public long getLastUpdate() {
		return lastUpdate;
	}

	public void setLastUpdate(long lastUpdate) {
		this.lastUpdate = lastUpdate;
	}
	
	public static JsonObject getFields(String ...fieldNames ){
		JsonObject fields = new JsonObject();
		for(String name : fieldNames){
			fields.put(name, 1);
		}
		return fields;
	}
	
	public static FindOptions getFindOptions(String ...fieldNames ){
		JsonObject fields = new JsonObject();
		for(String name : fieldNames){
			fields.put(name, true);
		}
		return new FindOptions().setFields(fields);
	}
	
	public static JsonObject findById(String id){
		 return new JsonObject().put("_id", id);
	}
	
	public static JsonObject findByIDS(JsonArray ids){
		 return new JsonObject().put("_id", new JsonObject().put("$in", ids));
	}
	
	public static JsonObject all(){
		 return new JsonObject();
	}

}
