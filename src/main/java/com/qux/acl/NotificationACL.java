package com.qux.acl;

import com.qux.MATC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.model.User;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class NotificationACL implements Acl{

		private Logger logger = LoggerFactory.getLogger(NotificationACL.class);


		@Override
		public void canCreate(User user, RoutingContext event,Handler<Boolean> handler) {
			canDelete(user, event, handler);
		}

		@Override
		public void canRead(User user, RoutingContext event,Handler<Boolean> handler) {
			canDelete(user, event, handler);
		}

		@Override
		public void canWrite(User user, RoutingContext event,Handler<Boolean> handler) {
			canDelete(user, event, handler);
		}

		@Override
		public void canDelete(User user, RoutingContext event,Handler<Boolean> handler) {
			handler.handle(user.getEmail().equals(MATC.ADMIN));
		}
}
