package com.qux.rest;

import com.qux.acl.AppAcl;
import com.qux.acl.InvitationACL;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import com.qux.model.App;
import com.qux.model.AppEvent;
import com.qux.model.Event;
import com.qux.model.Invitation;
import com.qux.model.Mouse;
import com.qux.model.TestSetting;
import com.qux.util.DB;
import com.qux.util.MongoREST;

public class InvitationREST extends MongoREST{
	
	private final String app_db, event_db, server, test_db, mouse_db;
	
	private Logger logger = LoggerFactory.getLogger(InvitationREST.class);
	
	private final int port;
	
	private final InvitationACL invACL;
	
	private final String invDB;
	

	public InvitationREST(MongoClient db, String server, int port) {
		super(db, Invitation.class);
		setACL(new AppAcl(db));
		this.invACL = new InvitationACL(db);
		this.app_db = DB.getTable(App.class);
		this.event_db = DB.getTable(Event.class);
		this.test_db = DB.getTable(TestSetting.class);
		this.server = server;
		this.port = port;
		this.mouse_db = DB.getTable(Mouse.class);
		this.invDB = DB.getTable(Invitation.class);
	}
	
	
	public Handler<RoutingContext> findByApp() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				findByApp(event, null);
			}
		};
	}

	private void findByApp(RoutingContext event, Handler<JsonObject> handler) {
		/**
		 * People that can write the app can also ask 
		 */
		this.acl.canWrite(getUser(event), event , allowed -> {
			String appID = getId(event, "appID");
			if (allowed) {
				this.findByApp(event, appID, handler);
			} else {
				/**
				 * check if is public
				 */
				mongo.count(app_db, App.findPublicByID(appID), isPublic->{
					if(isPublic.succeeded() && isPublic.result() == 1l){
						this.findByApp(event, appID, handler);
					} else {
						error("findByApp", "User " + getUser(event) + " tried to access " + event.request().path());
						returnError(event, 405);
					}
				});
			}
		});
		
	}
	
	private void findByApp(RoutingContext event, String appID, Handler<JsonObject> handler) {
		
		mongo.find(invDB, Invitation.findByApp(appID) , res ->{
			if(res.succeeded()){
				List<JsonObject> list = res.result();
				JsonObject inv = Invitation.getInvitationFromList(list);				
				if(handler!=null){
					handler.handle(inv);
				} else {
					returnJson(event, cleanJson(inv));
				}
			} else {
				returnError(event, 404);
			}
		});
	}
	
	
	
	
	/********************************************************************************************
	 * findByHash
	 ********************************************************************************************/
	
	public Handler<RoutingContext> findAppByHash() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				findAppByHash(event);
			}
		};
	}

	private void findAppByHash(RoutingContext event) {
		String hash = getId(event, "hash");
		logger.info("findAppByHash() > enter > " + hash);
		mongo.findOne(invDB, Invitation.findByHash(hash), null, invFound ->{
			if(invFound.succeeded()){
				JsonObject inv = invFound.result();
				if(inv!=null){
					String appID = inv.getString("appID");
					mongo.find(app_db, App.findById(appID), res ->{
						if(res.succeeded()){
							List<JsonObject> apps = res.result();
							if(apps.size() == 1){
								JsonObject app  =apps.get(0);
								returnJson(event, cleanJson(app));
							} else {
								error("findAppByHash", "Found  not *1* but " +  apps.size() + " apps with hash "+ hash);
								returnError(event, 404);
							}
						} else {
							returnError(event, 404);
						}
					});
				} else {
					error("findAppByHash", "Found  not 0 apps with hash "+ hash);
					returnError(event, 404);
				}
			} else {
				error("findAppByHash", "Found  not 0 apps with hash "+ hash);
				returnError(event, 404);
			}
		});
	}
	
	
	/********************************************************************************************
	 * findByHash
	 ********************************************************************************************/

	
	public Handler<RoutingContext> findTestByHash() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				findTestByHash(event);
			}
		};
	}

	private void findTestByHash(RoutingContext event) {
		this.invACL.canTest(getUser(event), event, res ->{
			if(res){
				String appID = getId(event, "appID");
				findTestByApp(event, appID);		
			} else {
				error("findTestByHash", "Could not read form mongo > " + event.request().path());
				returnError(event, 404);
			}
		});		
	}
	
	
	private void findTestByApp(RoutingContext event, String appID) {
		
		mongo.find(test_db, TestSetting.findByApp(appID) , res ->{
			if(res.succeeded()){
				List<JsonObject> testSettings = res.result();
				
				if(testSettings.size() == 0){
					logger.info("findTestByApp() > No test settings for " + appID + "Return empty settings"); 
					
					JsonObject  json = new JsonObject()					
						.put("appID", getId(event, "appID"))
						.put("created", System.currentTimeMillis())
						.put("tasks", new JsonArray());
					
					returnJson(event, json);
				} else if(testSettings.size() == 1){
					JsonObject testSetting  =testSettings.get(0);
					returnJson(event, cleanJson(testSetting));
				
				} else {
					error("findTestByApp", "Found more or less then *1* app for the given appID!" + testSettings.size() + " > " + appID);
					returnError(event, 404);
				}
			} else {
				returnError(event, 404);
			}
		});
	}
	
	

	/********************************************************************************************
	 * findByHash
	 ********************************************************************************************/

	public void getLastUpdate(RoutingContext event) {
		logger.info("applyChanges() > enter");
		String hash = getId(event, "hash");
		mongo.findOne(invDB, Invitation.findByHash(hash), null, invFound ->{
			if(invFound.succeeded()){
				JsonObject inv = invFound.result();
				if(inv!=null){
					String appID = inv.getString("appID");
					getLastUpdate(event, appID);
				} else {
					error("findAppByHash", "Found  not 0 apps with hash "+ hash);
					returnError(event, 404);
				}
			} else {
				error("findAppByHash", "Found  not 0 apps with hash "+ hash);
				returnError(event, 404);
			}
		});
	}
	
		
	protected void getLastUpdate(RoutingContext event, String id) {
		logger.info("getLastUpdate() > " + id); 
		JsonObject fields = new JsonObject()
			.put("screens", 0)
			.put("groups", 0)
			.put("widgets", 0)
			.put("templates", 0)
			.put("grid", 0)
			.put("lines", 0);
		
		mongo.findOne(app_db, App.findById(id),fields, appRes->{
			if(appRes.succeeded()){
				JsonObject result = appRes.result();
				if (result!= null){
					event.response().end(result.encode());
				} else {
					logger.error("findByUser() > Could not find live " + id);
					returnError(event, 404);
				}
			} else {
				logger.error("findByUser() > Mongo Error : " + appRes.cause().getMessage());
				returnError(event, 404);
			}
		});
	}
	

	/********************************************************************************************
	 * findByHash
	 ********************************************************************************************/

	
	
	
	public Handler<RoutingContext> addEvents() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				addEvents(event, event_db);
			}
		};
	}
	
	public Handler<RoutingContext> addMouse() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				addEvents(event, mouse_db );
			}
		};
	}

	private void addEvents(RoutingContext event, String db) {
	
				
		/**
		 * Make sure the user can test.
		 */
		this.invACL.canTest(getUser(event), event, res ->{
			if(res){
				String appID = getId(event, "appID");
				addEvents(event, appID, db);
			} else {
				error("addEvents", "User " + getUser(event) + " tried to add events to app "+ event.request().path());
				returnError(event, 404);
			}
		});
	}
	
	private void addEvents(RoutingContext event, String appID, String db) {
		
		JsonObject json = getJson(event);
		json.put("appID", appID);
		
		/**
		 * TODOL Add here some count check...
		 */
		mongo.insert(db, json, res->{
			if(res.succeeded()){
				returnOk(event, "events.added");
				
				/**
				 * Log SessionStart
				 */
				if(json.containsKey("type") && "SessionStart".equals(json.getString("type"))){
					AppEvent.send(event, getUser(event).getEmail(), AppEvent.TYPE_APP_TEST, appID);
				}
				
			} else {
				returnError(event, 404);
			}
		});
	}
	
	
	/********************************************************************************************
	 * QR Code stuff
	 ********************************************************************************************/

	
	public Handler<RoutingContext> getTestQR() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				getQR(event, "true", "false");
			}
		};
	}
	
	public Handler<RoutingContext> getDebugQR() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				getQR(event, "false", "true");
			}
		};
	}
	
	public Handler<RoutingContext> getTestQRByHash() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				String hash = getId(event, "hash");
				getQR(event, "true","false", hash);
			}
		};
	}
	
	
	public Handler<RoutingContext> getDebugQRByHash() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				String hash = getId(event, "hash");
				getQR(event, "false","true", hash);
			}
		};
	}
	
	
	
	private void getQR(RoutingContext event, String log, String live, String hash) {
		
		String server = getServer(event);
		
		String url = server + "/simulate.html?&h=" + hash + "&log=" + log +"&qr=true&live=" + live;
		
		qr(url, event);
		
		logger.info("getQR() > Exit > " + url); 
	}
	
	
	
	private void getQR(RoutingContext event, String log, String live) {
		
		String appID = getId(event, "appID");
		/**
		 * People that can read can also get the QR Code and thus
		 * later test.
		 */
		this.acl.canRead(getUser(event), event , allowed -> {
			if (allowed) {
				
				/**
				 * FIXME: We should do here a query...
				 */
				findByApp(event, appID, invitations ->{
					
					String hash = Invitation.getHash(invitations, Invitation.TEST);
					
					getQR(event, log, live, hash);
									
				});
			} else {
				error("getQR", "User " + getUser(event) + " tried to access " + event.request().path());
				qr( "Sign Up for free to load the prototype to your phone.", event);
			}
		});
		
	}

	

	private void qr(String url,RoutingContext event ) {
		try {
			
			
			/**
			 * FIXME: do not  in IO-Loop
			 */
			Map<EncodeHintType, Object> hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
			hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
			hints.put(EncodeHintType.MARGIN, 0); /* default = 4 */
			
			BitMatrix bitMatrix = new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE, 300, 300, hints );
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			MatrixToImageWriter.writeToStream(bitMatrix, "jpg", out);
			
			event.response().putHeader("content-type", "image/jpeg");
			event.response().end(Buffer.buffer(out.toByteArray()));
			
			return;
			
		} catch (Exception e) {
			error("qr", "Something went wrong" + e.getMessage());
		}
		returnError(event, 404);;
	}
	
	// add invitations to app
	
	private String getServer(RoutingContext event) {
		String path = event.request().absoluteURI();
		/**
		 * Make it work for debug
		 */
		if(path.startsWith("http://0.0.0.0") || path.startsWith("http://localhost")){
			try {
				InetAddress inetAddress = InetAddress.getLocalHost();
				String ip = inetAddress.getHostAddress();
				return "http://" + ip + ":" + port;
			} catch (UnknownHostException e) {
				error("getServer", "UnknownHostException");
			}
		}
		if (path.contains("kyra")){
			return "http://www.kyra.app";
		}
		return server; // assume in live mode we are always 80
	}
	
	protected JsonObject cleanJson(JsonObject json){
		json.remove("users");
		json.remove("invitations");
		return super.cleanJson(json);
	}
	

}
