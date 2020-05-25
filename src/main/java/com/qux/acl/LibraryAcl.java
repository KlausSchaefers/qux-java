package com.qux.acl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.model.Library;
import com.qux.model.LibraryTeam;
import com.qux.model.User;
import com.qux.util.DB;
import io.vertx.core.Handler;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class LibraryAcl extends MongoAcl implements Acl{
	
	private Logger logger = LoggerFactory.getLogger(LibraryAcl.class);
	
	private final String library_db;
	
	private final String library_team_db;


	public LibraryAcl(MongoClient client) {
		super(client);
		this.library_db = DB.getTable(Library.class);
		this.library_team_db = DB.getTable(LibraryTeam.class);
	}

	public void canCreate(User user, RoutingContext event, Handler<Boolean> handler) {
		if(user.hasRole(User.USER)){
			handler.handle(true);
		} else {
			handler.handle(false);
		}
	}


	@Override
	public void canRead(User user, RoutingContext event, Handler<Boolean> handler) {
		
		String libID = getId(event);	
		
		logger.debug("canRead() > "+ libID);
		
		/**
		 * First check if there is an Team entry, otherwise check if app is public. 
		 * 
		 * FIXME: For public apps we could have also a permission in the team 
		 */
		client.count(library_team_db, LibraryTeam.canRead(user, libID), res->{

			if(res.succeeded() && res.result() == 1l){
				handler.handle(true);
			} else {	
				logger.error("canRead() > Check Public because > success: "+ res.succeeded() +  " >> count:" + res.result());
				client.count(library_db, Library.findPublicByID(libID), isPublic ->{
					assertOne(isPublic, handler, event);
				});
			}
		
		});
	}

	@Override
	public void canWrite(User user, RoutingContext event, Handler<Boolean> handler) {
		String id = getId(event);
		client.count(library_team_db, LibraryTeam.canWrite(user, id), res->{
			assertOne(res, handler, event);
		});
	}

	@Override
	public void canDelete(User user, RoutingContext event, Handler<Boolean> handler) {
		String id = getId(event);
		client.count(library_team_db, LibraryTeam.isOwner(user, id),  res->{
			assertOne(res, handler, event);
		});
	}
	
	protected String getId(RoutingContext event) {
		return event.request().getParam("libID");
	}
	

}
