package com.qux;

import java.time.LocalDateTime;

import com.qux.auth.ITokenService;
import com.qux.auth.KeyCloakTokenService;
import com.qux.blob.FileSystemService;
import com.qux.blob.IBlobService;
import com.qux.blob.S3BlobService;
import com.qux.util.*;
import com.qux.auth.QUXTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.qux.acl.AppAcl;
import com.qux.acl.InvitationCommentACL;
import com.qux.bus.MailHandler;


import com.qux.bus.VMHeater;
import com.qux.model.Annotation;
import com.qux.model.TestSetting;
import com.qux.rest.AppPartREST;
import com.qux.rest.AppREST;
import com.qux.rest.CommandStackREST;
import com.qux.rest.CommentREST;
import com.qux.rest.ContactRest;
import com.qux.rest.EventRest;

import com.qux.rest.ImageREST;
import com.qux.rest.InvitationREST;
import com.qux.rest.LibraryRest;
import com.qux.rest.LibraryTeamRest;
import com.qux.rest.MouseRest;
import com.qux.rest.NotificationREST;
import com.qux.rest.PasswordRest;

import com.qux.rest.TeamREST;
import com.qux.rest.TestSettingsRest;
import com.qux.rest.UserREST;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.StartTLSOptions;

import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;


public class MATC extends AbstractVerticle {
	
	public static final String VERSION = "4.0.93";

	private MongoClient client;
	
	private MailClient mail;

	private final Logger logger = LoggerFactory.getLogger(MATC.class);
	
	private HttpServer server;
	
	private boolean isDebug = false;

	private final String startedTime = LocalDateTime.now().toString();


	public static String MAIl_USER = "";

	private ITokenService tokenService;
	
	@Override
	public void start() {
		this.logger.info("start() > enter");


		JsonObject config = initConfig();
		initMongo(config);

		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create().setMergeFormAttributes(false));
		router.route().handler(CorsHandler.create("*")
				.allowedMethod(HttpMethod.GET)
				.allowedHeader("Access-Control-Request-Method")
				.allowedHeader("Access-Control-Allow-Credentials")
				.allowedHeader("Access-Control-Allow-Origin")
				.allowedHeader("Access-Control-Allow-Headers")
				.allowedHeader("Content-Type"));

		initTokenService(config);
		initMail(router, config);
		initStatus(router);
		initUserRest(config, router);
		initAppRest(router, config);
		initTeamRest(router);
		initCommandRest(router);
		initCommentRest(router);
		initEventRest(router);
		initInvitationRest(router, config);
		initAnnotationRest(router);
		initTestRest(router);
		initNotification(router);
		initLibrary(router);
		initBus();


