package com.qux.rest;

import com.qux.acl.Acl;
import com.qux.acl.AppAcl;
import com.qux.bus.MailHandler;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.model.App;
import com.qux.model.AppEvent;
import com.qux.model.Model;
import com.qux.model.Team;
import com.qux.model.User;
import com.qux.util.DB;
import com.qux.util.Mail;
import com.qux.util.MongoREST;

public class TeamREST extends MongoREST {
	
	private Logger logger = LoggerFactory.getLogger(TeamREST.class);

	private final String user_db, team_db, app_db;
	
	public TeamREST(MongoClient db) {
		super(db, App.class);
		this.acl = new AppAcl(db);
		this.user_db = DB.getTable(User.class);
		this.team_db = DB.getTable(Team.class);
		this.app_db = DB.getTable(App.class);
	}
	
	/********************************************************************************************
	 * setTeam
	 ********************************************************************************************/

	public Handler<RoutingContext> createPermission() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				createPermission(event);
			}
		};
	}
	
	private void createPermission(RoutingContext event) {
	
		String appID = getId(event, "appID");
	
		/**
		 * only people that can write an app, can add team members and remove them
		 */
		this.acl.canWrite(getUser(event), event , allowed -> {
			if (allowed) {
				this.createPermission(event, appID);
			} else {
				error("createPermission", "User " + getUser(event) + " tried to set team at " + appID);
				returnError(event, "apps.team.member.add.error.read");
			}
		});
		
	}
	
	private void createPermission(RoutingContext event, String appID) {
		//log("setPermission", "enter > " + appID);
		
		
		JsonObject json = event.getBodyAsJson();
		
		if(json.containsKey("id")){
			error("createPermission()", "Legacy update method called! Use ''/rest/apps/:appID/team/:userID.json'' ");
			returnError(event,404);
		}
		if(json.containsKey("email") && json.containsKey("permission")){
			
			String email = json.getString("email");
			int permission = json.getInteger("permission");
			
			/**
			 * permission must be smaller null
			 */
			if(permission < Acl.OWNER){
				
				mongo.findOne(user_db, User.findByEmail(email) , null, res->{
					
					if(res.succeeded()){
						
						JsonObject user = res.result();
						if(user!= null){
							/**
							 * Ok we have a user. Lets do the update
							 */
							createPermission(event, appID, user, permission);
						} else {
							/**
							 * it might happen there is no user with the mail!
							 */
							returnError(event, "apps.team.member.add.error.email");
						}	
					} else {
						returnError(event, 404);
					}
				});
				
			} else {
				error("setPermission", "User " + getUser(event) + " tried to set owner permission!");
				returnError(event, "apps.team.member.add.error.owner");
			}
		
			
		} else {
			error("setPermission", "Wromng json from user " + getUser(event));
			returnError(event, 405);
		}
	}
	
	
	private void createPermission(RoutingContext event, String appID, JsonObject user, int permission) {
		logger.debug("createPermission() > enter");	
		
		AppEvent.send(event, getUser(event).getEmail(),AppEvent.TYPE_APP_CREATE_PERMISSION);
				
		if(permission > 0){
			
			JsonObject team = Team.create(user.getString("_id"), appID, permission);
			
			mongo.insert(team_db,team, res->{
				if(res.succeeded()){		
					returnOk(event, "apps.team.member.add.success");
					sendAddMail(event, appID, user);
				} else {
					res.cause().printStackTrace();
					returnError(event, 404);
				}
			});
		} else {
			returnError(event, "apps.team.member.add.error.0");
		}
	

	}

	private void sendAddMail(RoutingContext event, String appID, JsonObject user) {
		logger.debug("sendAddMail() > enter");	

		
		/**
		 * Now send mail
		 */
		mongo.findOne(app_db, App.findById(appID), null, appFound->{
			if(appFound.succeeded()){
				JsonObject app = appFound.result();
				
				User from = getUser(event);
				
				JsonObject payload = new JsonObject()
						.put("name", user.getString("name"))
						.put("fromEmail", from.getEmail())
						.put("fromName", from.getName())
						.put("fromLastname", from.getName())
						.put("appName", app.getString("name"))
						.put("appID", app.getString("_id"));					
				
				Mail.to(user.getString("email"))
					.subject(" - A prototype was shared with you")
					.payload(payload)
					.template(MailHandler.TEMPLATE_TEAM_ADDED)
					.send(event);
			} else {
				logger.error("sendAddMail() > Could not find app " + appID);
			}
		});
	}
	

	/********************************************************************************************
	 * update permission
	 ********************************************************************************************/


	public Handler<RoutingContext> updatePermission() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				updatePermission(event);
			}
		};
	}
	
	private void updatePermission(RoutingContext event) {
	
		String appID = getId(event, "appID");
	
		/**
		 * only people that can write an app, can add team members and remove them
		 */
		this.acl.canWrite(getUser(event), event , allowed -> {
			if (allowed) {
				this.updatePermission(event, appID);
			} else {
				error("createPermission", "User " + getUser(event) + " tried to set team at " + appID);
				returnError(event, "apps.team.member.add.error.read");
			}
		});
		
	}
	
	private void updatePermission(RoutingContext event, String appID) {
		logger.info("updatePermission() > enter");
		
		JsonObject json = event.getBodyAsJson();
		if(json.containsKey("permission")){
			

			int permission = json.getInteger("permission");
			String userID = getId(event, Team.USER_ID);
			
			/**
			 * permission must be smaller null
			 */
			if(permission < Acl.OWNER){
				
				JsonObject update = new JsonObject()
					.put("$set", new JsonObject()
								 .put(Team.PERMISSION, permission));
						
				/**
				 * We update no all entries for the user / app combination!
				 */
				mongo.updateCollection(team_db, Team.findByUserAndApp(userID, appID), update, res->{
					
					if(res.succeeded()){										
						returnOk(event, "apps.team.member.add.success");
					} else {
						logger.error("updatePermission() Mongo Error" + res.cause().getMessage()); 
						returnError(event, "apps.team.member.add.error.email");
					}
				});
				
			} else {
				error("updatePermission", "User " + getUser(event) + " tried to set owner permission!");
				returnError(event, "apps.team.member.add.error.owner");
			}
		
			
		} else {
			error("updatePermission", "Wromng json from user " + getUser(event));
			returnError(event, 405);
		}
	}
	
	
	
	
	
	/********************************************************************************************
	 * remove permission
	 ********************************************************************************************/

	public Handler<RoutingContext> removePermission() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				removePermission(event);
			}
		};
	}
	
	
	public void removePermission(RoutingContext event) {
		String appID = getId(event, "appID");
	
		String userID = getId(event, "userID");
		User user = getUser(event);
		
		/**
		 * one cannot remove itself
		 */
		if(user.getId().equals(userID)){
			returnError(event, "apps.team.member.remove.error");
		}
		
		/**
		 * only people that can write an app, can add team members and remove them
		 */
		this.acl.canWrite(user, event , allowed -> {
			if (allowed) {
				this.removePermission(event, appID, userID);
			} else {
				error("removePermission", "User " + getUser(event) + " tried to remove permission at " + appID);
				returnError(event, "apps.team.member.remove.error");
			}
		});
	}
	
	public void removePermission(RoutingContext event, String appID, String userID) {
		mongo.removeDocuments(team_db, Team.findByUserAndApp(userID, appID), res->{
			if(res.succeeded()){
				returnOk(event, "apps.team.member.remove.success");
			} else {
				res.cause().printStackTrace();
				returnError(event, 404);
			}
		});
	}
	
	
	/********************************************************************************************
	 * get suggestions: all users the current user shares apps with!
	 ********************************************************************************************/

	public Handler<RoutingContext> getSuggestion() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				getSuggestion(event);
			}
		};
	}

	private void getSuggestion(RoutingContext event) {
		//log("getSuggestion", "enter");
		event.response().putHeader("content-type", "application/json");
		
		User user = getUser(event);
		if(User.USER.equals(user.getRole())){
			
			/**
			 * Very ugly. Here we do a three times join!!!
			 * 
			 * 1) Get all apps for the user
			 * 
			 * 2) Get all userIds for those apps
			 * 
			 * 3) get all the users
			 */
			mongo.find(team_db, Team.findByUser(user),  res -> {
				if(res.succeeded()){
					List<JsonObject> acls = res.result();
					getUsers(event, acls);
				} else {
					returnError(event, "team.error");
				}
			});
			
		} else {
			error("getTeam", "The user "+ user + " tried to read teams");
			returnError(event, 404);
		}
		
	}

	private void getUsers(RoutingContext event, List<JsonObject> appACLs) {
		
		JsonArray appIDs = new JsonArray();
		for(JsonObject acl : appACLs){		
			if (acl.containsKey(Team.APP_ID) && acl.getString(Team.APP_ID) != null) {
				appIDs.add(acl.getString(Team.APP_ID));			
			} else {
				this.logger.error("getUsers() > Acl has no app id", acl.toString());
			}
		}
		
		mongo.find(team_db, Team.findByAppIds(appIDs),  res->{
			if(res.succeeded()){
				List<JsonObject> userACLs = res.result();		
				HashMap<String, Integer> userIDs = getUserMap(userACLs);
				getUsersByIds(event, userIDs);
			} else {
				returnError(event, "team.error");
			}
		});
	}


	protected void getUsersByIds(RoutingContext event, HashMap<String,Integer> userIDs) {
		
		JsonArray ids = new JsonArray();
		
		for(String id : userIDs.keySet()){
			ids.add(id);
		}
		
		mongo.findWithOptions(user_db, User.findByIDS(ids), Model.getFindOptions("name", "lastname", "email", "image"), res -> {
			
			if(res.succeeded()){
				JsonArray result = new JsonArray();
				List<JsonObject> users = res.result();
				for(JsonObject user : users){
					user.put("permission", userIDs.get(user.getString("_id")));
					result.add(cleanJson(user));
				}
				
				event.response().end(result.encode());
			} else {
				returnError(event, 404);
			}
		});
		
		
	}

	/********************************************************************************************
	 * get team: the users of an app
	 ********************************************************************************************/

	public Handler<RoutingContext> getTeam() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				getTeam(event);
			}
		};
	}

	private void getTeam(RoutingContext event) {
	
		event.response().putHeader("content-type", "application/json");
		
		String appID = getId(event, "appID");
	
		this.acl.canRead(getUser(event), event , allowed -> {
			if (allowed) {
				this.getTeam(event, appID);
			} else {
				error("getTeam", "User " + getUser(event) + " tried to  read " + event.request().path());
				returnError(event, 404);
			}
		});
		
	}
	
	private void getTeam(RoutingContext event, String appID) {
		//log("getTeam", "enter > " + id);
		
		mongo.find(team_db, Team.findByApp(appID), res -> {
			if(res.succeeded()){				
				
				List<JsonObject> userACLs = res.result();		
				
				HashMap<String, Integer> userIDs = getUserMap(userACLs);
				
				getUsersByIds(event, userIDs);
				
			} else {
				returnError(event, "team.error");
			}
		});
				
	}
	
	/********************************************************************************************
	 * Helper methods
	 ********************************************************************************************/

	
	private HashMap<String, Integer> getUserMap(List<JsonObject> userACLs) {
		HashMap<String, Integer> userIDs = new HashMap<String, Integer>();
		for(JsonObject acl : userACLs){			
			userIDs.put(acl.getString(Team.USER_ID), acl.getInteger(Team.PERMISSION));			
		}
		return userIDs;
	}

	protected JsonObject cleanJson(JsonObject user){
		user.remove("password");
		return super.cleanJson(user);
	}
}
