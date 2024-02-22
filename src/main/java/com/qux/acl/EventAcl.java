package com.qux.acl;

import io.vertx.core.Handler;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import com.qux.model.Team;
import com.qux.model.User;
import com.qux.util.DB;

/**
 * 
 * Events can only be written by everybody that can read the app
 *
 */
public class EventAcl extends MongoAcl implements Acl{
	
	private final String team_db;

	public EventAcl(MongoClient client) {
		super(client);
		this.team_db = DB.getTable(Team.class);
	}

	@Override
	public void canCreate(User user, RoutingContext event,Handler<Boolean> handler) {
		
		String appID = event.request().params().get("appID");
		client.count(team_db, Team.canRead(user, appID), res->{
			assertOne(res, handler, event);
		});
		
	}

	@Override
	public void canRead(User user, RoutingContext event,Handler<Boolean> handler) {
		if(user.hasRole(User.USER)){
			String appID = event.request().params().get("appID");
			client.count(team_db, Team.canRead(user, appID), res->{
				assertOne(res, handler, event);
			});
		} else {
			handler.handle(false);
		}
		
	}
	@Override
	public void canWrite(User user, RoutingContext event,	Handler<Boolean> handler) {
		if(user.hasRole(User.USER)){
			String appID = event.request().params().get("appID");
			client.count(team_db, Team.canWrite(user, appID), res->{
				assertOne(res, handler, event);
			});
		} else {
			handler.handle(false);
		}
	}
	@Override
	public void canDelete(User user, RoutingContext event,	Handler<Boolean> handler) {
		if(user.hasRole(User.USER)){
			String appID = event.request().params().get("appID");
			client.count(team_db, Team.canWrite(user, appID), res->{
				assertOne(res, handler, event);
			});
		} else {
			handler.handle(false);
		}
	}
	
	

}
