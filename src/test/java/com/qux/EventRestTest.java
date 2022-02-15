package com.qux;

import com.qux.model.App;
import com.qux.util.rest.MongoREST;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class EventRestTest extends MatcTestCase {
	


	@Test
	public void testParseQuery(TestContext context){
		log("testParseQuery", "enter");
		
		Map<String,String> result = MongoREST.parseQuery("a=1");
		context.assertEquals(1, result.size());
		context.assertEquals("1", result.get("a"));
		
		
		result = MongoREST.parseQuery("exclude=Animation&batch=true");
		context.assertEquals(2, result.size());
		context.assertEquals("Animation", result.get("exclude"));
		context.assertEquals("true", result.get("batch"));
		
		result = MongoREST.parseQuery("batch=true&exclude=Animation");
		context.assertEquals(2, result.size());
		context.assertEquals("Animation", result.get("exclude"));
		context.assertEquals("true", result.get("batch"));
		
		result = MongoREST.parseQuery("batch=true");
		context.assertEquals(1, result.size());
		context.assertEquals("true", result.get("batch"));
		
		result = MongoREST.parseQuery("exclude=Animation");
		context.assertEquals(1, result.size());
		context.assertEquals("Animation", result.get("exclude"));
		
		log("testParseQuery", "exit");
	}
	
	
	@Test
	public void testBatch(TestContext context){
		log("testBatch", "enter");
		
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		/**
		 * create user & app
		 */
		postUser("klaus", context);
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		App klaus_app_public = postApp("klaus_app_public", true, context);
	
		/**
		 * add events
		 */
		for (int i=0; i< 100; i++) {
			postEvent(klaus_app_public, "session"+i, "Click", context);
			postEvent(klaus_app_public, "session"+i, "Animation", context);
		}
		assertList("/rest/events/" + klaus_app_public.getId() +".json", 200, context);
		assertList("/rest/events/" + klaus_app_public.getId() +".json?batch=true", 200, context);
		assertList("/rest/events/" + klaus_app_public.getId() +".json?batch=true&exclude=Animation", 100, context);
		
		JsonObject count = this.get("/rest/events/" + klaus_app_public.getId() +"/all/count.json");
		context.assertEquals(200, count.getInteger("count"));
		
		count = this.get("/rest/events/" + klaus_app_public.getId() +"/all/count.json?batch=true&exclude=Animation");
		context.assertEquals(100, count.getInteger("count"));
		
		log("testBatch", "exit");
	}
	

	@Test
	public void test(TestContext context){
		log("test", "enter");
		
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		/**
		 * create user & app
		 */
		postUser("klaus", context);
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		App klaus_app_public = postApp("klaus_app_public", true, context);
		App klaus_app_private = postApp("klaus_app_private", false, context);
		
		/**
		 * add events
		 */
		postEvent(klaus_app_public, "session1", "Click", context);
		postEvent(klaus_app_public, "session2", "Click", context);
		postEvent(klaus_app_public, "session3", "Click", context);
		assertList("/rest/events/" + klaus_app_public.getId() +".json", 3, context);
		
		JsonObject count = this.get("/rest/events/" + klaus_app_public.getId() +"/all/count.json");
		context.assertEquals(3, count.getInteger("count"));
		
		
		postEvent(klaus_app_private, "session1", "Click", context);
		postEvent(klaus_app_private, "session1", "Click", context);
		assertList("/rest/events/" + klaus_app_private.getId() +".json", 2, context);
		count = this.get("/rest/events/" + klaus_app_private.getId() +"/all/count.json");
		context.assertEquals(2, count.getInteger("count"));
		
		
		/**
		 * logout
		 */
		logout();
		postEventError(klaus_app_public, "session3", "Click", context);
		postEventError(klaus_app_private, "session1", "Click", context);
		
		assertListError("/rest/events/" + klaus_app_private.getId() +".json", context);
		assertListError("/rest/events/" + klaus_app_public.getId() +".json", context);
		count = this.get("/rest/events/" + klaus_app_private.getId() +"/all/count.json");
		context.assertEquals(true, count.containsKey("error"));
		context.assertEquals(401, count.getInteger("error"));

		/**
		 * check if the public events were added
		 */
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		assertList("/rest/events/" + klaus_app_public.getId() +".json", 3, context);
		
		
		
		
		log("test", "exit");
	}
	
	
	@Test
	public void testDeletSession(TestContext context){
		log("testDeletSession", "enter");
		
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		/**
		 * create user & app
		 */
		postUser("klaus", context);
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		App klaus_app_public = postApp("klaus_app_public", true, context);
		
		/**
		 * add events
		 */
		postEvent(klaus_app_public, "session1", "Click1", context);
		postEvent(klaus_app_public, "session1", "Click2", context);
		postEvent(klaus_app_public, "session1", "Click3", context);
		postEvent(klaus_app_public, "session1", "Click4", context);
		postEvent(klaus_app_public, "session2", "Click", context);
		postEvent(klaus_app_public, "session3", "Click", context);
		assertList("/rest/events/" + klaus_app_public.getId() +".json", 6, context);
		
		deleteSession(klaus_app_public, "session1", context);
		assertList("/rest/events/" + klaus_app_public.getId() +".json", 2, context);
		
		
		
		
		log("testDeletSession", "exit");
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
	
	public void deleteSession(App app, String session, TestContext context){
	
		
		JsonObject result = delete("/rest/events/" + app.getId() + "/" + session +".json");
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
	
	
	
	
	@Test
	public void testExclusion(TestContext context){
		log("testExclusion", "enter");
		
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		/**
		 * create user & app
		 */
		postUser("klaus", context);
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		App klaus_app_public = postApp("klaus_app_public", true, context);
		App klaus_app_private = postApp("klaus_app_private", false, context);
		
		/**
		 * add events
		 */
		postEvent(klaus_app_public, "session1", "Click", context);
		postEvent(klaus_app_public, "session2", "Click", context);
		postEvent(klaus_app_public, "session3", "Click", context);
		postEvent(klaus_app_public, "session3", "Animation", context);
		assertList("/rest/events/" + klaus_app_public.getId() +".json", 4, context);
		assertList("/rest/events/" + klaus_app_public.getId() +".json?exclude=Animation", 3, context);
		assertList("/rest/events/" + klaus_app_public.getId() +".json?exclude=Animation&batch=true", 3, context);
		
		
		postEvent(klaus_app_private, "session1", "Click", context);
		postEvent(klaus_app_private, "session1", "Click", context);
		postEvent(klaus_app_private, "session1", "Animation", context);
		assertList("/rest/events/" + klaus_app_private.getId() +".json", 3, context);
		assertList("/rest/events/" + klaus_app_private.getId() +".json?exclude=Animation", 2, context);
		
		
		
		log("test", "exit");
	}
	

}
