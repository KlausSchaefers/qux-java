package de.vommond.lunarmare;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface ModelService {
	
	/**
	 * Creates an object. returns the id if the newly created object
	 * @param object
	 * 			The object to be created
	 * @param handler
	 * 			The handler top handle the results
	 */
	public void create(JsonObject object, Handler<ModelServiceResult<String>> handler);
	
	public void replace(JsonObject object, Handler<ModelServiceResult<String>> handler);
	
	public void update(JsonObject object, Handler<ModelServiceResult<String>> handler);
	
	public void get(String id, Handler<ModelServiceResult<JsonObject>> handler);
	
	public void find(JsonObject query, Handler<ModelServiceResult<List<JsonObject>>> handler);
	
	public void delete(String id, Handler<ModelServiceResult<Boolean>> handler);
}
