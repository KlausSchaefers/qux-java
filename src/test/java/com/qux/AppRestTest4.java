package com.qux;

import java.io.InputStream;
import java.util.Set;

import javax.imageio.ImageIO;

import com.qux.model.App;
import com.qux.model.Image;
import com.qux.model.User;
import com.qux.util.rest.MongoUtil;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class AppRestTest4 extends MatcTestCase {
	
	MongoUtil util = new MongoUtil();
	

	
	@Test
	public void testAppCopy2(TestContext context){
		log("testAppCopy2", "enter");
		
		cleanUp();
		
		context.assertEquals("456/image.png", Image.replaceAppIDinImageUrl("123/image.png", "456"));
		
		log("testAppCopy2", "enter");
	}
	
	@Test
	public void testAppCopy(TestContext context){
		log("testAppCopy", "enter");
		
		cleanUp();
		deploy(new MATC(), context);

		User klaus = postUser("klaus", context);
		assertLogin(context, klaus, "123456789");
		App app = postApp("klaus_app_public", true, context);
		
		
		assertList("/rest/images/" + app.getId() + ".json", 0, context);
		postImage(app, context, "test.png");
		JsonArray images = assertList("/rest/images/" + app.getId() + ".json", 1, context);

		
		/**
		 * Now add an images
		 */
		JsonObject fullApp = get("/rest/apps/"+ app.getId() + ".json");		
		addScreen(images, fullApp);
		addWidget(images, fullApp);		
		post("/rest/apps/"+ app.getId() + ".json", fullApp);
	
		
		
		/**
		 * We call copy. That will give me the new id
		 */
		JsonObject result = post("/rest/apps/copy/"+ app.getId() , new JsonObject().put("name", "klaus_app_public_copy"));

		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(result.containsKey("id"));
		context.assertNotEquals(app.getId(), result.getString("id"), "No new id");

		/**
		 * Sleep a little so all the async stuff can take place
		 */
		sleep(100);
		
		String newId = result.getString("id");
		/**
		 * Load the copy
		 */
		JsonObject newApp = get("/rest/apps/"+ newId+ ".json");
		
		/**
		 * Check if properties are updates
		 */
		context.assertTrue(!newApp.containsKey("invitations"));
		context.assertEquals(newApp.getString("name"), "klaus_app_public_copy");
		context.assertEquals(false, newApp.getBoolean("isPublic"));
		context.assertEquals(app.getId(), newApp.getString("parent"));
		context.assertTrue(app.getLastUpdate() <  newApp.getLong("lastUpdate"));
		
		
		assertList("/rest/images/" + newId + ".json", 1, context);
		
		/**
		 * Check all images
		 */
		this.assertBackgroundImageUrls(newApp.getJsonObject("screens"), newId, context);		
		this.assertBackgroundImageUrls(newApp.getJsonObject("widgets"), newId, context);
		
		log("testAppCopy", "enter");
	}

	private void assertBackgroundImageUrls(JsonObject boxes, String appID, TestContext context) {
		Set<String> ids = boxes.fieldNames();
		for(String id : ids){
			JsonObject box = boxes.getJsonObject(id);
			if(box.containsKey("style")){
				JsonObject style = box.getJsonObject("style");
				if(style.containsKey("backgroundImage")){
					JsonObject backgroundImage = style.getJsonObject("backgroundImage");
					String url = backgroundImage.getString("url");
					context.assertTrue(url.startsWith(appID), "Wrong image url "+ url);
					
					try{
						InputStream is = getRaw("/rest/images/" + url + "?token=" + this.getJWT());
						context.assertTrue(is !=null);						
						ImageIO.read(is);					
						is.close();
					} catch(Exception e){
						context.fail("Could not load image "+ url);
					}
					
					System.out.println(" - " + url);
				}
			}
		}
	}
	
	private void addScreen(JsonArray images, JsonObject fullApp) {
		JsonObject screens = new JsonObject();
		fullApp.put("screens", screens);
		
		JsonObject image = images.getJsonObject(0);
		
		JsonObject backgroundImage = new JsonObject()
				.put("w", image.getInteger("w"))
				.put("h", image.getInteger("h"))
				.put("url", image.getString("url"));
		
		JsonObject screenStyle = new JsonObject()
				.put("backgroundImage", backgroundImage);
				
				
		JsonObject screen = new JsonObject()
				.put("id", "s10000")
				.put("name", "Screen 1")
				.put("style", screenStyle);

		screens.put("s10000", screen);
	}


	private void addWidget(JsonArray images, JsonObject fullApp) {
		JsonObject widgets = new JsonObject();
		fullApp.put("widgets", widgets);
		
		JsonObject image = images.getJsonObject(0);
		
		JsonObject backgroundImage = new JsonObject()
				.put("w", image.getInteger("w"))
				.put("h", image.getInteger("h"))
				.put("url", image.getString("url"));
		
		JsonObject screenStyle = new JsonObject()
				.put("backgroundImage", backgroundImage);
				
				
		JsonObject widget = new JsonObject()
				.put("id", "w10001")
				.put("name", "Widget 1")
				.put("style", screenStyle);

		widgets.put("w10001", widget);
	}
}
