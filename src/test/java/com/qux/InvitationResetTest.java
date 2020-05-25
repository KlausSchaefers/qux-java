package com.qux;

import java.util.List;

import com.qux.model.App;
import com.qux.model.AppPart;
import com.qux.model.Invitation;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class InvitationResetTest extends MatcTestCase {
		
	
	@Test
	public void testResetToken (TestContext context){
			log("testResetToken", "enter");
			
			
			cleanUp();
			
			deploy(new MATC(), context);
			
			/**
			 * create user & app
			 */
			postUser("klaus", context);
			
			/**
			 * Klaus
			 */
			assertLogin(context, "klaus@quant-ux.de", "123456789");
			App app = postApp("klaus_app_private", false, context);
			JsonObject inv = getInvitation(app, context);
		
			for(String token : inv.fieldNames()) {
				getInvitationApp(app, token, context);
			}
			
			// reset
			resetInvitations(app);
			
			// old tokens should not work
			for(String token : inv.fieldNames()) {
				getInvitationAppError(app, token, context);
			}
			
			// get new tokens
			JsonObject inv2 = getInvitation(app, context);
			for(String token : inv2.fieldNames()) {
				getInvitationApp(app, token, context);
			}
			
			
			// logout & and make sure we cannot reset
			logout();
			JsonObject resetError = resetInvitations(app);
			context.assertTrue(resetError.containsKey("error"));
			
						
			
			log("testResetToken", "exit");
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
