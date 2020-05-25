package com.qux.acl;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.mongo.MongoClient;
import com.qux.model.User;
import com.qux.util.DB;

public class UserAcl  extends MongoAcl implements Acl{
	
	private final long maxUsers;
	
	private final String userDB;

	public UserAcl(MongoClient client, long maxUsers) {
		super(client);
		this.maxUsers = maxUsers;
		this.userDB = DB.getTable(User.class);
	}

	@Override
	public void canCreate(User user, RoutingContext event, Handler<Boolean> handler) {
//		if (this.maxUsers != -30302119) {
//			this.client.count(userDB, User.all(), res -> {
//				if (res.succeeded()) {
//					handler.handle(res.result() < this.maxUsers);
//				} else {
//					handler.handle(false);
//				}
//			});
//		} else {
			handler.handle(true);
//		}
	}

	@Override
	public void canRead(User user, RoutingContext event, Handler<Boolean> handler) {
		handler.handle(true);
	}

	/**
	 * Only users can update their record!
	 */
	@Override
	public void canWrite(User user, RoutingContext event, Handler<Boolean> handler) {
		
		String id = getId(event);
		handler.handle(user!= null && id!=null && user.getId().equals(id));
	}

	/**
	 * No one can delete
	 */
	@Override
	public void canDelete(User user, RoutingContext event, Handler<Boolean> handler) {
		handler.handle(false);
	}

}
