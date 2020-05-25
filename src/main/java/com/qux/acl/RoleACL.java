package com.qux.acl;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import com.qux.model.User;

public class RoleACL implements Acl{
	
	private final Acl acl;
	
	private String createRole = User.USER;
	
	private String readRole = User.USER;
	
	private String writeRole = User.USER;
	
	private String deleteRole = User.USER;
	
	
	public RoleACL(Acl a){
		this.acl = a;
	}
	
	public RoleACL(){
		this.acl = new TrueAcl();
	}
		
	public RoleACL read(String role){
		this.readRole = role;
		return this;
	}
	
	public RoleACL write(String role){
		this.writeRole = role;
		return this;
	}
	
	
	public RoleACL delete(String role){
		this.deleteRole = role;
		return this;
	}
	
	public RoleACL create(String role){
		this.createRole = role;
		return this;
	}
	

	@Override
	public void canCreate(User user, RoutingContext event,Handler<Boolean> handler) {
		if(user.hasRole(createRole)){
			acl.canCreate(user, event, handler);
		} else {
			handler.handle(false);
		}
	}

	@Override
	public void canRead(User user, RoutingContext event, Handler<Boolean> handler) {
		if(user.hasRole(readRole)){
			acl.canRead(user, event, handler);
		} else {
			handler.handle(false);
		}
	}

	@Override
	public void canWrite(User user, RoutingContext event, Handler<Boolean> handler) {
		if(user.hasRole(writeRole)){
			acl.canWrite(user, event, handler);
		} else {
			handler.handle(false);
		}
	}

	@Override
	public void canDelete(User user, RoutingContext event, Handler<Boolean> handler) {
		
		if(user.hasRole(deleteRole)){
			
			acl.canDelete(user, event, handler);
			
		} else {
			handler.handle(false);
		}
	}

}
