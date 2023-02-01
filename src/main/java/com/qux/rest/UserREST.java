package com.qux.rest;

import java.util.ArrayList;
import java.util.List;

import com.qux.MATC;
import com.qux.acl.UserAcl;
import com.qux.auth.ITokenService;
import com.qux.blob.IBlobService;
import com.qux.bus.MailHandler;
import com.qux.model.AppEvent;
import com.qux.model.Team;
import com.qux.model.User;
import com.qux.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.util.DB;
import com.qux.util.Mail;
import com.qux.util.rest.MongoREST;
import com.qux.util.Util;
import com.qux.validation.UserValidator;
import io.vertx.core.Handler;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

public class UserREST extends MongoREST {

	//private final String imageFolder;
	
	private final String team_db;

	private long imageSize = 1024 * 1024;
	
	private final Logger logger = LoggerFactory.getLogger(UserREST.class);

	private final IBlobService blobService;

	private boolean allowSignUp = true;

	private boolean hasCustomDomains = false;

	private String[] allowedDomains = new String[0];

	public UserREST(ITokenService tokenService, IBlobService blobService, MongoClient db, JsonObject conf) {
		super(tokenService, db, User.class);
		this.blobService = blobService;
		this.imageSize = conf.getLong("image.size");
		this.team_db = DB.getTable(Team.class);
		this.initConfig(conf);
		setACL(new UserAcl(db));
		setValidator(new UserValidator(db));
		setPartialUpdate(true);
	}

