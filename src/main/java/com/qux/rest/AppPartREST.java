package com.qux.rest;

import com.qux.acl.AppPartAcl;
import com.qux.acl.RoleACL;
import com.qux.auth.ITokenService;
import com.qux.model.AppPart;
import com.qux.model.User;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.mongo.MongoClient;
import com.qux.util.rest.MongoREST;
import com.qux.validation.PojoValidator;

public class AppPartREST<T> extends MongoREST{
	
	public AppPartREST(ITokenService tokenService, MongoClient db, Class<T> cls, String idParameter) {
		super(tokenService, db, cls);
		
		/**
		 * use simple pojo validation
		 */
		setValidator(new PojoValidator<T>(cls));
		
		/**
		 * Normal app acl applies, just create is reserved for people that
		 * can actually read the app. Also user must be registered.
		 */
		setACL(new RoleACL( new AppPartAcl(db)));
		
		/**
		 * set the mongo id parameter!
		 */
		setIdParameter(idParameter);
		
	}
	
	
	/********************************************************************************************
	 * Simple methods for CRUD methods form MongoRest
	 ********************************************************************************************/

	
	
	protected void beforeCreate(RoutingContext event, JsonObject json){
		User u = getUser(event);
		json.put("userID", u.getId());
		json.put("appID", getId(event, "appID"));
		json.put("created", System.currentTimeMillis());
	}
	

	
	protected void beforeUpdate(RoutingContext event, JsonObject json){
		User u = getUser(event);
		json.put("appID", getId(event, "appID"));
		json.put("userID", u.getId());
		json.put("lastUpdate", System.currentTimeMillis());
	}
	
	
	
	/********************************************************************************************
	 * Find & update by app. This is for parts that have a one to one relation, for instance the
	 * CommandStack, There is one command stack and it should have the same id in the rest interface
	 * as the app itself.
	 ********************************************************************************************/

	public Handler<RoutingContext> findOrCreateByApp() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				findOrCreateByApp(event);
			}
		};
	}

	public void findOrCreateByApp(RoutingContext event) {

		String appID  = getId(event, "appID");

		if(appID == null){
			returnError(event, 404);
		}
		
		if (this.acl != null) {
			this.acl.canRead(getUser(event), event, allowed -> {
				if (allowed) {
					findOrCreateByApp(event, appID);
				} else {
					error("findByApp", "User " + getUser(event) + " tried to  read ", event);
					returnError(event, 404);
				}
			});
		} else {
			this.findOrCreateByApp(event, appID);
		}
		
	}
	
	
	public void findOrCreateByApp(RoutingContext event, String appID) {

		mongo.findOne(table, AppPart.findByApp(appID), null, res -> {
			
			if(res.succeeded()){
				
				JsonObject json = res.result();
				if(json!=null){
					/**
					 * If something was found, return it
					 */
					returnJson(event, cleanJson(json));
					
				} else {
					
					/**
					 * If nothing found we create the app part
					 */
					json = createInstance(event, appID);
					create(event, json);
				
				}
			} else {
				returnError(event, 404);
			}
		});	
	}

	
	protected JsonObject createInstance(RoutingContext event, String appID){
		User u = getUser(event);
		JsonObject  json = new JsonObject()
			.put("userID", u.getId())
			.put("appID", getId(event, "appID"))
			.put("created", System.currentTimeMillis());
		
		return json;
	}
	
	
	/********************************************************************************************
	 * Update
	 ********************************************************************************************/

	public Handler<RoutingContext> updateByApp() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				updateByApp(event);
			}
		};
	}

	public void updateByApp(RoutingContext event) {

		String appID  = event.request().getParam("appID");
		
		if(appID == null){
			returnError(event, 404);
		}
		
		
		if (this.acl != null) {
			this.acl.canWrite(getUser(event), event, allowed -> {
				if (allowed) {
					updateByApp(event, appID);
				} else {
					error("update", "User " + getUser(event) + " tried to  update " + appID);
					returnError(event, 405);
				}
			});
		} else {
			this.updateByApp(event, appID);
		}
		
	}
	
	public void updateByApp(RoutingContext event, String appID) {
		
		JsonObject json = getJson(event);
		
		User u = getUser(event);
		json.put("appID", appID)
			.put("userID", u.getId())
			.put("lastUpdate", System.currentTimeMillis());
		
		
		if(this.validator !=null){
			this.validator.validate(json, true, errors ->{
				if(errors.isEmpty()){
					updateByApp(event, json);
				} else {
					returnError(event, table+ ".update.error");
				}
			});
		} else {
			updateByApp(event, json);
		}	
	}


	private void updateByApp(RoutingContext event, JsonObject json) {
		mongo.save(table, json, res -> {
			if(res.succeeded()){
				returnOk(event, table + ".update.success");
			} else {
				returnError(event, table + ".update.error");
			}
		});
	}
	
	
	
	
	

	
}
