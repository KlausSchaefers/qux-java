package com.qux.acl;

import io.vertx.core.Handler;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import com.qux.model.Invitation;
import com.qux.model.User;
import com.qux.util.DB;

public class InvitationACL extends MongoAcl implements Acl{
	
	private final String inv_db;

	public InvitationACL(MongoClient client) {
		super(client);
		this.inv_db = DB.getTable(Invitation.class);
	}

	@Override
	public void canCreate(User user, RoutingContext event,Handler<Boolean> handler) {
		canTest(user, event, handler);
	}

	@Override
	public void canRead(User user, RoutingContext event, Handler<Boolean> handler) { 
		canTest(user, event, handler);
	}

	@Override
	public void canWrite(User user, RoutingContext event, Handler<Boolean> handler) {
		canTest(user, event, handler);
	}

	@Override
	public void canDelete(User user, RoutingContext event, Handler<Boolean> handler) {
		canTest(user, event, handler);
	}
	
	public void canTest(User user, RoutingContext event, Handler<Boolean> handler){
		String appID  = event.request().getParam("appID");
		String hash = event.request().getParam("hash");
		client.count(inv_db, Invitation.canTest(appID, hash), res ->{
			assertOne(res, handler, event);
		});
	}
}
