package com.qux.rest;

import java.util.List;

import com.qux.acl.Acl;
import com.qux.acl.LibraryAcl;
import com.qux.auth.ITokenService;
import com.qux.model.App;
import com.qux.model.AppEvent;
import com.qux.model.Library;
import com.qux.model.LibraryTeam;
import com.qux.model.Model;
import com.qux.util.DB;
import com.qux.util.Mail;
import com.qux.util.rest.MongoREST;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class LibraryRest extends MongoREST{
	
	private final String library_db;
	
	private final String library_team_db;

	public LibraryRest(ITokenService tokenService, MongoClient db) {
		super(tokenService, db, Library.class);
		this.setACL(new LibraryAcl(db));
		this.setPartialUpdate(false);
		this.setIdParameter("libID");
		this.library_db = DB.getTable(Library.class);
		this.library_team_db = DB.getTable(LibraryTeam.class);
	}
	
	
	public Handler<RoutingContext> findByUser() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				findByUser(event);
			}
		};
	}

	
	private void findByUser(RoutingContext event) {
		logger.debug("findByUser() > enter");
		/**
		 * Join over lib_team and lib table :-(
		 */
		long start = System.currentTimeMillis();	
		mongo.find(library_team_db, LibraryTeam.findByUser(getUser(event)), res ->{
			
			if(res.succeeded()){				
				JsonArray appIDs = new JsonArray();				
				List<JsonObject> acls = res.result();
				for(JsonObject acl : acls){
					if(acl.containsKey(LibraryTeam.LIB_ID) && acl.getString(LibraryTeam.LIB_ID) != null) {
						appIDs.add(acl.getString(LibraryTeam.LIB_ID));
					}
				}								
				long end = System.currentTimeMillis();
				logger.info("findByUser() > exit > library_team_db: " + (end - start) );
				this.logMetric(this.getClass(), "findByUser", (end - start));
				findByIds(event,  appIDs);		
			} else {
				logger.error("findByUser() > Mongo Error " + res.cause().getMessage()); 
				returnError(event, 404);
			}
		});		
	}

	private void findByIds(RoutingContext event,JsonArray appIDs) {
		logger.debug("findByIds() > enter " + appIDs);
		mongo.find(library_db, Library.findByIDS(appIDs), appRes->{
			
			if(appRes.succeeded()){		
				long dbDone = System.currentTimeMillis();
				
				List<JsonObject> apps = appRes.result();
				JsonArray result = new JsonArray();
				for(JsonObject app : apps){
					/**
					 * Sometimes the app might be marked for deletion, but it is still not deleted!
					 */
					if (!App.isDeleted(app)){
						/**
						 * Filter out all not needed widgets etc to speed up loading
						 */
						app = cleanJson(app);
						result.add(app);
					} else {
						logger.info("findByUser() > app " + app);
					}
				}
				event.response().end(result.encode());
				long end = System.currentTimeMillis();
				this.logMetric(this.getClass(), "findByUser[preview]", (end - dbDone), result.size());
			} else {
				logger.error("findByUser() > Mongo Error : " + appRes.cause().getMessage());
				returnError(event, 404);
			}
		});
	}
	
	
	/********************************************************************************************
	 * create
	 ********************************************************************************************/
	
	protected void beforeCreate(RoutingContext event, JsonObject json){		
		json.put("created", System.currentTimeMillis());
	}
	

	protected void afterCreate(RoutingContext event, JsonObject app) {
		logger.info("afterCreate() > enter " + app.getString("_id"));
		
		JsonObject owner = LibraryTeam.create(getUser(event), app.getString("_id"), Acl.OWNER);
		mongo.save(library_team_db, owner, ownerCreated ->{
			if(ownerCreated.succeeded()){
				logger.info("afterCreate() > Owner added > " + ownerCreated.result());
			} else {
				logger.error("afterCreate() Could not add owner"); 
			}
		});
		AppEvent.send(event, getUser(event).getEmail(), AppEvent.TYPE_LIB_CREATE, app.getString("_id"));
	}
	
	
	/********************************************************************************************
	 * create
	 ********************************************************************************************/
	
	public void delete(RoutingContext event, String appID) {
		log("delete", "Delete "+ appID);
			
		mongo.removeDocuments(library_db, Model.findById(appID), res->{
			if(res.succeeded()){
				mongo.removeDocuments(library_team_db, LibraryTeam.findByLib(appID), res2 -> {
					log("delete", "Remoed team lib");
				});
				returnOk(event, table + ".delete.success");
			} else {
				log("delete", "Cannot set isDeletedFlag");
				Mail.error(event, "AppREST.delete() > Could set isDeletedFLag");		
				returnError(event, table + ".delete.error");
			}
		});
	}
	
	
}

