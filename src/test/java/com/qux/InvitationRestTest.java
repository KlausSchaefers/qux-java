package com.qux;

import java.util.List;

import com.qux.acl.Acl;
import com.qux.model.App;
import com.qux.model.AppPart;
import com.qux.model.Invitation;
import com.qux.model.User;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class InvitationRestTest extends MatcTestCase {
	

	@Test
	public void test(TestContext context){
		log("test", "enter");
		
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		/**
		 * create user & app
		 */
		postUser("klaus", context);
		User dennis= postUser("dennis", context);
		User bernd  = postUser("bernd", context);
		
		/**
		 * Klaus
		 */
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		App klaus_app_public = postApp("klaus_app_public", true, context);
		App klaus_app_public2 = postApp("klaus_app_public2", true, context);
		App klaus_app_private = postApp("klaus_app_private", false, context);
		createPermission(dennis, klaus_app_private, Acl.READ, context);
		createPermission(bernd, klaus_app_private, Acl.WRITE, context);
		
		getInvitation(klaus_app_private, context);
		getQR(klaus_app_private, "test", context);
		getQR(klaus_app_public, "debug", context);
		
		getApp(klaus_app_private, context);
		getApp(klaus_app_public, context);
		getApp(klaus_app_public2, context);
	
		
		/**
		 * Check that dennis CANNOT get the invitations
		 */
		assertLogin(context, "dennis@quant-ux.de", "123456789");
		getInvitationError(klaus_app_private, context);		
		getInvitation(klaus_app_public, context);
		
		getQR(klaus_app_private, "debug", context);
		getQR(klaus_app_public, "debug", context);
		
		getApp(klaus_app_private, context);
		getApp(klaus_app_public, context);
		getApp(klaus_app_public2, context);
		
		/**
		 * bernd can get invitations
		 */
		assertLogin(context, "bernd@quant-ux.de", "123456789");
		getInvitation(klaus_app_private, context);
		getInvitation(klaus_app_public, context);
		
		getQR(klaus_app_private, "debug", context);
		getQR(klaus_app_public, "debug", context);
		
		getApp(klaus_app_private, context);
		getApp(klaus_app_public, context);
		getApp(klaus_app_public2, context);
		
		/**
		 * check that guest cannot get invitations
		 */
		logout();
		getInvitationError(klaus_app_private, context);
		getInvitation(klaus_app_public, context);
	
		
		getAppError(klaus_app_private, context);
		getApp(klaus_app_public, context);
		getApp(klaus_app_public2, context);
		
		//getQRError(klaus_app_private, "debug", context);
		//getQR(klaus_app_public, "debug", context);
		//getQR(klaus_app_public2, "debug", context);
		
		
		
		/**
		 * Now test if we can use the invitation to read the app and to
		 */
		logout();
		assertInvitationApp(klaus_app_public,3, context);
		assertInvitationApp(klaus_app_private, 3,context);
		
	
		
		assertLogin(context, "bernd@quant-ux.de", "123456789");
		assertInvitationApp(klaus_app_public, 6, context);
		assertInvitationApp(klaus_app_private, 6, context);
		
		log("test", "exit");
	}
	
	

	public JsonObject resetInvitations(App app) {
		return this.delete("/rest/apps/invitation/" + app.getId());
	}
	
	public void assertInvitationApp(App app, int event, TestContext context){

		
		List<JsonObject> list = client.find(inv_db, Invitation.findByApp(app.getId()));
		JsonObject invitations = Invitation.getInvitationFromList(list);		
		
		/**
		 * check if all hashes allow findByApp
		 */
		String hash = Invitation.getHash(invitations, Invitation.TEST);
		getInvitationApp(app, hash, context);
		
		hash = Invitation.getHash(invitations, Invitation.READ);
		getInvitationApp(app, hash, context);
		
		hash = Invitation.getHash(invitations, Invitation.WRITE);
		getInvitationApp(app, hash, context);
		
		/**
		 * Make sure other hashs dont work
		 */
		getInvitationAppError(app, "NOT-EXISTING-HASH", context);
		JsonObject query = Invitation.canTest(app.getId(), Invitation.getHash(invitations, Invitation.TEST));

		
		context.assertEquals(1l, client.count(inv_db, query), "Count query does not work");		
		JsonObject test_iv = client.findOne(inv_db, query);		
		context.assertEquals(Invitation.TEST, test_iv.getInteger(Invitation.PERMISSION));
		context.assertEquals(app.getId(), test_iv.getString(Invitation.APP_ID));
		context.assertEquals(Invitation.getHash(invitations, Invitation.TEST), test_iv.getString(Invitation.HASH));
		
		/**
		 * lets send some events
		 */
		hash = Invitation.getHash(invitations, Invitation.TEST);
		postInvitationEvents(app, hash, context);
		
		hash = Invitation.getHash(invitations, Invitation.READ);
		postInvitationEvents(app, hash, context);
		
		hash = Invitation.getHash(invitations, Invitation.WRITE);
		postInvitationEvents(app, hash, context);
		
		if(!app.getPublic()){
			postInvitationEventsError(app, "NOT-EXISTING-HASH2", context);
		} 
		//else {
//			postInvitationEventsError(app, "NOT-EXISTING-HASH2", context);
//		}
	
		
		List<JsonObject> events = client.find(event_db, AppPart.findByApp(app.getId()));
		context.assertEquals(event, events.size());
	}
	
	public void postInvitationEvents(App app, String hash, TestContext context){
		
		JsonObject event = new JsonObject()
			.put("session", "asd")
			.put("user", "user")
			.put("screen", "s1")
			.put("widget", "w1")
			.put("type", "Click")
			.put("user", "user")
			.put("time", System.currentTimeMillis())
			.put("x", 3)
			.put("y", 4);
		
		JsonObject result = post("/rest/invitation/" +app.getId() + "/" + hash + "/events.json",event);
		log("postInvitationEvents", result +"");
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(!result.containsKey("errors"));

		
	}
	
	public void postInvitationEventsError(App app, String hash, TestContext context){
		
		JsonObject event = new JsonObject()
			.put("session", "asd")
			.put("user", "user")
			.put("screen", "s1")
			.put("widget", "w1")
			.put("type", "Click")
			.put("user", "user")
			.put("time", System.currentTimeMillis())
			.put("x", 3)
			.put("y", 4);
		
		JsonObject result = post("/rest/invitation/" +app.getId() + "/" + hash + "/events.json",event);
		context.assertTrue(result.containsKey("error") || result.containsKey("errors"));

		
	}
	
	
	public JsonObject getInvitationApp(App app, String hash, TestContext context){

		JsonObject result = get("/rest/invitation/" + hash + "/app.json");
		log("getInvitationApp", " > "+ result);
		
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(!result.containsKey("errors"));
		
		context.assertTrue(!result.containsKey("users"));
		context.assertTrue(!result.containsKey("invitations"));
		
		context.assertEquals(app.getId(), result.getString("id"));
		
		return result;
		
	}
	
	/**
	 * Make sure we cannot read the app with a wrong token
	 */
	public JsonObject getInvitationAppError(App app, String hash, TestContext context){
		JsonObject result = get("/rest/invitation/" + hash + "/app.json");
		debug("getInvitationAppError", " > "+ result);
		context.assertTrue(result.containsKey("error") || result.containsKey("errors"));
		return result;
	}
	
	
	public void getQR(App app, String type ,TestContext context){

//		InputStream is = getRaw("/rest/invitation/" +app.getId() + "/" + type + ".jpg");
//		context.assertNotNull(is, app.toString());
//		String url = QRCodeReader.decode(is);
//		context.assertNotNull(url);
//		
//		log("getQR", url);
	}
	
	
	public void getQRError(App app, String type ,TestContext context){
		
//		InputStream is = getRaw("/rest/invitation/" +app.getId() + "/" + type + ".jpg");
//		context.assertNull(is);
//	
	}
	
	
	
	public JsonObject getInvitationError(App app,TestContext context ){
		
		JsonObject result = get("/rest/invitation/" +app.getId() + ".json");
		log("getInvitationError", ""+result);
		context.assertTrue(result.containsKey("error") || result.containsKey("errors"));
	
		return result;	
	}
	
	public JsonObject getInvitation(App app,TestContext context ){
		JsonObject result = get("/rest/invitation/" +app.getId() + ".json");
		log("getInvitation", ""+result);
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(!result.containsKey("errors"));
		return result;
	}
	
	public void postEvent(App app, String session, String type, TestContext context){
		JsonObject event = new JsonObject()
			.put("session", session)
			.put("user", "user")
			.put("screen", "s1")
			.put("widget", "w1")
			.put("type", type)
			.put("user", "user")
			.put("time", System.currentTimeMillis())
			.put("x", 3)
			.put("y", 4);
		
		JsonObject result = post("/rest/events/" + app.getId() +".json", event);
		log("postEvent", ""+result);
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(!result.containsKey("errors"));
	
	}
	
	public void postEventError(App app, String session, String type, TestContext context){
		JsonObject event = new JsonObject()
			.put("session", session)
			.put("user", "user")
			.put("screen", "s1")
			.put("widget", "w1")
			.put("type", type)
			.put("user", "user")
			.put("time", System.currentTimeMillis())
			.put("x", 3)
			.put("y", 4);
		
		JsonObject result = post("/rest/events/" + app.getId() +".json", event);
		log("postEventError", ""+result);
		context.assertTrue(result.containsKey("error") || result.containsKey("errors"));
		context.assertEquals(405, result.getInteger("error"));
	}

}
