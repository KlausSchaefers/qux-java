package com.qux;

import com.qux.model.App;
import com.qux.model.User;
import com.qux.util.MongoUtil;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class AppRestTest5 extends MatcTestCase {
	
	MongoUtil util = new MongoUtil();
	

	@Test
	public void testList(TestContext context){
		log("testList", "enter");
		
		cleanUp();
		

		deploy(new MATC(), context);

		User klaus = postUser("klaus", context);
		assertLogin(context, klaus, "123456789");
		
		
		
		/**
		 * Now add an images
		 */
		App app = postApp("klaus_app_public", true, context);
		JsonObject fullApp = get("/rest/apps/"+ app.getId() + ".json");		
		addScreen(fullApp);
		addWidget(fullApp);		
		post("/rest/apps/"+ app.getId() + ".json", fullApp);
	
		
		App app2 = postApp("klaus_app_public_2", true, context);
		JsonObject fullApp2 = get("/rest/apps/"+ app.getId() + ".json");		
		addScreen(fullApp2);
		addWidget(fullApp2);		
		post("/rest/apps/"+ app2.getId() + ".json", fullApp);
		
		App app3 = postApp("klaus_app_public_3", true, context);
		JsonObject fullApp3 = get("/rest/apps/"+ app.getId() + ".json");		
		addScreen(fullApp3);
		addWidget(fullApp3);		
		post("/rest/apps/"+ app3.getId() + ".json", fullApp);
		
		JsonArray klaus_apps = getList("/rest/apps/");
		log("testList", "klaus_apps > "+ klaus_apps.size());
		context.assertEquals(3, klaus_apps.size());
		
		/**
		 * Test here the new API
		 */
		JsonObject klaus_apps2 = get("/rest/apps?paging=true");
		context.assertEquals(3, klaus_apps2.getInteger("size"));
		context.assertEquals(0, klaus_apps2.getInteger("offset"));
		context.assertEquals(3, klaus_apps2.getJsonArray("rows").size());
		
		log("testList", "exit");
	}

	
	private void addScreen(JsonObject fullApp) {
		JsonObject screens = new JsonObject();
		fullApp.put("screens", screens);
		
		
		JsonObject screenStyle = new JsonObject();
				
				
		JsonObject screen = new JsonObject()
				.put("id", "s10000")
				.put("name", "Screen 1")
				.put("style", screenStyle);

		screens.put("s10000", screen);
	}


	private void addWidget(JsonObject fullApp) {
		JsonObject widgets = new JsonObject();
		fullApp.put("widgets", widgets);
		
		
		JsonObject screenStyle = new JsonObject();
				
				
		JsonObject widget = new JsonObject()
				.put("id", "w10001")
				.put("name", "Widget 1")
				.put("style", screenStyle);

		widgets.put("w10001", widget);
	}
}
