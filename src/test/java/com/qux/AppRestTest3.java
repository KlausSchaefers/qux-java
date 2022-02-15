package com.qux;

import com.qux.model.App;
import com.qux.util.JsonPath;
import com.qux.util.rest.MongoUtil;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class AppRestTest3 extends MatcTestCase {
	
	MongoUtil util = new MongoUtil();
	
	@Test
	public void testConversion(TestContext context){
		log("testConversion", "enter");
		
		cleanUp();
		
		JsonObject rawApp = new JsonObject()
			.put("name", "Klaus")
			.put("description", "lalal")
			.put("screens", new JsonObject())
			.put("widgets", new JsonObject())
			.put("lastUUID", 0);
		
		String appID = client.insert(app_db, rawApp);
		log("testConversion" , "Created app :" + appID);
		
		
			
		
		JsonArray changes = new JsonArray();
		changes.add(createChange("update", "lastUUID", 2));
		changes.add(createChange("add", "grid", new JsonObject().put("x", 10).put("y", 10)));
		changes.add(createChange("add", "s1", new JsonObject().put("x", 10).put("y", 10).put("id", "s1"), "screens"));
		changes.add(createChange("add", "w1", new JsonObject().put("x", 1).put("y", 1).put("id","w1"), "widgets"));	
		JsonObject updateApp = updateChanges(appID, changes);
		
		assertJsonPath(updateApp, "lastUUID", 2, context);
		assertJsonPath(updateApp, "widgets.w1", context);
		assertJsonPath(updateApp, "widgets.w1.x",1, context);
		
		
		
		JsonArray changes2 = new JsonArray();
		changes2.add(createChange("update", "lastUUID", 3));
		changes2.add(createChange("add", "grid", new JsonObject().put("x", 10).put("y", 10)));
		changes2.add(createChange("add", "w1", new JsonObject().put("x", 11).put("y",11).put("id","w1"), "widgets"));	
		changes2.add(createChange("add", "w2", new JsonObject().put("x", 2).put("y", 2).put("id","w2"), "widgets"));	
		updateApp = updateChanges(appID, changes2);

		assertJsonPath(updateApp, "lastUUID", 3, context);
		assertJsonPath(updateApp, "widgets.w1", context);
		assertJsonPath(updateApp, "widgets.w1.x",11, context);
		assertJsonPath(updateApp, "widgets.w2.x",2, context);
		
		
		
		JsonArray changes3 = new JsonArray();
		changes3.add(createChange("update", "lastUUID", 4));
		changes3.add(createChange("add", "grid", new JsonObject().put("x", 10).put("y", 10)));
		changes3.add(createChange("delete", "w1", new JsonObject().put("x", 11).put("y",11).put("id","w1"), "widgets"));	
		changes3.add(createChange("update", "w2", 
				new JsonObject()
					.put("x", 22)
					.put("y", 22)
					.put("id","w2")
					.put("name","Widget 2")
					.put("style", new JsonObject()
						.put("color", 255255255)), 
				"widgets"));	
		changes3.add(createChange("add", "t3", new JsonObject().put("x", 3).put("y", 3).put("id","t3"), "templates"));	
		updateApp = updateChanges(appID, changes3);

		assertJsonPath(updateApp, "lastUUID", 4, context);
		assertJsonPathNull(updateApp, "widgets.w1", context);
		assertJsonPath(updateApp, "widgets.w2.x",22, context);
		assertJsonPath(updateApp, "widgets.w2.style.color",255255255, context);
		assertJsonPath(updateApp, "templates.t3.x",3, context);
		
		log("testConversion", "enter");
	}


	private JsonObject updateChanges(String appID, JsonArray changes) {
		JsonObject update = util.changeToMongo(changes);

		client.update(app_db,appID, update);
		JsonObject updateApp = client.findOne(app_db, App.findById(appID));
		
		debug("updateChanges", "Update : " + update.encodePrettily());
		debug("updateChanges", "App : " + updateApp.encodePrettily());
		
		return updateApp;
	}
	
	
	public void assertJsonPath(JsonObject object, String path, TestContext  context){
		JsonPath jp = new JsonPath(object);
		Object obs = jp.getValue(path);
		context.assertNotNull(obs, "Path '" + path + "' could not be macthed" );
	}
	
	public void assertJsonPathNull(JsonObject object, String path, TestContext  context){
		JsonPath jp = new JsonPath(object);
		Object obs = jp.getValue(path);
		context.assertNull(obs, "Path '" + path + "' could be macthed" );
	}
	
	
	public void assertJsonPath(JsonObject object, String path, int exp, TestContext  context){
		
		JsonPath jp = new JsonPath(object);
		
		int obs = jp.getInteger(path);
		context.assertEquals(exp,obs, "Path '" + path + "' could not be macthed" );
	}

	@Test
	public void testChanges(TestContext context){
		log("testChanges", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		/**
		 * create user
		 */
		postUser("klaus", context);
		
		/**
		 * create klaus apps
		 */
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		App app = postApp("klaus_app_public", true, context);
	
		
		JsonObject rawApp = new JsonObject()
			.put("name", "Klaus")
			.put("description", "lalal")
			.put("screens", new JsonObject())
			.put("widgets", new JsonObject())
			.put("lastUUID", 0);
		
		updateApp(app, rawApp, context);
		
		
		JsonArray changes = new JsonArray();
		changes.add(createChange("update", "lastUUID", 2));
		changes.add(createChange("add", "grid", new JsonObject().put("x", 10).put("y", 10)));
		changes.add(createChange("add", "s1", new JsonObject().put("x", 10).put("y", 10).put("id", "s1"), "screens"));
		changes.add(createChange("add", "w1", new JsonObject().put("x", 1).put("y", 1).put("id","w1"), "widgets"));	
		JsonObject updateApp = postChanges(app, changes, context);
		
		
		assertJsonPath(updateApp, "lastUUID", 2, context);
		assertJsonPath(updateApp, "widgets.w1", context);
		assertJsonPath(updateApp, "widgets.w1.x",1, context);
		
		
		
		JsonArray changes2 = new JsonArray();
		changes2.add(createChange("update", "lastUUID", 3));
		changes2.add(createChange("add", "grid", new JsonObject().put("x", 10).put("y", 10)));
		changes2.add(createChange("add", "w1", new JsonObject().put("x", 11).put("y",11).put("id","w1"), "widgets"));	
		changes2.add(createChange("add", "w2", new JsonObject().put("x", 2).put("y", 2).put("id","w2"), "widgets"));	
		updateApp = postChanges(app, changes2, context);

		assertJsonPath(updateApp, "lastUUID", 3, context);
		assertJsonPath(updateApp, "widgets.w1", context);
		assertJsonPath(updateApp, "widgets.w1.x",11, context);
		assertJsonPath(updateApp, "widgets.w2.x",2, context);
		
	
		
		
		
		JsonArray changes3 = new JsonArray();
		changes3.add(createChange("update", "lastUUID", 4));
		changes3.add(createChange("add", "grid", new JsonObject().put("x", 10).put("y", 10)));
		changes3.add(createChange("delete", "w1", new JsonObject().put("x", 11).put("y",11).put("id","w1"), "widgets"));	
		changes3.add(createChange("update", "w2", 
				new JsonObject()
					.put("x", 22)
					.put("y", 22)
					.put("id","w2")
					.put("name","Widget 2")
					.put("style", new JsonObject()
						.put("color", 255255255)), 
				"widgets"));	
		changes3.add(createChange("add", "t3", new JsonObject().put("x", 3).put("y", 3).put("id","t3"), "templates"));	
		updateApp = postChanges(app, changes3, context);

		assertJsonPath(updateApp, "lastUUID", 4, context);
		assertJsonPathNull(updateApp, "widgets.w1", context);
		assertJsonPath(updateApp, "widgets.w2.x",22, context);
		assertJsonPath(updateApp, "widgets.w2.style.color",255255255, context);
		assertJsonPath(updateApp, "templates.t3.x",3, context);

		log("testChanges", "exit");
	}


	private JsonObject postChanges(App app, JsonArray changes, TestContext context) {
	
		JsonObject result = post("/rest/apps/" +app.getId() + "/update", changes);		
		context.assertTrue(!result.containsKey("error"), "Error contained");
		context.assertEquals("app.changes.succcess", result.getString("details"));
		
		JsonObject updateApp = client.findOne(app_db, App.findById(app.getId()));
		
		debug("postChanges", result.encode());
		log("postChanges", "App : " + updateApp.encodePrettily());
		
		return updateApp;
	}
	
	
	public JsonObject createChange(String type,String name, JsonObject newValue){
		return new JsonObject()
			.put("type", type)
			.put("name", name)
			.put("object", newValue);
	}
	
	public JsonObject createChange(String type,String name, JsonObject newValue,  String parent){
		return new JsonObject()
			.put("type", type)
			.put("parent", parent)
			.put("name", name)
			.put("object", newValue);
	}

	public JsonObject createChange(String type, String name,String newValue){
		return new JsonObject()
			.put("type", type)
			.put("name", name)
			.put("object", newValue);
	}
	
	
	public JsonObject createChange(String type, String name,int newValue){
		return new JsonObject()
			.put("type", type)
			.put("name", name)
			.put("object", newValue);
	}
}
