package com.qux.rest;

import java.util.List;

import com.qux.acl.AppEventACL;
import com.qux.auth.ITokenService;
import com.qux.model.AppEvent;
import com.qux.model.Model;
import com.qux.util.rest.MongoREST;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class AppEventRest extends MongoREST {
	
	
	public AppEventRest(ITokenService tokenService, MongoClient db) {
		super(tokenService, db,  AppEvent.class);
		setACL(new AppEventACL());
	}
	
	@Override
	protected void beforeCreate(RoutingContext event, JsonObject json) {
		/**
		 * Make sure we have a user...
		 */
		json.put(AppEvent.FIELD_USER, getUser(event).getEmail());
	}
	
	
	public void findAll(RoutingContext event) {


		this.acl.canRead(getUser(event), event, allowed -> {
			if (allowed) {
				
				/**
				 * FIXME: Do some paging
				 */
				super.mongo.find(table, Model.all(), res ->{
					
					if(res.succeeded()){
						
						List<JsonObject> events = res.result();
						returnJson(event, events);
						
						
					} else {
						logger.error("findAll()");
					}
					
				});
				
				
				
			} else {
				error("read", "User " + getUser(event) + " tried to  read " + event.request().path());
				returnError(event, 404);
			}
		});
		
	}
	
	
	
	
}
	
	