		HttpServerOptions options = new HttpServerOptions()
			.setCompressionSupported(true);
		
		
		this.server = vertx.createHttpServer(options)
			.requestHandler(router::accept)
			.listen(config.getInteger("http.port"));
		
		
		System.out.println("******************************************");
		System.out.println("* Quant-UX-Server " + VERSION + " launched at " + config.getInteger("http.port") + "");
		System.out.println("******************************************");
	}

	private IBlobService getBlockService(String folder, JsonObject config) {
		logger.info("getConfig() > enter");
		if (Config.isFileSystem(config)) {
			return new FileSystemService(folder);
		} else {
			return new S3BlobService();
		}
	}

	private JsonObject getConfig() {
		logger.info("getConfig() > Enter");
		JsonObject config = this.config();
		JsonObject defaultConfig = Config.setDefaults(config);
		return Config.mergeEnvIntoConfig(defaultConfig);
	}

	private JsonObject initConfig() {
		JsonObject config = this.getConfig();
		if(config.containsKey(Config.DEBUG)){
			this.isDebug = config.getBoolean(Config.DEBUG);
			this.logger.info("start() > isDebug : " + this.isDebug);
		}

		if (config.containsKey(Config.MAIl_USER)) {
			this.logger.info("start() > set mail user", MAIl_USER);
			MAIl_USER = config.getString(Config.MAIl_USER);
		}
		return config;
	}

	private void initStatus(Router router) {
		router.route(HttpMethod.GET, "/rest/status.json").handler(event -> event.response().end(new JsonObject()
				.put("started", startedTime)
				.put("version", VERSION)
				.encodePrettily()));
	}

	private void initLibrary(Router router) {
		
		LibraryRest libs = new LibraryRest(this.tokenService, client);
		router.route(HttpMethod.GET, "/rest/libs").handler(libs.findByUser());
		router.route(HttpMethod.POST, "/rest/libs").handler(libs.create());
		router.route(HttpMethod.GET, "/rest/libs/:libID.json").handler(libs.find());
		router.route(HttpMethod.POST, "/rest/libs/:libID.json").handler(libs.update());

		LibraryTeamRest libTeams = new LibraryTeamRest(this.tokenService, client);
		router.route(HttpMethod.GET, "/rest/libs/:libID/team.json").handler(libTeams.getTeam());
		router.route(HttpMethod.GET, "/rest/libs/:libID/suggestions/team.json").handler(libTeams.getSuggestion());
		router.route(HttpMethod.POST, "/rest/libs/:libID/team/").handler(libTeams.createPermission());
		router.route(HttpMethod.POST, "/rest/libs/:libID/team/:userID.json").handler(libTeams.updatePermission());
		router.route(HttpMethod.DELETE, "/rest/libs/:libID/team/:userID.json").handler(libTeams.removePermission());
	}


	public void initTokenService(JsonObject config) {
		if (Config.isKeyCloak(config)) {
			logger.info("initTokenService() > Use keyCloak");
			KeyCloakConfig keyCloakConfig = Config.getKeyCloak(config);
			this.tokenService = new KeyCloakTokenService(keyCloakConfig);
		} else {
			initQUXTokenService(config);
		}
	}

	private void initQUXTokenService(JsonObject config) {
		QUXTokenService tokenService = new QUXTokenService();
		if (config.containsKey(Config.JWT_PASSWORD)){
			String jwtSecret = config.getString(Config.JWT_PASSWORD);
			if (jwtSecret.trim().length() > 0) {
				tokenService.setSecret(jwtSecret);
			} else {
				logger.error("initQUXTokenService() > Password is empty");
				tokenService.setSecret(Util.getRandomString());
			}
		} else {
			tokenService.setSecret(Util.getRandomString());
			logger.error("initQUXTokenService() > No key. Use random!");
		}
		this.tokenService = tokenService;
	}


	private void initNotification(Router router) {
		NotificationREST rest = new NotificationREST(this.tokenService, client);
		router.route(HttpMethod.GET, "/rest/notifications.json").handler(rest::findByUser);
	}

	private void initMongo(JsonObject config) {
		JsonObject mongoConfig = Config.getMongo(config);
		if (config.containsKey(Config.MONGO_TABLE_PREFIX)) {
			logger.info("initMongo() Set DB prefix to " + config.getString(Config.MONGO_TABLE_PREFIX));
			DB.setPrefix(config.getString(Config.MONGO_TABLE_PREFIX));
		}
		client = MongoClient.createShared(vertx, mongoConfig);
	}


	private void initMail(Router router, JsonObject config){
		logger.info("initMail() > enter");

		JsonObject mailConfig = Config.getMail(config);
		if (mailConfig.containsKey("user")){
			mail = createMail(mailConfig);
			MailHandler.start(
				vertx,
				mail,
				mailConfig.getString("user"),
				Config.getHttpHost(config)
			);
		}
		
		ContactRest contact = new ContactRest();
		router.route(HttpMethod.POST, "/rest/contact").handler(contact::send);
	}

	private MailClient createMail(JsonObject config){

		logger.info("createMail() > enter" + config.getString("user"));

		if(this.isDebug){
			logger.info("createMail() > Create DebugMailClient");
			return new DebugMailClient(config.getBoolean("debug"));
		} else{
			MailConfig mailConfig = new MailConfig();
			mailConfig.setHostname(config.getString("host"));
			mailConfig.setPort(587);
			mailConfig.setStarttls(StartTLSOptions.REQUIRED);
			mailConfig.setUsername(config.getString("user"));
			mailConfig.setPassword(config.getString("password"));
			mailConfig.setStarttls(StartTLSOptions.OPTIONAL);
			mailConfig.setKeepAlive(false);
			return MailClient.createShared(vertx, mailConfig,config.getString("host"));
		}
	}

	private void initBus() {
		DeploymentOptions options = new DeploymentOptions().setWorker(true);
		vertx.deployVerticle(new VMHeater(client), options);
	}

	private void initCommandRest(Router router) {
		CommandStackREST commandStack = new CommandStackREST(this.tokenService, client);
		router.route(HttpMethod.GET, "/rest/commands/:appID.json").handler(commandStack.findOrCreateByApp());
		router.route(HttpMethod.POST, "/rest/commands/:appID.json").handler(commandStack.updateByApp());
		router.route(HttpMethod.POST, "/rest/commands/:appID/add").handler(commandStack.add());
		router.route(HttpMethod.DELETE, "/rest/commands/:appID/pop/:count").handler(commandStack.pop());
		router.route(HttpMethod.POST, "/rest/commands/:appID/undo").handler(commandStack.undo());
		router.route(HttpMethod.POST, "/rest/commands/:appID/redo").handler(commandStack.redo());
	}
	
	
	private void initTestRest(Router router) {
		
		TestSettingsRest rest = new TestSettingsRest(this.tokenService, client, TestSetting.class, "testID");
		router.route(HttpMethod.GET, "/rest/test/:appID.json").handler(rest.findOrCreateByApp());
		router.route(HttpMethod.POST, "/rest/test/:appID.json").handler(rest.updateByApp());
	}
	
	
	private void initAnnotationRest(Router router) {
		
		AppPartREST<Annotation> rest = new AppPartREST<>(this.tokenService, client, Annotation.class, "annotationID");
		
		router.route(HttpMethod.GET, "/rest/annotations/apps/:appID/all.json").handler(rest.findBy());
		router.route(HttpMethod.GET, "/rest/annotations/apps/:appID/:type.json").handler(rest.findBy());
		router.route(HttpMethod.GET, "/rest/annotations/apps/:appID/:reference/:type.json").handler(rest.findBy());
		
		router.route(HttpMethod.POST, "/rest/annotations/apps/:appID").handler(rest.create());
		router.route(HttpMethod.POST, "/rest/annotations/apps/:appID/:annotationID.json").handler(rest.update());
		router.route(HttpMethod.DELETE, "/rest/annotations/apps/:appID/:annotationID.json").handler(rest.delete());
	}

	
	private void initEventRest(Router router) {
		
		EventRest event = new EventRest(this.tokenService, client);
		event.setBatch(true);
		router.route(HttpMethod.POST, "/rest/events/:appID.json").handler(event.create());
		router.route(HttpMethod.GET, "/rest/events/:appID.json").handler(event.findBy());
		router.route(HttpMethod.GET, "/rest/events/:appID/:session.json").handler(event.findBy());
		router.route(HttpMethod.GET, "/rest/events/:appID/all/count.json").handler(event.countBy());
		router.route(HttpMethod.DELETE, "/rest/events/:appID/:session.json").handler(event.deleteBy());
			
		MouseRest mouse = new MouseRest(this.tokenService, client);
		mouse.setBatch(true);
		router.route(HttpMethod.POST, "/rest/mouse/:appID.json").handler(mouse.create());
		router.route(HttpMethod.GET, "/rest/mouse/:appID.json").handler(mouse.findBy());
		router.route(HttpMethod.GET, "/rest/mouse/:appID/:session.json").handler(mouse.findBy());
		router.route(HttpMethod.DELETE, "/rest/mouse/:appID/:session.json").handler(mouse.deleteBy());
	}
	
	
	private void initInvitationRest(Router router, JsonObject config) {
		InvitationREST invitation = new InvitationREST(this.tokenService,client);
		router.route(HttpMethod.GET, "/rest/invitation/:appID.json").handler(invitation.findByApp());
		router.route(HttpMethod.GET, "/rest/invitation/:hash/app.json").handler(invitation.findAppByHash());
		router.route(HttpMethod.GET, "/rest/invitation/:hash/update.json").handler(invitation::getLastUpdate);
		router.route(HttpMethod.GET, "/rest/invitation/:appID/:hash/test.json").handler(invitation.findTestByHash());
		router.route(HttpMethod.POST, "/rest/invitation/:appID/:hash/events.json").handler(invitation.addEvents());
		router.route(HttpMethod.POST, "/rest/invitation/:appID/:hash/mouse.json").handler(invitation.addMouse());
	}
	

	private void initCommentRest(Router router) {
		CommentREST comment = new CommentREST(this.tokenService, client);

		router.route(HttpMethod.GET, "/rest/comments/apps/:appID.json").handler(comment.findBy());
		router.route(HttpMethod.GET, "/rest/comments/apps/:appID/:type.json").handler(comment.findBy());
		router.route(HttpMethod.GET, "/rest/comments/apps/:appID/:reference/:type.json").handler(comment.findBy());
		
		router.route(HttpMethod.GET, "/rest/comments/count/apps/:appID/:type.json").handler(comment.count());
		router.route(HttpMethod.GET, "/rest/comments/count/apps/:appID/:reference/:type.json").handler(comment.count());

	
		router.route(HttpMethod.POST, "/rest/comments/apps/:appID").handler(comment.create());
		router.route(HttpMethod.POST, "/rest/comments/apps/:appID/:commentID.json").handler(comment.update());		
		router.route(HttpMethod.DELETE, "/rest/comments/apps/:appID/:commentID.json").handler(comment.delete());
			
		
		
		CommentREST invitationComment = new CommentREST(this.tokenService, client,new InvitationCommentACL(client))
			.exlcudeUrlParameter("hash");		
		
		router.route(HttpMethod.GET, "/rest/comments/hash/:hash/:appID.json").handler(invitationComment.findBy());
		router.route(HttpMethod.GET, "/rest/comments/hash/:hash/:appID/:type.json").handler(invitationComment.findBy());
		router.route(HttpMethod.GET, "/rest/comments/hash/:hash/:appID/:reference/:type.json").handler(invitationComment.findBy());
		
		router.route(HttpMethod.GET, "/rest/comments/count/hash/:hash/:appID/:type.json").handler(invitationComment.count());
		router.route(HttpMethod.GET, "/rest/comments/count/hash/:hash/:appID/:reference/:type.json").handler(invitationComment.count());
		
		router.route(HttpMethod.GET, "/rest/comments/hash/:hash/:appID/:type/count.json").handler(invitationComment.count());
		router.route(HttpMethod.GET, "/rest/comments/hash/:hash/:appID/:reference/:type/count.json").handler(invitationComment.count());
	
		router.route(HttpMethod.POST, "/rest/comments/hash/:hash/:appID").handler(invitationComment.create());
		router.route(HttpMethod.POST, "/rest/comments/hash/:hash/:appID/:commentID.json").handler(invitationComment.update());		
		router.route(HttpMethod.DELETE, "/rest/comments/hash/:hash/:appID/:commentID.json").handler(invitationComment.delete());
	}
	
	private void initTeamRest(Router router){
	
		TeamREST team = new TeamREST(this.tokenService, client);
	
		router.route(HttpMethod.GET, "/rest/apps/:appID/team.json").handler(team.getTeam());
		router.route(HttpMethod.GET, "/rest/apps/:appID/suggestions/team.json").handler(team.getSuggestion());
		router.route(HttpMethod.POST, "/rest/apps/:appID/team/").handler(team.createPermission());
		router.route(HttpMethod.POST, "/rest/apps/:appID/team/:userID.json").handler(team.updatePermission());
		router.route(HttpMethod.DELETE, "/rest/apps/:appID/team/:userID.json").handler(team.removePermission());
	}
	

	private void initAppRest(Router router, JsonObject config) {

		IBlobService blob = getBlockService(config.getString("image.folder.apps"), config);
		
		AppREST app = new AppREST(this.tokenService, blob, client);
		router.route(HttpMethod.GET, "/rest/apps").handler(app.findByUser());
		router.route(HttpMethod.GET, "/rest/apps/public").handler(app.findPublic());
		router.route(HttpMethod.POST, "/rest/apps").handler(app.create());
		router.route(HttpMethod.GET, "/rest/apps/:appID.json").handler(app.find());
		router.route(HttpMethod.GET, "/rest/apps/embedded/:appID.json").handler(app.findEmbedded());
		router.route(HttpMethod.POST, "/rest/apps/props/:appID.json").handler(app.update());
		router.route(HttpMethod.POST, "/rest/apps/:appID.json").handler(app.update());
		router.route(HttpMethod.DELETE, "/rest/apps/:appID.json").handler(app.delete());
		router.route(HttpMethod.POST, "/rest/apps/copy/:appID").handler(app.copy());
		router.route(HttpMethod.DELETE, "/rest/apps/invitation/:appID").handler(app::resetToken);
		router.route(HttpMethod.POST, "/rest/apps/:appID/update").handler(app.applyChanges());

		IBlobService blobService = getBlockService(config.getString("image.folder.apps"), config);

		ImageREST image = new ImageREST(
				this.tokenService,
				blobService,
				client,
				config.getLong("image.size")
		);
		image.setACL(new AppAcl(client));
		image.setIdParameter("appID");
		
		router.route(HttpMethod.GET, "/rest/images/:appID/:image").handler(image.getImage());
		router.route(HttpMethod.GET, "/rest/images/:hash/:appID/:image").handler(image.getInvitationImage());
		router.route(HttpMethod.POST, "/rest/images/:appID").handler(image.setImage());
		router.route(HttpMethod.GET, "/rest/images/:appID.json").handler(image.findBy());
		router.route(HttpMethod.DELETE, "/rest/images/:appID/:imageID/:ass/:file").handler(image.delete());	
		
	}
	

	private void initUserRest(JsonObject config, Router router) {

		IBlobService blob = getBlockService(config.getString("image.folder.user"), config);
		UserREST user = new UserREST(this.tokenService, blob, client,config);
		
		router.route(HttpMethod.POST, "/rest/user").handler(user.create());
		router.route(HttpMethod.POST, "/rest/user/:id/images/").handler(user.setImage());
		router.route(HttpMethod.GET, "/rest/user/:id/images/:name/:image").handler(user.getImage());
		router.route(HttpMethod.DELETE, "/rest/user/:id/images/:image").handler(user.deleteImage());
		router.route(HttpMethod.POST, "/rest/user/:id.json").handler(user.update());
		router.route(HttpMethod.GET, "/rest/user/:id.json").handler(user.find());
		router.route(HttpMethod.GET, "/rest/user").handler(user.current());
		router.route(HttpMethod.POST, "/rest/login").handler(user.login());
		router.route(HttpMethod.DELETE, "/rest/login").handler(user.logout());
		router.route(HttpMethod.GET, "/rest/retire").handler(user.retire());
		router.route(HttpMethod.POST, "/rest/user/notification/last.json").handler(user::updateNotificationView);
		router.route(HttpMethod.GET, "/rest/user/notification/last.json").handler(user::getNotificationView);
		router.route(HttpMethod.POST, "/rest/user/privacy/update.json").handler(user::updatePrivacy);

		router.route(HttpMethod.POST, "/rest/user/external").handler(user::createExternalIfNotExists);

		PasswordRest pass = new PasswordRest(this.tokenService, client);
		router.route(HttpMethod.POST, "/rest/user/password/request").handler(pass.resetPassword());
		router.route(HttpMethod.POST, "/rest/user/password/set").handler(pass.setPassword());
	}
	


	@Override
	public void stop(){
	
		try {
			
			client.close();
			server.close();
			mail.close();
			
			System.out.println("******************************");
			System.out.println("* Quant-UX Server STOP       *");
			System.out.println("******************************");
	
			super.stop();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}