package com.qux.rest;

import java.util.List;

import com.qux.acl.NotificationACL;
import com.qux.auth.ITokenService;
import com.qux.model.Notification;
import com.qux.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.util.rest.MongoREST;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class NotificationREST extends MongoREST{
	
	private Logger logger = LoggerFactory.getLogger(NotificationREST.class);
		
	public NotificationREST(ITokenService tokenService, MongoClient db) {
		super(tokenService, db, Notification.class);
		this.setACL(new NotificationACL());
	}
	
	public void findByUser(RoutingContext event) {
		logger.info("findByUser() > enter");
		User user = getUser(event);
		mongo.find(table, Notification.findByUserOrPublic(user.getId()), res -> {
			if(res.succeeded()){
				List<JsonObject> json = res.result();
				if(json!=null){
					returnJson(event, json);
				} else {
					returnError(event, 404);
				}
			} else {
				returnError(event, 404);
			}
		});
	}
	
}
