package com.qux;

import com.qux.model.App;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class CommandStackRestTest extends MatcTestCase{
	
	@Test
	public void test_Command(TestContext context){
		log("test_Command", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		postUser("klaus", context);
		
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		App app = postApp("klaus_app_public", true, context);
		
	
		log("test_Command", "Created App > "+ app.getId());
		assertStack(app, context);
		
		
		for(int i=1; i< 100; i++){
			JsonObject command = new JsonObject().put("type", "AddWidget").put("new", new JsonObject().put("x",10)).put("id", "c" + i);
			command = postCommand(app, command, i, i, context);
		}
		

		
		/**
		 * Logout
		 */
		logout();

		JsonArray error =  getList("/rest/commands/" +app.getId() + ".json");
		context.assertEquals(404, error.getInteger(0));
	
		
		log("test_Command", "exit");
	}
	
	@Test
	public void test_CommandSimple(TestContext context){
		log("test_CommandSimple", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		postUser("klaus", context);
		
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		App app = postApp("klaus_app_public", true, context);
		
	
		log("test_Command", "Created App > "+ app.getId());
		assertStack(app, context);
		
		JsonObject command = new JsonObject().put("type", "AddWidget").put("new", new JsonObject().put("x",10)).put("id", "c1");
		command = postCommand(app, command, 1, 1, context);
		
		
		JsonObject command2 = new JsonObject().put("type", "AddScreen").put("new", new JsonObject().put("x",10)).put("id", "c2");
		command2 = postCommand(app, command2,2, 2, context);

		postUndo(app, 1, 2, context);
		
		postRedo(app, 2, 2, context);
		
		
		
		JsonObject command3 = new JsonObject().put("type", "AddScreen").put("new", new JsonObject().put("x",10)).put("id", "c3");
		command3 = postCommand(app, command3, 3, 3, context);
		
		/**
		 * delete command 1
		 */
		removeCommand(app, 2, 1,1, context);
	
		
		/**
		 * Logout
		 */
		logout();

		JsonArray error =  getList("/rest/commands/" +app.getId() + ".json");
		context.assertEquals(404, error.getInteger(0));
	
		JsonObject error3 = post("/rest/commands/" +app.getId()+"/add", command);
		log("test_Command", error3.encode());
		context.assertTrue(error3.containsKey("error") );
		
		log("test_Command", "exit");
	}
	
	public void assertStack(App app, TestContext context){
		JsonObject stack = get("/rest/commands/" + app.getId() + ".json");
		log("assertStack", "get(stack) : " + stack);
		context.assertTrue(!stack.containsKey("error"));
		context.assertTrue(!stack.containsKey("errors"));
		context.assertEquals(stack.getString("appID"), app.getId());
	}
	
	
	public void postRedo(App app,  int expectedPos, int extpectedLength, TestContext context){
		log("postRedo", "enter");
		JsonObject result = post("/rest/commands/" +app.getId()+"/redo", new JsonObject());
		context.assertTrue(!result.containsKey("errors"));
		context.assertEquals(expectedPos, result.getInteger("pos"));
		
		JsonObject stack = get("/rest/commands/" + app.getId() + ".json");
		log("postRedo", "stack > "+  stack.encode());
		context.assertEquals(expectedPos, stack.getInteger("pos"));
		context.assertEquals(extpectedLength, stack.getJsonArray("stack").size());
		
	}	
	
	public void postUndo(App app, int expectedPos, int extpectedLength, TestContext context){
		log("postUndo", "enter");
		JsonObject result = post("/rest/commands/" +app.getId()+"/undo", new JsonObject().put("as","as"));
		context.assertTrue(!result.containsKey("errors"));
		context.assertEquals(expectedPos, result.getInteger("pos"));
		
		JsonObject stack = get("/rest/commands/" + app.getId() + ".json");
		log("postRedo", "stack > "+  stack.encode());
		context.assertEquals(expectedPos, stack.getInteger("pos"));
		context.assertEquals(extpectedLength, stack.getJsonArray("stack").size());
		
	}	
	
	
	public JsonObject postCommand(App app, JsonObject command, int expectedPos, int extpectedLength, TestContext context){
		JsonObject result = post("/rest/commands/" +app.getId()+"/add", command);
		
		log("postCommand", "result > " + result.encode());
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("command"));
		context.assertEquals(expectedPos, result.getInteger("pos"));
		
		JsonObject stack = get("/rest/commands/" + app.getId() + ".json");
		context.assertEquals(expectedPos, stack.getInteger("pos"));
		context.assertEquals(extpectedLength, stack.getJsonArray("stack").size());
		
		log("postCommand", "stack > "+  stack.encode());
		return result;
	}

	public JsonObject removeCommand(App app, int count, int expectedPos, int extpectedLength, TestContext context){
		
		
		JsonObject result = delete("/rest/commands/" +app.getId()+"/pop/" + count);
		
		log("removeCommand", "result > " + result.encode());
		context.assertTrue(!result.containsKey("errors"));
		context.assertEquals(expectedPos, result.getInteger("pos"));
		
		JsonObject stack = get("/rest/commands/" + app.getId() + ".json");
		log("removeCommand", "stack > "+  stack.encodePrettily());
		context.assertEquals(expectedPos, stack.getInteger("pos"));
		context.assertEquals(extpectedLength, stack.getJsonArray("stack").size(), "Expectd stack length");
		

		return result;
	}
}
