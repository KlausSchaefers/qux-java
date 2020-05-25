package com.qux;

import com.qux.model.App;
import com.qux.model.User;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(VertxUnitRunner.class)
public class AnnotationRESTTest extends MatcTestCase {

	
	@Test
	public void test(TestContext context){
		log("test", "enter");
		
		

		
		cleanUp();
		
		deploy(new MATC(), context);
		
		/**
		 * create user & app
		 */
		User klaus = postUser("klaus", context);
		assertLogin(context, klaus, "123456789");
		App app = postApp("klaus_app_public", true, context);
	
	
		postAnnotation(app, "1", "session", "ref1", context);
		postAnnotation(app, "2", "session", "ref1", context);
		
		
		
		log("test", "exit");
	}
	
	
	public JsonObject updateAnnotation(App app, String value, JsonObject anno, TestContext context){
		
		anno = new JsonObject()
			.put("value", value);
		
		
		JsonObject result = post("/rest/annotations/apps/" + app.getId() + "/" + anno.getString("id"), anno);
		
		log("updateAnnotation", " > "+ result);
		
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(result.containsKey("userID"));
		context.assertTrue(result.containsKey("appID"));
		context.assertTrue(result.containsKey("created"));
		context.assertEquals(app.getId(), result.getString("appID"));
		context.assertEquals(value, result.getString("value"));
		return result;
		
	}
	
	public JsonObject postAnnotation(App app, String value, String type, String ref, TestContext context){
		
		JsonObject anno = new JsonObject()
			.put("type", type)
			.put("reference", ref)
			.put("value", value)
			.put("appID", "Evil");
		
		
		JsonObject result = post("/rest/annotations/apps/" + app.getId(), anno);
		
		log("postAnnotatzipon", " > "+ result);
		
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(result.containsKey("userID"));
		context.assertTrue(result.containsKey("appID"));
		context.assertTrue(result.containsKey("created"));
		context.assertEquals(app.getId(), result.getString("appID"));
		
		return result;
		
	}
	
}
