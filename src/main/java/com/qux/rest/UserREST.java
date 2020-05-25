package com.qux.rest;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.qux.MATC;
import com.qux.acl.UserAcl;
import com.qux.bus.MailHandler;
import com.qux.model.AppEvent;
import com.qux.model.Team;
import com.qux.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.util.DB;
import com.qux.util.Mail;
import com.qux.util.MongoREST;
import com.qux.util.TokenService;
import com.qux.util.Util;
import com.qux.validation.UserValidator;
import io.vertx.core.Handler;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

public class UserREST extends MongoREST {

	private final String imageFolder;
	
	private final String team_db;
	
	private final long maxUsers;
	
	private long imageSize = 1024 * 1024;
	
	private Logger logger = LoggerFactory.getLogger(UserREST.class);

	public UserREST(MongoClient db, JsonObject conf) {
		super(db, User.class);
		this.imageFolder = conf.getString("image.folder.user");
		this.imageSize = conf.getLong("image.size");
		this.maxUsers = conf.getLong("max.users");
		this.team_db = DB.getTable(Team.class);
		setACL(new UserAcl(db, this.maxUsers));
		setValidator(new UserValidator(db));
		setPartialUpdate(true);
	}

	public Handler<RoutingContext> current() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				current(event);
			}
		};
	}

	public void current(RoutingContext event) {
		logger.info("current() > enter");
		User user = getUser(event);
		JsonObject json = mapper.toVertx(user);
		event.response().end(cleanJson(json).encode());
	}

	
	public Handler<RoutingContext> login() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				login(event);
			}
		};
	}
	
	protected void login(RoutingContext event) {
		
		JsonObject login = event.getBodyAsJson();
		
		
		/**
		 * make sure data is there
		 */
		if(login.containsKey("email") && login.containsKey("password")){
			this.mongo.findOne(this.table, User.findByEmail(login.getString("email")), null, res -> {
				
				if(res.succeeded()){
					
					JsonObject user = res.result();
					
					if(user!=null){
						/**
						 * The user might have retired
						 */
						if (User.STATUS_RETIRED.equals(user.getString("status"))){
							/**
							 * We log anon as he want to be left alone...
							 */
							error("login", "Retired user tried login");
							returnError(event, "user.login.fail");
						} else {
							
							/**
							 * Now check password!
							 */
							checkPassword(event, login, user);
						}
					} else {
						error("login", "No user with mail :"+ login.getString("email"));
						returnError(event, "user.login.fail");
					}
				} else {
					returnError(event, "user.login.fail");
				}
			});
		} else {
			returnError(event, "user.login.fail");
		}
	}



	public void checkPassword(RoutingContext event, JsonObject login, JsonObject user) {
		if(Util.matchPassword(login.getString("password"), user.getString("password"))){
			setLoginUser(event, user);
		} else {
			updateFailedLogins(user);
			AppEvent.send(event, user.getString("email"), AppEvent.TYPE_USER_LOGIN_ERROR);
			logger.error("error() > *ATTENTION* Wrong login for "+ user.getString("email"));
			returnError(event, "user.login.fail");
		}
	}


	/**
	 * Set user in session and update Mongo KPIs.
	 */
	public void setLoginUser(RoutingContext event, JsonObject user) {
		/**
		 * Every thing fine. We can set the user in the session
		 */
		setUser(mapper.fromVertx(user, User.class), event );
		
		/**
		 * We update user data...
		 */
		int loginCount = 0;
		if(user.containsKey("loginCount")){
			loginCount = user.getInteger("loginCount");
		}
		loginCount++;
		user.put("loginCount", loginCount);							
		user.put("lastlogin", System.currentTimeMillis());
		
		mongo.save(table, user, write ->{
			if(!write.succeeded()){
				logger.error("login() > Could not update lastLogin");
			}
		});
		
		/**
		 * Log for KPI
		 */
		AppEvent.send(event, user.getString("email"), AppEvent.TYPE_USER_LOGIN);
		
		
		String token = TokenService.getToken(user);
		user.put("token", token);
		
		/**
		 * Copy data here, other wise password  and so might be rewritten in the clean and
		 * then in the async save the password is gone
		 */
		event.response().end(cleanJson(user.copy()).encode());
	}



	private void updateFailedLogins(JsonObject user) {
		if(!user.containsKey("failedLoginAttempts")){
			user.put("failedLoginAttempts",0);
		} 							
		user.put("failedLoginAttempts",user.getInteger("failedLoginAttempts")+1);							
		mongo.save(table, user, write ->{
			if(!write.succeeded()){
				logger.error("login() > Could not store failedLoginAttempts");
			}
		});
	}
	
	public Handler<RoutingContext> logout() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				logout(event);
			}
		};
	}
	
	
	
	public void logout(RoutingContext event) {
		/**
		 * Store for KPI
		 */
		User user = getUser(event);
		AppEvent.send(event, user.getEmail(), AppEvent.TYPE_USER_LOGOUT);

		returnOk(event, "user.logged.out");
	}
	
	
	
	/********************************************************************************************
	 * Delete
	 ********************************************************************************************/

	public void afterDelete(RoutingContext event, String id) {
		logger.info("afterDelete() > " + id);
		
		JsonObject query =  new JsonObject().put(Team.USER_ID, id);
		
		mongo.removeDocuments(this.team_db, query, res ->{
			if(res.succeeded()){
				logger.error("Removed entries in team db");
			} else {
				logger.error("Could not clean up team db");
			}
		});
	}
	
	
	/********************************************************************************************
	 * Create
	 ********************************************************************************************/

	
	protected void create(RoutingContext event, JsonObject json) {

		
		json.put("created", System.currentTimeMillis());
		json.put("lastUpdate", System.currentTimeMillis());
		
		/**
		 * Set payed until ...
		 */
		Calendar cal = Calendar.getInstance();

		json.put("email", json.getString("email").toLowerCase());
		json.put("password", Util.hashPassword(json.getString("password")));
		json.put("role", User.USER);
		json.put("plan", "free");
		json.put("newsletter", false);
		json.put("lastNotification", 0);
		json.put("acceptedTOS", System.currentTimeMillis());
		json.put("acceptedPrivacy", System.currentTimeMillis());
		json.put("acceptedGDPR", true);
		
		mongo.insert(this.table, json, res -> {
			if (res.succeeded()) {		
				
				json.put("_id", res.result());
				logger.info("create() > User " + json.encode());
				cleanJson(json);
				event.response().end(json.encode());
						
				/**
				 * Also send email
				 */
				Mail.to(json.getString("email"))
					.bcc(MATC.ADMIN)
					.subject("Welcome to Quant-UX.com")
					.payload(new JsonObject())
					.template(MailHandler.TEMPLATE_USER_CREATED)
					.send(event);
				
				/**
				 * Log KPI
				 */
				AppEvent.send(event, json.getString("email"), AppEvent.TYPE_USER_SIGNUP);
				
			} else {
				returnError(event, table + ".create.error");
			}
		});

	}
	

	
	
	/********************************************************************************************
	 * Update
	 ********************************************************************************************/

	/**
	 * The acl was already validated. We just has the password and make sure 
	 * none is setting the role, plan or so...
	 */
	public void update(RoutingContext event, String id, JsonObject json) {

		if(json.containsKey("password")){
			json.put("password", Util.hashPassword(json.getString("password")));
		}
		
		if(json.containsKey("role")){
			logger.error("update() > User " + getUser(event) + " tried to set role!");
			Mail.error(event, "UserRest.update() " + getUser(event)  + " tried to set role to " + json.getString("role"));
			json.remove("role");
		} 
		
		if(json.containsKey("paidUntil")){
			logger.error("update() > User " + getUser(event) + " tried to set paidUntil!");
			Mail.error(event, "UserRest.update() " + getUser(event)  + " tried to set paidUntil");
			json.remove("paidUntil");
		} 
		
		if(json.containsKey("domain")){
			logger.error("update() > User " + getUser(event) + " tried to set domain!");
			json.remove("domain");
		} 
		
		if(json.containsKey("status")){
			logger.error("update() > User " + getUser(event) + " tried to set status!");
			json.remove("status");
		} 
		
		if(json.containsKey("plan")){
			logger.error("update() > User " + getUser(event) + " tried to set plan!");
			json.remove("plan");
		} 
		
		if(json.containsKey("has")){
			logger.error("update() > User " + getUser(event) + " tried to set has!");
			json.remove("has");
		} 
		
		/**
		 * update the user now! Set user afterwards in "afterUpdate" method.
		 */
		super.update(event, id, json);
	}
	
	protected void afterUpdate(RoutingContext event, String id, JsonObject json) {
	
		setUser(mapper.fromVertx(json, User.class), event );
	}

	
	/********************************************************************************************
	 * Set Image
	 ********************************************************************************************/

	
	
	public Handler<RoutingContext> setImage() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				setImage(event);
			}
		};
	}

	public void setImage(RoutingContext event) {
		
		String id  = event.request().getParam("id");
		if (this.acl != null) {
			this.acl.canWrite(getUser(event), event, allowed -> {
				if (allowed) {
					setImage(event, id);
				} else {					
					returnError(event, 405);
				}
			});
		} else {
			this.setImage(event, id);
		}
		
	
	}
	
	public void setImage(RoutingContext event, String id) {
		
		List<FileUpload> files = new ArrayList<FileUpload>(event.fileUploads());
		if(files.size() == 1){
		
			FileUpload file = files.get(0);
				
			setImage(event, id, file);
			
		} else {
			
			/**
			 * delete all useless files
			 */
			FileSystem fs = event.vertx().fileSystem();
			for(FileUpload file : files){
				fs.delete(file.uploadedFileName(), res->{
					
				});
			}
			returnError(event, 404);
		}
		
	}



	private void setImage(RoutingContext event, String id, FileUpload file) {
		
		if(checkImage(file)){
			FileSystem fs = event.vertx().fileSystem();
			
			String userFolder = imageFolder +"/" + id ;
			
			fs.mkdirs(userFolder, res -> {
				
				String imageID = System.currentTimeMillis() +"";
				String type = Util.getFileType(file.fileName());
				String image = imageID + "." + type;
				String dest = userFolder +"/" +  image;

				fs.move(file.uploadedFileName(),dest , res2->{
					
					if(res2.succeeded()){
						/**
						 * now update object in db!
						 */
						onUserImageUploaded(event, id, image);
						
					} else {
						/**
						 * FIXME: Do we have to remove the upload??
						 */
						returnError(event, "user.image.error2");
					}
				});
			});
			
		
		} else {
			FileSystem fs = event.vertx().fileSystem();
			fs.delete(file.uploadedFileName(),res->{
				if(!res.succeeded()){
					error("setImage", "Could not delete " + file.uploadedFileName());
				}
			});
			returnError(event, "user.image.wrong");
		}
	}



	private void onUserImageUploaded(RoutingContext event, String id, String image) {
	
		/**
		 * Update session!
		 */
		User user = getUser(event);
		user.setImage(image);
		setUser(user, event);
		
		/**
		 * build a new user json and pass it to the update method,
		 * which will alson send the response;
		 */
		JsonObject json = new JsonObject().put("image", image );
		update(event, id, json );
		
		
	}

	private boolean checkImage(FileUpload file) {
		return file.size() < this.imageSize;
	}
	
	

	public Handler<RoutingContext> deleteImage() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				deleteImage(event);
			}
		};
	}
	

	public void deleteImage(RoutingContext event) {
		
		String id  = event.request().getParam("id");
		String image = event.request().getParam("image");
		
		if (this.acl != null) {
			this.acl.canWrite(getUser(event), event, allowed -> {
				
				if(allowed){
					this.mongo.findOne(table, User.findById(id),null, res->{
						if(res.succeeded() && res.result() != null){
							
							User user =getUser(event);
							user.setImage(null);
							setUser(user, event);
							
							
							JsonObject json = res.result();
							json.remove("image");
							
						
							JsonObject query = new JsonObject()
							.put("$unset", new JsonObject().put("image", ""));
							
							mongo.updateCollection(table, User.findById(id), query, updated ->{
								if(updated.succeeded()){
									returnJson(event, cleanJson(json));
								} else {
									returnError(event, 405);
									error("deleteImage", "Could not update user");
								}
							});
							
							
							/**
							 * Delete the image async
							 */
							String file = imageFolder  +"/" + id + "/" + image ;
							FileSystem fs = event.vertx().fileSystem();
							System.out.println("Delete user image "+ file);
							fs.exists(file, exists->{
								if(exists.succeeded() && exists.result()){
									fs.delete(file, deleted ->{
										if(!deleted.succeeded()){
											error("deleteImage", "Could not delete image "+ file);
										} else {
											System.out.println("Deleted " + file);
										}
									});
								}
							});
	
						} else {
							error("deleteImage", "Could not load user" );
							returnError(event, 405);
						}
					});
				} else {
					error("deleteImage", "The user "+ getUser(event) + " tried to delete an image " );
					returnError(event, 405);
				}
				
			
			});
		} else {
			getImage(event, id, image);
		}
	}

	
	/********************************************************************************************
	 * Get Image
	 ********************************************************************************************/


	public Handler<RoutingContext> getImage() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				getImage(event);
			}
		};
	}
	

	public void getImage(RoutingContext event) {
		
		String id  = event.request().getParam("id");
		String image = event.request().getParam("image");
		
		if (this.acl != null) {
			this.acl.canRead(getUser(event), event, allowed -> {
				if (allowed) {
					getImage(event, id, image);
				} else {
					returnError(event, 404);
				}
			});
		} else {
			getImage(event, id, image);
		}
	}
	
	
	
	public void getImage(RoutingContext event, String userID, String image) {
		
		String file = imageFolder  +"/" + userID + "/" + image ;
		event.response().putHeader("Cache-Control", "no-transform,public,max-age=86400,s-maxage=86401");
		event.response().putHeader("ETag", userID+"/"+image);
		event.response().sendFile(file);
	
	}
	
	
	
	public Handler<RoutingContext> retire() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				retireUser(event);
			}
		};
	}
	
	
	/********************************************************************************************
	 * retire user
	 ********************************************************************************************/

	public void retireUser(RoutingContext event) {
		logger.info("retireUser() > enter ");
		
		User user = getUser(event);
		if (!user.isGuest()){
			
			JsonObject request = new JsonObject()
					.put("status", User.STATUS_RETIRED);
			
			JsonObject update = new JsonObject()
					.put("$set", request);
			
			mongo.updateCollection(table, User.findById(user.getId()),update , res ->{
				if(res.succeeded()){
					AppEvent.send(event, user.getEmail(), AppEvent.TYPE_USER_RETIRED);
				} else {
					logger.error("retireUser() > could not save mongo", res.cause());
				}
			});
			
			this.logout(event);
		} else {
			error("retireUser", "The user "+ getUser(event) + " tried to retire");
			returnError(event, 405);
		}
	
	}
	
	

	/********************************************************************************************
	 * Update
	 ********************************************************************************************/

	public void updateNotificationView(RoutingContext event){
		User user = this.getUser(event);
		logger.debug("updateNotificationView() > enter > " + user); 
		if (!user.isGuest()){
			JsonObject update =  new JsonObject().put("lastNotification", System.currentTimeMillis());
			particalUpdate(event, getUser(event).getId(), update);
		} else {
			logger.debug("updateNotificationView() > Called for guest..."); 
			returnOk(event, "user.notificaiton.update");
		}
	}
	
	public void getNotifcationView(RoutingContext event){
		User user = this.getUser(event);
		logger.debug("updateNotificationView() > enter > " + user); 
		if (!user.isGuest()){
			mongo.findOne(table, User.findById(user.getId()),null, res -> {
				JsonObject result = new JsonObject();
				result.put("lastNotification", 0);
				if (res.succeeded()){
					JsonObject json = res.result();
					if (json.containsKey("lastNotification")){
						result.put("lastNotification", json.getLong("lastNotification"));
					} 
				} 
				returnJson(event, result);
			});
		} else {
			JsonObject result = new JsonObject();
			result.put("lastNotification", 0);
			returnJson(event, result);
		}
	}
	
	/********************************************************************************************
	 * GDPR
	 ********************************************************************************************/

	
	public void updatePrivacy(RoutingContext event){
		User user = this.getUser(event);
		logger.debug("updatePrivacy() > enter > " + user); 
		if (!user.isGuest()){
			JsonObject update =  new JsonObject().put("acceptedGDPR", true);
			particalUpdate(event, getUser(event).getId(), update);
			
			AppEvent.send(event, user.getEmail(), AppEvent.TYPE_USER_UDPATE_PRIVACY);
			
		} else {
			logger.debug("updateNotificationView() > Called for guest..."); 
			returnOk(event, "user.notificaiton.update");
		}
	}
	

	/********************************************************************************************
	 * Helper
	 ********************************************************************************************/
	
	protected JsonObject cleanJson(JsonObject user){
		user.remove("password");
		return super.cleanJson(user);
	}

	


}
