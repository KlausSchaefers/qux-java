package com.qux.rest;

import com.qux.acl.EventAcl;
import com.qux.auth.ITokenService;
import com.qux.model.Mouse;
import com.qux.util.rest.MongoREST;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class MouseRest extends MongoREST {

	public MouseRest(ITokenService tokenService, MongoClient db) {
		super(tokenService, db, Mouse.class);
		setACL(new EventAcl(db));
	}

	@Override
	protected void beforeCreate(RoutingContext event, JsonObject json){
		String appID = getId(event, "appID");
		json.put("appID", appID);
	}
}
