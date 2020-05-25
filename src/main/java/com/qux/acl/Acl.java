package com.qux.acl;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import com.qux.model.User;

public interface Acl {

	public static int NONE = 0;
	
	public static int READ = 1;
	
	public static int WRITE = 2;
	
	public static int OWNER = 3;
	
	
	public void canCreate(User user, RoutingContext event, Handler<Boolean> handler);
	
	public void canRead(User user, RoutingContext event, Handler<Boolean> handler);
	
	public void canWrite(User user, RoutingContext event, Handler<Boolean> handler);
	
	public void canDelete(User user, RoutingContext event, Handler<Boolean> handler);
	
	
}