	private void initConfig(JsonObject conf) {
		this.allowSignUp = Config.getUserSignUpAllowed(conf);
		if (!this.allowSignUp) {
			logger.error("initConfig() > No signups allowed");
		}

		String domains = Config.getUserAllowedDomains(conf);
		if (!"*".equals(domains)) {
			this.hasCustomDomains = true;
			this.allowedDomains = domains.split(",");
			logger.error("initConfig() > Limit domains to: " + domains);
		}
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
		this.logger.info("login() > enter");
		JsonObject login = event.getBodyAsJson();
		if(login.containsKey("email") && login.containsKey("password")){
			this.mongo.findOne(this.table, User.findByEmail(login.getString("email")), null, res -> {
				if (res.succeeded()){
					JsonObject user = res.result();
					if (user != null){
						if (User.STATUS_RETIRED.equals(user.getString("status"))){
							error("login", "Retired user tried login");
							returnError(event, "user.login.fail");
						} else {
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


	public void setLoginUser(RoutingContext event, JsonObject user) {

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
		AppEvent.send(event, user.getString("email"), AppEvent.TYPE_USER_LOGIN);
		String token = this.getTokenService().getToken(user);
		user.put("token", token);
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
		User user = getUser(event);
		AppEvent.send(event, user.getEmail(), AppEvent.TYPE_USER_LOGOUT);
		returnOk(event, "user.logged.out");
	}

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

	public void createExternalIfNotExists(RoutingContext event) {
		logger.info("createExternalIfNotExists() > ");

		JsonObject json = event.getBodyAsJson();
		if (!json.containsKey("id")) {
			logger.error("createExternalIfNotExists() > No id");
			returnError(event, 400);
		}
		if (!json.containsKey("email")) {
			logger.error("createExternalIfNotExists() > No email");
			returnError(event, 400);
		}
		if (!json.containsKey("name")) {
			logger.error("createExternalIfNotExists() > No name");
			returnError(event, 400);
		}

		String id = json.getString("id");
		this.mongo.findOne(this.table, User.findById(id), null, res -> {
			if (res.succeeded()) {
				JsonObject result = res.result();
				if (result != null) {
					logger.info("createExternalIfNotExists() > Found user");
					result.put("id", id);
					result.remove("_id");
					cleanJson(result);
					returnJson(event, result);
				} else {
					logger.info("createExternalIfNotExists() > Create user");
					insertExternal(event, json, id);
				}
			} else {
				returnError(event, 401);
			}
		});
	}

	private void insertExternal(RoutingContext event, JsonObject json, String id) {
		logger.info("insertExternal() > Create user : " + id);

		json.remove("id");
		json.put("_id", id);

		json.put("external", true);
		json.put("created", System.currentTimeMillis());
		json.put("lastUpdate", System.currentTimeMillis());
		json.put("email", json.getString("email").toLowerCase());
		json.put("external", true);
		json.put("role", User.USER);
		json.put("plan", "Free");
		json.put("newsletter", false);
		json.put("lastNotification", 0);
		json.put("password", Util.getRandomString());
		json.put("acceptedTOS", System.currentTimeMillis());
		json.put("acceptedPrivacy", System.currentTimeMillis());
		json.put("acceptedGDPR", true);

		this.mongo.insert(this.table, json, res -> {

			if (res.succeeded()) {
				this.logger.error("insertExternal() > Created user");

				json.put("id", id);
				cleanJson(json);
				returnJson(event, json);

				Mail.to(json.getString("email"))
						.subject("Welcome to Quant-UX")
						.payload(new JsonObject())
						.template(MailHandler.TEMPLATE_USER_CREATED)
						.send(event);

			} else {
				this.logger.error("insertExternal() > could not save user", res.cause());
				returnError(event, 401);
			}
		});
	}

	protected void create(RoutingContext event, JsonObject json) {

		if (!this.allowSignUp) {
			logger.error("create() > User tried to signup although not allowed");
			returnError(event,  "user.create.nosignup");
			return;
		}

		if (this.hasCustomDomains && this.allowedDomains.length > 0) {
			String email = json.getString("email").toLowerCase();
			if(!checkAllowedDomains(email)) {
				logger.error("create() > Wrong domain", email);
				returnError(event,  "user.create.domain");
				return;
			}
		}

		json.put("created", System.currentTimeMillis());
		json.put("lastUpdate", System.currentTimeMillis());
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

				Mail.to(json.getString("email"))
					.subject("Welcome to Quant-UX")
					.payload(new JsonObject())
					.template(MailHandler.TEMPLATE_USER_CREATED)
					.send(event);

				AppEvent.send(event, json.getString("email"), AppEvent.TYPE_USER_SIGNUP);
				
			} else {
				returnError(event, table + ".create.error");
			}
		});
	}

	private boolean checkAllowedDomains(String email) {
		logger.info("checkAllowedDomains() > check ", email);
		String[] parts = email.split("@");
		if (parts.length == 2) {
			String customDomain = parts[1];
			for (String domain : this.allowedDomains) {
				if (customDomain.endsWith(domain)) {
					return true;
				}
			}
		}
		return false;
	}


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

		super.update(event, id, json);
	}
	
	protected void afterUpdate(RoutingContext event, String id, JsonObject json) {
	}


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
			FileSystem fs = event.vertx().fileSystem();
			for(FileUpload file : files){
				fs.delete(file.uploadedFileName(), res->{
					
				});
			}
			returnError(event, 404);
		}
	}



	private void setImage(RoutingContext event, String id, FileUpload file) {
		if (checkImage(file)){
			String userFolder = this.blobService.createFolder(event, id);
			String imageID = System.currentTimeMillis() +"";
			String type = Util.getFileType(file.fileName());
			String image = imageID + "." + type;
			String dest = userFolder +"/" +  image;
			this.blobService.setBlob(event, file.uploadedFileName(), dest, uploadResult -> {
				if (uploadResult) {
					onUserImageUploaded(event, id, image);
				} else {
					returnError(event, "user.image.error2");
				}
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
		User user = getUser(event);
		user.setImage(image);
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
				if (allowed){
					this.mongo.findOne(table, User.findById(id),null, res->{
						if(res.succeeded() && res.result() != null){
							
							User user =getUser(event);
							user.setImage(null);

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
							this.blobService.deleteFile(event, id, image, deleteResult -> {
								if (!deleteResult) {
									error("deleteImage", "Could not delete image "+ image);
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
		this.blobService.getBlob(event, userID, image);
	}

	public Handler<RoutingContext> retire() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				retireUser(event);
			}
		};
	}

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
	
	public void getNotificationView(RoutingContext event){
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

	public void updatePrivacy(RoutingContext event){
		User user = this.getUser(event);
		logger.debug("updatePrivacy() > enter > " + user); 
		if (!user.isGuest()){
			JsonObject update =  new JsonObject().put("acceptedGDPR", true);
			particalUpdate(event, getUser(event).getId(), update);
			AppEvent.send(event, user.getEmail(), AppEvent.TYPE_USER_UPDATE_PRIVACY);
		} else {
			logger.debug("updateNotificationView() > Called for guest..."); 
			returnOk(event, "user.notificaiton.update");
		}
	}
	
	protected JsonObject cleanJson(JsonObject user){
		user.remove("password");
		return super.cleanJson(user);
	}

}
