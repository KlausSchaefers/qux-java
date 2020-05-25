package com.qux.acl;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import com.qux.model.User;

public class TrueAcl implements Acl{

	@Override
	public void canCreate(User user, RoutingContext event,Handler<Boolean> handler) {
		handler.handle(true);
		
	}

	@Override
	public void canRead(User user, RoutingContext event,Handler<Boolean> handler) {
		handler.handle(true);
		
	}

	@Override
	public void canWrite(User user, RoutingContext event,Handler<Boolean> handler) {
		handler.handle(true);
		
	}

	@Override
	public void canDelete(User user, RoutingContext event, Handler<Boolean> handler) {
		handler.handle(true);
		
	}

}
