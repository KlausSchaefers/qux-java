package com.qux.util;

import com.qux.model.User;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * base class that does some kind of di!
 * 
 * @author klaus_schaefers
 *
 */
public abstract class REST {
	
	public static final String SESSION_USER = "session.user";

	protected final JSONMapper mapper = new JSONMapper();
	
	private Logger logger = LoggerFactory.getLogger(REST.class);
	
	private String idParameter = "id";

	public void setIdParameter(String id) {
		this.idParameter = id;
	}

	public void log(String method, String message) {
		log(0, method, message);
	}

	public void error(String method, String message) {
		error(0, method, message);
	}
	
	public void error(String method, String message, 	RoutingContext event) {
		error(0, method, message + " > " + event.request().path());
	}
	
	public void error(int i, String method, String message) {
		logger.error(method+"() > " + message );
	}
	
	public void log(int level, String method, String message) {
		logger.info(method+"() > " + message );
	}
	
	protected User getUser(RoutingContext event) {

		try {
			/**
			 * First check if we have a JWT user (since 2.2.5)
			 */
			User tokenUser = TokenService.getUser(event);
			if (tokenUser != null) {
				logger.debug("Rest.getUser() > found JWT user");
				return tokenUser;
			}
		} catch (Exception e) {
			error("getUser", "could not parse token", event);
		}
		
		/**
		 * DO SOMETHING WITH THE SESSION.. 
		 * Can we somehow create a unqiue id for 
		 * the guest so he can edit and
		 */
		return User.GUEST_USER;
	}
	
	protected void setUser(User user, RoutingContext event){
		log("setUser", "enter > " + user);
	}
	

	
	protected void returnError(RoutingContext event, int code) {
		event.response().setStatusCode(code);
		event.response().end();
		
	}

	protected void returnJson(RoutingContext event, JsonObject result){
		event.response().putHeader("content-type", "application/json");
		event.response().end(result.encodePrettily());	
	}
	

	protected void returnJson(RoutingContext event, JsonArray result){
		event.response().putHeader("content-type", "application/json");
		event.response().end(result.encodePrettily());	
	}
	

	protected void returnJson(RoutingContext event, List<JsonObject> list) {
		JsonArray arr= new JsonArray();
		for(JsonObject o : list){
			if(!o.containsKey("id")){
				o.put("id", o.getString("_id"));
			}
			arr.add(o);
		}
		event.response().putHeader("content-type", "application/json");
		event.response().end(arr.encodePrettily());
	}
	
	protected void returnError(RoutingContext event, String error){
		JsonObject result = new JsonObject().put("type", "error");
		result.put("errors", new JsonArray().add(error));
		
		event.response().end(result.encodePrettily());
	
	}
	
	protected void returnError(RoutingContext event, List<String> errors){
		JsonObject result = new JsonObject().put("type", "error");
		result.put("errors", errors);
		
	
		event.response().end(result.encodePrettily());
	}
	
	protected void returnOk(RoutingContext event, String code){
		JsonObject result = new JsonObject()
			.put("details", code)
			.put("status", "ok");
		event.response().putHeader("content-type", "application/json");
		event.response().end(result.encodePrettily());
	}
	
	
	protected String getId(RoutingContext event) {
		return event.request().getParam(idParameter);
	}
	
	protected String getId(RoutingContext event, String id) {
		return event.request().getParam(id);
	}
	
	protected boolean hasParam(RoutingContext event, String id) {
		return event.request().params().contains(id);
	}
	
	protected JsonObject getJson(RoutingContext event){
		try{
			return event.getBodyAsJson();
		} catch(Exception e){
			System.err.println("getJson() > Error for " + event.request().path());
			System.out.println(event.getBodyAsString());
			e.printStackTrace();
		}
		return null;
	}
	
}
