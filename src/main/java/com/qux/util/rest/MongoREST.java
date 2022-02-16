package com.qux.util.rest;

import com.qux.auth.ITokenService;
import com.qux.model.Model;
import com.qux.model.Team;
import com.qux.model.User;
import com.qux.util.DB;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoREST extends CrudREST {
	
	protected static String BATCH = "batch";

	protected final MongoClient mongo;

	protected String table;

	protected String team_db;

	protected boolean returnUpdatedObject = true;

	protected boolean partialUpdate = true;
		
	protected Logger logger;
	
	private boolean isBatch = false;

	public MongoREST(ITokenService tokenService, MongoClient db, Class<?> cls) {
		super(tokenService);
		this.mongo = db;
		this.table = DB.getTable(cls);
		this.logger  =LoggerFactory.getLogger(getClass());
		this.team_db = DB.getTable(Team.class);
	}

	public void getACLList(User user, Handler<String> handler) {
		if (!user.hasRole(User.USER)) {
			handler.handle("[] - User is guest");
		} else {
			mongo.find(team_db, Team.findByUser(user), res ->{
				if (res.succeeded()) {
					StringBuilder appIDs = new StringBuilder();
					List<JsonObject> acls = res.result();
					for(JsonObject acl : acls){
						if(acl.containsKey(Team.APP_ID) && acl.getString(Team.APP_ID) != null) {
							appIDs.append(acl.getString(Team.APP_ID));
							appIDs.append(":");
							appIDs.append(acl.getInteger(Team.PERMISSION));
							appIDs.append(";");
						}
					}
					error("getACLList", "List for "+ user + ": " + appIDs);
					handler.handle(appIDs.toString());
				} else {
					error("getACLList", "Could not read ACL");
					handler.handle("[] - Mongo error");
				}
			});
		}


	}
	
	public void setBatch(boolean b) {
		this.isBatch = b;
	}

	
	public void setPartialUpdate(boolean enablePartialUpdate) {
		this.partialUpdate = enablePartialUpdate;
	}

	public void setReturnUpdatedObject(boolean returnUpdatedObject) {
		this.returnUpdatedObject = returnUpdatedObject;
	}

	public void logMetric(Class<?> component, String method, long ms){
		this.logMetric(component, method, ms, 1);
	}
	
	public void logMetric(Class<?> component, String method, long ms, long size){
		logger.info("MongoRest.logMetric() >> "  + component.getSimpleName()+ "." +method + "() : " + ms);
//		JsonObject timeMeasurent = new JsonObject()
//				.put("c", component.getSimpleName())
//				.put("m", method)
//				.put("created", System.currentTimeMillis())
//				.put("t", ms)
//				.put("s", size);
		
//		this.mongo.save(metricTable, timeMeasurent, res -> {
//			if (res.failed()){
//				error(0, "logMetric", "Could not save metric >> " + res.cause());
//			}
//		});
	}
	
	
	public void error(int i, String method, String message) {
		System.err.println(this.getClass().getSimpleName() +"."+method +"() > " + message);
		logger.error(method +"() > " +  message);
	
	}
	
	public void log(int level, String method, String message) {
		logger.info(method +"() > " +  message);
	}
	
	

	/**
	 * This methods creates the new object and returns it including the ID! The
	 * method is only invoked if the JSON is correct AND the user is allowed to
	 * create an new document!
	 * 
	 * Subclasses may overwrite the method!
	 * 
	 * @param event
	 *            The apex event
	 * @param json
	 *            The parsed and validated json!
	 */
	protected void create(RoutingContext event, JsonObject json) {
		mongo.insert(this.table, json, res -> {
			logger.info("create() > " + res.result() + " in " + this.table);
			if (res.succeeded()) {
				json.put("_id", res.result());
				afterCreate(event, json);
				returnJson(event, cleanJson(json));
			} else {
				res.cause().printStackTrace();
				returnError(event, table + ".create.error");
			}
		});
	}

	protected void afterCreate(RoutingContext event, JsonObject json){
		
	}
	
	/********************************************************************************************
	 * Update
	 * 
	 * http://docs.mongodb.org/manual/tutorial/modify-documents/
	 * 
	 ********************************************************************************************/

	
	protected void update(RoutingContext event, String id, JsonObject json) {
		beforeUpdate(event, id, json);
		if(this.partialUpdate){
			particalUpdate(event, id, json);
		} else {
			completeUpdate(event, id, json);
		}
	}

	protected void particalUpdate(RoutingContext event, String id, JsonObject json) {
		json = new JsonObject().put("$set", json);
		mongo.updateCollection(table, Model.findById(id), json, res -> {
			if(res.succeeded()){
				returnUpdate(event, id);
			} else {
				logger.error("particalUpdate() > Cannot update "+ res.cause().getMessage());
				returnError(event, "user.update.error");
			}
			
		});
	}



	protected void completeUpdate(RoutingContext event, String id, JsonObject json) {
	
		/**
		 * fix in case there is no id in the json!
		 */
		if(!json.containsKey("_id")){
			json.put("_id", id);
		}
		
		mongo.save(table, json, res -> {
			if(res.succeeded()){
				returnUpdate(event, id);
			} else {
				returnError(event, "user.update.error");
			}
		});
	}
	
	protected void returnUpdate(RoutingContext event, String id) {
		
		if(this.returnUpdatedObject){
			mongo.findOne(table, Model.findById(id), null, res->{
				if(res.succeeded()){
					JsonObject result = res.result();
					afterUpdate(event, id, result);
					if(result!= null){
						event.response().end(cleanJson(result).encode());
					} else {
						returnError(event,  table + ".update.error.2");
					}
					
				} else {
					
					returnError(event, table +".update.error.2");
				}
			});
		} else {
			returnOk(event, table +".update.success");
		}
		
		
	}
	
	protected void beforeUpdate(RoutingContext event, String id, JsonObject json) {
		
		
	}

	protected void afterUpdate(RoutingContext event, String id, JsonObject json) {
		
	}
	/********************************************************************************************
	 * Find
	 ********************************************************************************************/

	
	
	public void find(RoutingContext event, String id) {

		mongo.findOne(table, Model.findById(id), null, res -> {
			
			if(res.succeeded()){
				
				JsonObject json = res.result();
				if(json!=null){
					returnJson(event, cleanJson(json));
				} else {
					returnError(event, 404);
				}

			} else {
				returnError(event, 404);
			}
		});
		
		
				
	}
	
	
	
	/********************************************************************************************
	 * Find By
	 ********************************************************************************************/
	
	protected void findBy(RoutingContext event){
		JsonObject query = getPathQuery(event);
		String queryString = event.request().query();
		Map<String, String> map = parseQuery(queryString);
		if (this.isBatch && map.containsKey(BATCH)) {
			MongoResultPump pump = new MongoResultPump(event);
			mongo.findBatch(table, query)
					.exceptionHandler(err -> pump.error())
					.endHandler(v -> pump.end())
					.handler(doc -> pump.pump(doc));

		} else {
			mongo.find(table, query, res -> {
				if(res.succeeded()){
					List<JsonObject> list = res.result();
					JsonArray result = new JsonArray();
					for(JsonObject obj : list){
						if(findByCanRead(event, obj)){
							obj = cleanJson(obj);
							result.add(obj);
						}
					}
					returnJson(event, result);
				} else {
					returnError(event, 404);
				}
			});
		}
	}

	public boolean findByCanRead(RoutingContext event, JsonObject obj){
		return true;
	}
	
	

	/********************************************************************************************
	 * Find By
	 ********************************************************************************************/
	
	protected void countBy(RoutingContext event){
		JsonObject query = getPathQuery(event);
		mongo.count(table, query, res -> {
			if(res.succeeded()){
				long count = res.result();
				returnJson(event, new JsonObject().put("count", count));
			} else {
				returnError(event, 404);
			}
		});
	}

	
	/********************************************************************************************
	 * Delete
	 ********************************************************************************************/

	
	public void delete(RoutingContext event, String id) {
		logger.warn("delete() > enter > " + id);	
		mongo.removeDocuments(table, Model.findById(id), res->{
			if(res.succeeded()){
				returnOk(event, table + ".delete.success");				
				afterDelete(event, id);				
			} else {
				returnError(event, table + ".delete.error");
			}
		});
	}
	
	/**
	 * Template class for child classes to overwrite.
	 * Will be called after an entity was successfully deleted!
	 * 
	 * @param event The Apex event
	 * 
	 * @param id The id of the object that was deleted!
	 */
	public void afterDelete(RoutingContext event, String id) {
		
	}
	
	

	public void deleteBy(RoutingContext event, String id){
		
		JsonObject query = getPathQuery(event);		
		logger.warn("deleteBy() > enter > " + query.encode());		
	
		mongo.removeDocuments(table, query, res->{
			if(res.succeeded()){
				returnOk(event, table + ".delete.success");				
				afterDelete(event, id);				
			} else {
				returnError(event, table + ".delete.error");
			}
		});
		
	}
	
	
	/********************************************************************************************
	 * Helper
	 ********************************************************************************************/

	
	protected JsonObject cleanJson(JsonObject json){
		try{
			if(json.containsKey("_id")){
				json.put("id", json.getString("_id"));
			}	
		}catch(Exception e){
			this.logger.error("cleanJson() > Fucked up json " + json.toString()); 
		}
		
		return json;
	}
		

	
	protected JsonArray toArray(List<JsonObject> list) {
		JsonArray result = new JsonArray();
		for(JsonObject obj : list){
			obj = cleanJson(obj);
			result.add(obj);
		}
		return result;
	}
	
	public static Map<String, String> parseQuery (String query) {
		Map<String, String> result = new HashMap<String, String>();
		if (query != null) {
			String[] parts = query.split("&");
			for (String part : parts) {
				String[] keyValue = part.split("=");
				if (keyValue.length == 2) {
					result.put(keyValue[0], keyValue[1]);
				}
			}
		}
		return result;
	}
}
