package com.qux.acl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import com.qux.model.App;
import com.qux.model.Team;
import com.qux.model.User;
import com.qux.util.DB;

public class AppAcl extends MongoAcl implements Acl{
	
	private final String app_db, team_db;
	
	private Logger logger = LoggerFactory.getLogger(AppAcl.class);
	
	public AppAcl(MongoClient client){
		this(client, "appID");
	}
	
	public AppAcl(MongoClient client, String id){
		super(client, id);
		this.app_db = DB.getTable(App.class);
		this.team_db = DB.getTable(Team.class);
	}

	
	@Override
	public void canCreate(User user, RoutingContext event, Handler<Boolean> handler) {
		if(user.hasRole(User.USER)){
			handler.handle(true);
		} else {
			handler.handle(false);
		}
	}

	@Override
	public void canRead(User user, RoutingContext event, Handler<Boolean> handler) {
		String appID = getId(event);
		logger.debug("canRead() > "+ appID);
		
		/**
		 * First check if there is an Team entry, otherwise check if app is public
		 */
		client.count(team_db, Team.canRead(user, appID), res-> {
			if(res.succeeded() && res.result() == 1l){
				handler.handle(true);
			} else {	
				logger.error("canRead() > Check Public because > success: "+ res.succeeded() +  " >> count:" + res.result());
				client.count(app_db, App.findPublicByID(appID), isPublic ->{
					assertOne(isPublic, handler, event);
				});
			}
		});
	}

	@Override
	public void canWrite(User user, RoutingContext event, Handler<Boolean> handler) {
		String id = getId(event);
		client.count(team_db, Team.canWrite(user, id), res->{
			assertOne(res, handler, event);
		});
	}

	@Override 
	public void canDelete(User user, RoutingContext event, Handler<Boolean> handler) {
		String id = getId(event);
		client.count(team_db, Team.isOwner(user, id),  res->{
			assertOne(res, handler, event);
		});
	}
	
	
	

}
