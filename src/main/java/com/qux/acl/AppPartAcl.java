package com.qux.acl;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.mongo.MongoClient;
import com.qux.model.User;

public class AppPartAcl extends AppAcl implements Acl{
	
	public AppPartAcl(MongoClient client){
		super(client);
	}
	
	public AppPartAcl(MongoClient client, String id){
		super(client, id);
	}

	
	@Override
	public void canCreate(User user, RoutingContext event, Handler<Boolean> handler) {
		canRead(user, event, handler);
	}

}
