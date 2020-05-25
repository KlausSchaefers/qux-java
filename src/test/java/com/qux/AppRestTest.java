package com.qux;

import com.qux.model.App;
import com.qux.model.Model;
import com.qux.model.User;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class AppRestTest extends MatcTestCase {
	

	
	@Test
	public void test_SingleUser(TestContext context){
		log("test_SingleUser", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		
		/**
		 * create user
		 */
		User klaus = postUser("klaus", context);
		log("test_SingleUser", klaus.toString());
	
		
		/**
		 * make sure we cannot create an app without being logged in
		 */
		App app = createApp("Not_Allowed", false);
		JsonObject error = post("/rest/apps", app);
		log("test_SingleUser", "error > "+ error);
		context.assertEquals(error.getInteger("error"), 405);
		
		
		/**
		 * now login, we can create an app now
		 */
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		App klaus_app_public = postApp("klaus_app_public", true, context);
		log("test_SingleUser", "klaus_app_public > "+ klaus_app_public);
		
		
		App klaus_app_private = postApp("klaus_app_private", false, context);
		log("test_SingleUser", "klaus_app_private > "+ klaus_app_private);
		
		
		/**
		 * check mongo if the apps are there
		 */
		List<JsonObject> allApps = client.find(app_db, Model.all());
		log("test_SingleUser", "klaus_apps > "+ allApps.size());
		context.assertEquals(2, allApps.size());

		
		/**
		 * test rest get
		 */
		JsonArray klaus_apps = getList("/rest/apps/");
		log("test_SingleUser", "klaus_apps > "+ klaus_apps.size());
		context.assertEquals(2, klaus_apps.size());
		
		JsonArray public_apps = getList("/rest/apps/public");
		log("test_SingleUser", "public_apps > "+ public_apps.size());
		context.assertEquals(1, public_apps.size());
		
		klaus_app_private = get("/rest/apps/" + klaus_app_private.getId() + ".json", App.class);
		context.assertNotNull(klaus_app_private);
		log("test_SingleUser", "klaus_app_private > "+ klaus_app_private);
		
		JsonObject result = get("/rest/apps/" + klaus_app_private.getId() + ".json");
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(!result.containsKey("users"));
		
		
		/**
		 * test updates
		 */
		result.put("name", "klaus_app_private2");
		result.put("screens", new JsonObject().put("s1", new JsonObject().put("id", "s1")));
		
		JsonObject update_result = post("/rest/apps/" + klaus_app_private.getId() + ".json", result);
		log("test_SingleUser", "update : " + update_result);
		
		JsonObject klaus_app_private2 = get("/rest/apps/" + klaus_app_private.getId() + ".json");
		log("test_SingleUser", "get(updated) : " + klaus_app_private2);
		
		/**
		 * test also command
		 */
		JsonObject stack = get("/rest/commands/" + klaus_app_private.getId() + ".json");
		log("test_SingleUser", "get(stack) : " + stack);
		context.assertTrue(!stack.containsKey("error"));
		context.assertTrue(!stack.containsKey("errors"));
		context.assertEquals(stack.getString("appID"), klaus_app_private.getId());
		
		stack.getJsonArray("stack").add(new JsonObject().put("id", "c1").put("type", "TestCommand"));
		update_result = post("/rest/commands/" + klaus_app_private.getId() + ".json", stack);
		log("test_SingleUser", "update(stack) : " + update_result);
		context.assertTrue(!stack.containsKey("error"));
		//context.assertEquals("command.update.success", update_result.getString("details"));
		
		
		stack = get("/rest/commands/" + klaus_app_private.getId() + ".json");
		log("test_SingleUser", "get(stack)2 : " + stack);
		context.assertTrue(!stack.containsKey("error"));
		context.assertTrue(!stack.containsKey("errors"));
		context.assertEquals(stack.getString("appID"), klaus_app_private.getId());
		context.assertEquals(stack.getJsonArray("stack").size(), 1);
		context.assertEquals(stack.getJsonArray("stack").getJsonObject(0).getString("id"), "c1");
		context.assertEquals(stack.getJsonArray("stack").getJsonObject(0).getString("type"), "TestCommand");
		
		/**
		 * logout, user cannot read & write
		 */
		logout();
		
		JsonObject find_error = get("/rest/apps/" + klaus_app_private.getId() + ".json");
		log("test_SingleUser", "find(app) error : " + find_error);
		context.assertEquals(find_error.getInteger("error"), 401);
		
		find_error = get("/rest/commands/" + klaus_app_private.getId() + ".json");
		log("test_SingleUser", "find(app) error : " + find_error);
		context.assertEquals(find_error.getInteger("error"), 404);
		
		
		
		JsonObject update_error = post("/rest/apps/" + klaus_app_private.getId() + ".json", result);
		log("test_SingleUser", "update(app) error : " + update_error);
		context.assertEquals(update_error.getInteger("error"), 401);
		
		update_result = post("/rest/commands/" + klaus_app_private.getId() + ".json", stack);
		log("test_SingleUser", "update(command) error : " + update_error);
		context.assertEquals(update_error.getInteger("error"), 401);
		
		
		/**
		 * login again
		 */
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		
		stack = get("/rest/commands/" + klaus_app_private.getId() + ".json");
		log("test_SingleUser", "get(stack)3 : " + stack);
		context.assertTrue(!stack.containsKey("error"));
		context.assertTrue(!stack.containsKey("errors"));
		context.assertEquals(stack.getString("appID"), klaus_app_private.getId());
		context.assertEquals(stack.getJsonArray("stack").size(), 1);
		context.assertEquals(stack.getJsonArray("stack").getJsonObject(0).getString("id"), "c1");
		context.assertEquals(stack.getJsonArray("stack").getJsonObject(0).getString("type"), "TestCommand");
		
		log("test_SingleUser", "exit");
	}
	

	
}
