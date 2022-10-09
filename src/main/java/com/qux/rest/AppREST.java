package com.qux.rest;

import java.util.HashMap;	
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.qux.acl.Acl;
import com.qux.acl.AppAcl;
import com.qux.acl.RoleACL;
import com.qux.auth.ITokenService;
import com.qux.blob.IBlobService;
import org.slf4j.LoggerFactory;

import com.qux.model.App;
import com.qux.model.AppEvent;
import com.qux.model.AppPart;
import com.qux.model.Image;
import com.qux.model.Invitation;
import com.qux.model.Model;
import com.qux.model.Team;
import com.qux.model.User;
import com.qux.util.DB;
import com.qux.util.Mail;
import com.qux.util.rest.MongoREST;
import com.qux.util.rest.MongoUtil;
import com.qux.util.PreviewEngine;
import com.qux.util.Util;
import com.qux.validation.AppValidator;
import io.vertx.core.Handler;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class AppREST extends MongoREST {
	
	private final String[] part_dbs;
	
	private final MongoUtil util = new MongoUtil();
	
	private final PreviewEngine preview = new PreviewEngine();
	
	private final String team_db, inv_db, image_db;

	private final IBlobService blobService;


	/**
	 * Default AppREst with users ACL
	 */
	public AppREST(ITokenService tokenService, IBlobService blobService,  MongoClient db ) {
		this(tokenService, blobService, db, new RoleACL(new AppAcl(db)).read(User.GUEST));
	}
	
	public AppREST(ITokenService tokenService, IBlobService blobService, MongoClient db, Acl acl) {
		super(tokenService, db, App.class);
		this.setACL(acl);
		this.setValidator(new AppValidator(db, this));
		this.setReturnUpdatedObject(false);
		setIdParameter("appID");
		this.team_db = DB.getTable(Team.class);
		this.inv_db = DB.getTable(Invitation.class);
		this.image_db = DB.getTable(Image.class);
		this.part_dbs = App.getModelParts();
		this.blobService = blobService;
		super.logger = LoggerFactory.getLogger(AppREST.class);
	}
	
	/********************************************************************************************
	 * Copy
	 ********************************************************************************************/

	public Handler<RoutingContext> copy() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				copy(event);
			}
		};
	}

	public void copy(RoutingContext event) {
		String id  = getId(event);
		if (this.acl != null) {
			User user = getUser(event);
			this.acl.canWrite(user, event, allowed -> {
				if (allowed) {
					AppEvent.send(event, user.getEmail(), AppEvent.TYPE_APP_COPY_PRIVATE);	
					copy(event, id);
				} else {
					
					/**
					 * Check if the app is public
					 */
					this.mongo.count(table, App.findPublicByID(id), res ->{
						boolean isPublic  = res.result() == 1l;
						if(isPublic == true){
							this.logger.info("copy() > Copy public "+ id);
							AppEvent.send(event, user.getEmail(), AppEvent.TYPE_APP_COPY_PUBLIC);		
							copy(event, id);
						} else {
							error("copy", "User " + getUser(event) + " tried to  copy " + id);
							returnError(event, 405);
						}
					});
				}
			});
		} else {
			this.copy(event, id);
		}
	}
	
	
	private void copy(RoutingContext event, String id) {

		JsonObject params = event.getBodyAsJson();
		if(params != null){
			String name = params.getString("name");
					
			/**
			 * 1) Load app
			 */
			mongo.findOne(table, App.findById(id), null, appResponse->{
				if(appResponse.succeeded()){
					
					JsonObject app = appResponse.result();	
					
					/**
					 * 2) Load all images
					 */
					mongo.find(image_db, Image.findByApp(id), imageResponse ->{
						
						if(imageResponse.succeeded()){
							List<JsonObject> images= imageResponse.result();
							
							/**
							 * Copy
							 */
							copy(event, name, app, images);
							
						} else {
							error("copy", "Cannot load images "+ id);
							returnError(event, "app.copy.error");
						}
					});	
				} else {
					error("copy", "Cannot load app "+ id);
					returnError(event, "app.copy.error");
				}
			});
			
		} else {
			error("copy", "No data passed");
			returnError(event, "app.copy.error");
		}
		
		
	}



	private void copy(RoutingContext event, String name, JsonObject oldApp, List<JsonObject> images) {
		JsonObject fixedApp = copyApp(name, oldApp);
		mongo.save(table, fixedApp, res->{
			if(res.succeeded()){
				String id = res.result();
				fixedApp.put("id", id);	
				fixedApp.put("_id", id);		
				this.afterCreate(event, fixedApp);
				
				try{
					this.createImageFolder(event, oldApp, images, fixedApp);
				} catch(Exception e){
					e.printStackTrace();
					error("copy", "Could not copy images");
					returnError(event, 405);
				}
			} else {
				error("copy", "Could not save copy");
				returnError(event, "app.copy.error");
			}
		});
	}

	
	private JsonObject copyApp(String name, JsonObject oldApp) {
		/**
		 * Create a copy
		 */
		JsonObject newApp = oldApp.copy();
		newApp.remove("_id");
		newApp.remove("id");
		newApp.remove("isDirty");
		newApp.remove("lastBackup");
		newApp.put("isPublic", false);
		newApp.put("name", name);
		newApp.put("created", System.currentTimeMillis());
		newApp.put("lastUpdate", System.currentTimeMillis());
		newApp.put("parent", oldApp.getString("_id"));
		
		/**
		 * remove %
		 */
		String json = newApp.encode();
		json = json.replaceAll("%", "\\$perc;");
		
		JsonObject fixedApp = new JsonObject(json);
		return fixedApp;
	}
	
	
	private void createImageFolder(RoutingContext event, JsonObject oldApp, List<JsonObject> images, JsonObject newApp){

		String newID = newApp.getString("_id");
		this.blobService.createFolder(event, newID);
		copyImages(event, images, newApp);
	}

	private void copyImages(RoutingContext event, List<JsonObject> images, JsonObject newApp) {
		
		String newID = newApp.getString("_id");
		
		/**
		 * Copy all images
		 */
		Map<String, String> replacements = new HashMap<>();
		for(JsonObject image : images){

			JsonObject newImage = image.copy();
			newImage.remove("id");
			newImage.remove("_id");
			
			String oldUrl = image.getString("url");
			String newUrl = Image.replaceAppIDinImageUrl(oldUrl, newID);
			
			newImage.put("appID", newID);
			newImage.put("url", newUrl);
			
			replacements.put(oldUrl, newUrl);
		
			/**
			 * Save async in mongo and copy file also afterwards
			 */
			mongo.save(image_db, newImage, imageResult ->{
				if (imageResult.succeeded()){
					this.blobService.copyBlob(event, oldUrl, newUrl, copyResult -> {
						if(!copyResult){
							this.logger.error("copyImages() > Could savecopy image" + oldUrl + " > " + newUrl);
						} else {
							this.logger.debug("copyImages() > Copied image" + oldUrl + " > " + newUrl);
						}
					});
				} else {
					this.logger.error("copyImages() > Could save image in mongo"); 
				}			
			});
		}

		if(newApp.containsKey("screens")){
			JsonObject screens = newApp.getJsonObject("screens");
			updateBackgroundImageUrls(replacements, screens);
		}
		if(newApp.containsKey("widgets")){
			JsonObject widgets = newApp.getJsonObject("widgets");
			updateBackgroundImageUrls(replacements, widgets);
		}	
		if(newApp.containsKey("templates")){
			JsonObject templates = newApp.getJsonObject("templates");
			updateBackgroundImageUrls(replacements, templates);
		}
	
		JsonObject update = new JsonObject().put("$set", newApp);
		mongo.updateCollection(table, App.findById(newID), update, updateHandler ->{
			
			if(updateHandler.succeeded()){				
				returnJson(event, new JsonObject().put("id", newID));
				AppEvent.send(event, getUser(event).getEmail(), AppEvent.TYPE_APP_COPY, newID);			
			} else {
				error("copyImage", "Could not save updateed app");
				returnError(event, "app.copy.error");
				updateHandler.cause().printStackTrace();
			}
		});
	
		

	}

	
	private void updateBackgroundImageUrls(Map<String, String> replacements, JsonObject boxes) {
		Set<String> ids = boxes.fieldNames();
		for(String id : ids){
			JsonObject box = boxes.getJsonObject(id);
			if(box.containsKey("style")){
				JsonObject style = box.getJsonObject("style");
				if(style.containsKey("backgroundImage")){
					JsonObject backgroundImage = style.getJsonObject("backgroundImage");
					if(backgroundImage!= null && backgroundImage.containsKey("url")){
						String url = backgroundImage.getString("url");
						if(replacements.containsKey(url)){
							String newURL = replacements.get(url);
							backgroundImage.put("url", newURL);
						} else {
							this.logger.error("copyImages() > no replacement for image " + box.encode()); 
						}
					} else {
						this.logger.debug("copyImages() > Background is null " + box.encode());
					}
				}
			}
		}
	}
	
	

	/********************************************************************************************
	 * findByUser
	 ********************************************************************************************/

	
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
		 * Join over team and app table :-(
		 */
		long start = System.currentTimeMillis();
		String paging = event.request().getParam("paging");
		
		logger.info("findByUser() > enter > paging : " + paging);
		mongo.find(team_db, Team.findByUser(getUser(event)), res ->{
			
			if(res.succeeded()){				
				JsonArray appIDs = new JsonArray();				
				List<JsonObject> acls = res.result();
				for(JsonObject acl : acls){
					if(acl.containsKey(Team.APP_ID) && acl.getString(Team.APP_ID) != null) {
						appIDs.add(acl.getString(Team.APP_ID));
					}
				}								
				
				long end = System.currentTimeMillis();
				logger.info("findByUser() > exit > team_db: " + (end - start) );
				this.logMetric(this.getClass(), "findByUser[teamdb]", (end - start));
				
				if ("true".equals(paging)) {
					pageByUser(event,  appIDs);	
				} else {
					findByUser(event,  appIDs);		
				}
						
			} else {
				logger.error("findByUser() > Mongo Error " + res.cause().getMessage()); 
				returnError(event, 404);
			}
			
		});
		
		
		
	}

	private void findByUser(RoutingContext event,JsonArray appIDs) {
		
		String summary = event.request().getParam("summary");
		long start = System.currentTimeMillis();
		if("true".equals(summary)){
			
			/**
			 * Just return the summary in here
			 */
			FindOptions options = new FindOptions().setFields(App.summaryFields());
				
			mongo.findWithOptions(table, App.findByIDS(appIDs),options, appRes->{
			
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
							app = this.preview.create(app);
							app = cleanJson(app);
							result.add(app);
						} else {
							logger.info("findByUser() > deleted app " + app);
						}
					}
					
					event.response().end(result.encode());
					
					long end = System.currentTimeMillis();
					this.logMetric(this.getClass(), "findByUser[summary]", (dbDone - start), result.size());
					this.logMetric(this.getClass(), "findByUser[sum_preview]", (end - dbDone), result.size());
					
				} else {
					logger.error("findByUser() > Mongo Error : " + appRes.cause().getMessage());
					returnError(event, 404);
				}
			});
		} else {
			mongo.find(table, App.findByIDS(appIDs), appRes->{
				
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
							app = this.preview.create(app);
							app = cleanJson(app);
							result.add(app);
						} else {
							logger.info("findByUser() > deleted app " + app);
						}
					}
					event.response().end(result.encode());
					
					long end = System.currentTimeMillis();
					this.logMetric(this.getClass(), "findByUser[apps]", (dbDone - start), result.size());
					this.logMetric(this.getClass(), "findByUser[preview]", (end - dbDone), result.size());
				} else {
					logger.error("findByUser() > Mongo Error : " + appRes.cause().getMessage());
					returnError(event, 404);
				}
			});
		}
		
		
	}
	

	private void pageByUser(RoutingContext event,JsonArray appIDs) {
		final long start = System.currentTimeMillis();
		mongo.find(table, App.findByIDS(appIDs), appRes->{
			
			if(appRes.succeeded()){		
				long dbDone = System.currentTimeMillis();
				
				
				List<JsonObject> apps = appRes.result();
				JsonArray list = new JsonArray();
				for(JsonObject app : apps){
					/**
					 * Sometimes the app might be marked for deletion, but it is still not deleted!
					 */
					if (!App.isDeleted(app)){
						/**
						 * Filter out all not needed widgets etc to speed up loading
						 */
						app = this.preview.create(app);
						app = cleanJson(app);
						list.add(app);
					}
				}
				
				JsonObject result = new JsonObject()
						.put("rows", list)
						.put("size", apps.size())
						.put("offset", 0);
				
				
				long end = System.currentTimeMillis();
				logger.info("pageByUser() > exit > app_db: " + (dbDone - start)  + "ms > preview : " + ( end - dbDone) + "ms");
			
				event.response().end(result.encode());
				
			} else {
				logger.error("pageByUser() > Mongo Error : " + appRes.cause().getMessage());
				returnError(event, 404);
			}
		});
	}

	
	/********************************************************************************************
	 * findPublic
	 ********************************************************************************************/

	public Handler<RoutingContext> findPublic() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				findPublic(event);
			}
		};
	}

	
	private void findPublic(RoutingContext event) {
		logger.debug("findPublic() > exit");
		
		long start = System.currentTimeMillis();
		mongo.find(table, App.findPublic(), res->{
			
			if(res.succeeded()){
				
				long dbDone = System.currentTimeMillis();
				this.logMetric(this.getClass(), "findPublic[apps]", (dbDone-start));
				
				List<JsonObject> apps = res.result();
				JsonArray result = new JsonArray();
				for(JsonObject app : apps){
					/**
					 * Filter out all not needed widgets etc to speed up loading
					 */
					app = this.preview.create(app);
					app = cleanJson(app);
					result.add(app);
				}
				
				long end = System.currentTimeMillis();
				this.logMetric(this.getClass(), "findPublic[preview]", (end - dbDone));
				
				event.response().end(result.encode());
				
			} else {
				returnError(event, 404);
			}
		});
		
	}
	
	
	/********************************************************************************************
	 * create
	 ********************************************************************************************/
	
	protected void beforeCreate(RoutingContext event, JsonObject json){		

	}
	

	protected void afterCreate(RoutingContext event, JsonObject app) {
		logger.info("afterCreate() > enter " + app.getString("_id"));
		
		JsonObject owner = Team.create(getUser(event), app.getString("_id"), Acl.OWNER);
		mongo.save(team_db, owner, ownerCreated ->{
			if(ownerCreated.succeeded()){
				logger.debug("afterCreate() > Owner added > ");
			} else {
				logger.error("afterCreate() Could not add owner"); 
			}
		});
		
		String appID = app.getString("_id");
		addInvitation(appID,Invitation.TEST );
		addInvitation(appID,Invitation.READ );
		addInvitation(appID,Invitation.WRITE );
		AppEvent.send(event, getUser(event).getEmail(), AppEvent.TYPE_APP_CREATE, app.getString("_id"));	
	}
	
	private void addInvitation(String id, int r) {
		this.addInvitation(id, r, null);
	}

	private void addInvitation(String id, int r, Handler<Boolean> handler) {
		JsonObject inv = Invitation.create(Util.getRandomString(), id, r);
		mongo.save(inv_db, inv, invTestCreated ->{
			if(invTestCreated.succeeded()){
				logger.debug("afterCreate() > Invitation added ");
			} else {
				logger.error("afterCreate() Could not add invitation "+ r); 
			}
			if (handler != null) {
				handler.handle(invTestCreated.succeeded());
			}
		});
	}
	
	/********************************************************************************************
	 * resetHash
	 ********************************************************************************************/

	public void resetToken(RoutingContext event) {
		String appID = getId(event, "appID");
		logger.info("resetToken() > enter " +appID);
		/**
		 * People that can write the app can also ask 
		 */
		this.acl.canWrite(getUser(event), event , allowed -> {
			if (allowed) {
				
				/**
				 * Remove old invitations
				 */
				mongo.removeDocuments(inv_db, Invitation.findByApp(appID), res -> {
					if (res.succeeded()) {
						/**
						 * Add test
						 */
						addInvitation(appID,Invitation.TEST, testCreated -> {
							if (testCreated.booleanValue()) {
								/**
								 * Add read
								 */
								addInvitation(appID,Invitation.READ, readCreated -> {
									if (readCreated.booleanValue()) {
										/**
										 * Add write
										 */
										addInvitation(appID,Invitation.WRITE, writeCreated -> {
											if (writeCreated.booleanValue()) {
												returnOk(event, "app.token.reset");
												AppEvent.send(event, getUser(event).getEmail(), AppEvent.TYPE_APP_INVITATION_REST, appID);	
											} else {
												error("resetToken", "Could not Create TEST " + res.cause());
												returnError(event, 404);
											}
										});
									} else {
										error("resetToken", "Could not create READ " +  res.cause());
										returnError(event, 404);
									}
								});
							} else {
								error("resetToken", "Could not Create TEST " + res.cause());
								returnError(event, 404);
							}
						});
					} else {
						error("resetToken", "Could not delete inviattions for app "  + appID + res.cause());
						returnError(event, 404);
					}
				});
			} else {
				error("resetToken", "User " + getUser(event).getEmail() + " tried to reset tokens without write");
				returnError(event, 404);
			}
		});
	}
	
	
	
	
	/********************************************************************************************
	 * update, we only send an ok
	 ********************************************************************************************/
	
	protected void beforeUpdate(RoutingContext event, String id, JsonObject json) {
		if(json.containsKey("users")){
			error("beforeUpdate", getUser(event) + " tried to write acl on app " + id);
			json.remove("users");
		}
		if(json.containsKey("invitations")){
			error("beforeUpdate", getUser(event) + " tried to write invitations on app " + id);
			json.remove("invitations");
		}
	}
	
	protected void returnUpdate(RoutingContext event, String id) {
		error("returnUpdate", "Full update is deprecated!");
		returnOk(event, "app.update.succcess");
		/**
		 * Send update on Bus;
		 */
		App.onUpdate(event, id);
	}
	
	/********************************************************************************************
	 * delete
	 ********************************************************************************************/

	public void delete(RoutingContext event, String appID) {
		log("delete", "Delete "+ appID);
		
		/**
		 * First we set a "isDeleted flag" and return the request, Then we delete the document
		 */
		JsonObject isDeleted = new JsonObject().put(App.FIELD_IS_DELETED, true);
		JsonObject update = new JsonObject().put("$set", isDeleted);
		
		mongo.updateCollection(table,  Model.findById(appID), update, resUpdate -> {
			if (resUpdate.succeeded()){
				logger.info("delete() >> Flag (isDeleted) was set on "+ appID +  " ");
				/**
				 * Flag was set. So now we delete the rest async...
				 */
				deleteAppAndParts(event, appID);
				returnOk(event, table + ".delete.success");
			} else {
				log("delete", "Cannot set isDeletedFlag");
				Mail.error(event, "AppREST.delete() > Could set isDeletedFLag");		
				returnError(event, table + ".delete.error");
			}
		});
	
		
	}
	
	public void deleteAppAndParts(RoutingContext event, String appID){
		log("deleteAppAndParts", "Enter "+ appID);
		
		
		/**
		 * Delete the app
		 */
		logger.info("deleteAppAndParts() >> run async "+ appID +  " ");
		
		mongo.removeDocuments(table, Model.findById(appID), res->{
			if(res.succeeded()){
				logger.info("deleteAppAndParts() >> App was deleted "+ appID +  " ");
				/**
				 * Log also the name?
				 */
				AppEvent.send(event, getUser(event).getEmail(), AppEvent.TYPE_APP_DELETE, appID);

				/**
				 * Delete also all parts. 
				 */
				for(String table : part_dbs){
					log("delete", " - Delete parts :" + table);
					mongo.removeDocuments(table, AppPart.findByApp(appID), res2->{
						if(!res.succeeded()){
							error("delete", "Could not empty " + table);
						}
					});
				}
			
			} else {
				log("deleteAppAndParts", "Could no delete app!");
				//Mail.error(event, "AppREST.deleteAppAndParts() > Could no delete app!");	
			}
		});
		
	}
	
	
	
	/********************************************************************************************
	 * Changes
	 ********************************************************************************************/
	
	
	
	public Handler<RoutingContext> applyChanges() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				applyChanges(event);
			}
		};
	}

	
	private void applyChanges(final RoutingContext event) {
		logger.info("applyChanges() > enter");
		
		final String appID = getId(event);
		acl.canWrite(getUser(event), event, allowed ->{
			
			if(allowed){
				String json = event.getBodyAsString();
				
				if(json.startsWith("[") && json.endsWith("]")){
					try{
						JsonArray changes = new JsonArray(json);
						applyChanges(appID, changes, event);
					} catch(Exception e){
						error("applyChanges", "Exception: Could not parse json: "  + json);	
						Mail.error(event, "AppREST.applyChanges() > User " + getUser(event) + " >> Could not parse json: "  + json);
						returnError(event, 405);
					}
				} else {
					error("applyChanges", "No JsonArray passed: "  + json);
					returnError(event, 405);
				}
					
			} else {
				error("applyChanges", "Error: " + getUser(event)+ " tried to write to app " + appID);
				Mail.error(event, "AppREST.applyChanges() > User " + getUser(event) + " tried to write to app " + appID);	
				returnError(event, 401);
			}
		});
		
	}
	
	
	public void applyChanges(String appID, JsonArray changes, RoutingContext event) {
	
		/**
		 * Here we do magic. We convert the JavaScript DeltaObject to a Mongo update!
		 */
		JsonObject update = util.changeToMongo(changes);
		
		if(update.isEmpty()){
			 returnError(event, "app.update.error.no.data");
			 return;
		}
		
		mongo.updateCollection(table, Model.findById(appID), update, res -> {
			if(res.succeeded()){
				returnOk(event, "app.changes.succcess");
			} else {
				log("applyChanges", res.cause().getMessage());
				Mail.error(event, "AppREST.applyChanges() > User " + getUser(event) + " >> Mongo Error " + res.cause().getMessage() + " >> " + update.encodePrettily());
				returnError(event, "app.update.error");
			}
		});
		
		/**
		 * Send update on Bus;
		 */
		App.onUpdate(event, appID);
	}
	
	

	/********************************************************************************************
	 * Changes
	 ********************************************************************************************/

	public Handler<RoutingContext> findEmbedded() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				findEmbedded(event);
			}
		};
	}
	
	private void findEmbedded(RoutingContext event) {
		String id  = getId(event);
		this.acl.canRead(getUser(event), event, allowed -> {
			if (allowed) {
				
				mongo.findOne(table, App.findById(id), App.summaryFields(), res ->{
					if (res.succeeded()){
						JsonObject json = res.result();
						if (json != null){
							returnJson(event, json);
						} else {
							error("findEmbedded", "Could not find " + id);
							returnError(event, 401);
						}
					} else {
						error("findEmbedded", "Could not find " + id);
						returnError(event, 401);
					}
				});
				
			} else {
				error("findEmbedded", "User " + getUser(event) + " tried to  read " + id);
				Mail.error(event, "AppREST.findEmbedded() > User "+ getUser(event)+ " tried to read without permission");		
				returnError(event, 401);
			}
		});
		
	}
	

	/********************************************************************************************
	 * Changes
	 ********************************************************************************************/
	

	
	protected JsonObject cleanJson(JsonObject json){
		json.remove("users");
		json.remove("invitations");
		return super.cleanJson(json);
	}
	

}
