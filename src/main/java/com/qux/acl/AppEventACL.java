package com.qux.acl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.model.User;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class AppEventACL implements Acl{
	
	private Logger logger = LoggerFactory.getLogger(AppEventACL.class);
	

	@Override
	public void canCreate(User user, RoutingContext event,Handler<Boolean> handler) {
		handler.handle(true);		
	}

	@Override
	public void canRead(User user, RoutingContext event,Handler<Boolean> handler) {
		handler.handle(false);
	}

	@Override
	public void canWrite(User user, RoutingContext event,Handler<Boolean> handler) {
		handler.handle(false);	
	}

	@Override
	public void canDelete(User user, RoutingContext event,Handler<Boolean> handler) {
		canRead(user, event, handler);
	}


}
