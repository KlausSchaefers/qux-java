package com.qux.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PreviewEngineTest {
	
	private Logger logger = LoggerFactory.getLogger(PreviewEngineTest.class);
	
	//@Test
	public void test(){
		logger.info("test() > enter");
		
		JsonObject app = new JsonObject();
		
		JsonObject screens = new JsonObject();
		screens.put("s1", new JsonObject().put("id", "s1").put("children", new JsonArray().add("w1").add("w2")).put("props", new JsonObject().put("start", true)));
		screens.put("s2", new JsonObject().put("id", "s2").put("props", new JsonObject()));
		screens.put("s3", new JsonObject().put("id", "s3").put("props", new JsonObject().put("start", false)));
		app.put("screens", screens);
		
		JsonObject widgets = new JsonObject();
		widgets.put("w1", new JsonObject().put("id", "w1"));
		widgets.put("w2", new JsonObject().put("id", "w2"));
		widgets.put("w3", new JsonObject().put("id", "w3"));
		widgets.put("w4", new JsonObject().put("id", "w4"));
		widgets.put("w5", new JsonObject().put("id", "w5"));
		
		app.put("widgets", widgets);
		app.put("lines", new JsonObject());
		app.put("groups", new JsonObject());
		
		
		//System.out.println(app.encodePrettily());
		
		PreviewEngine engine = new PreviewEngine();
		JsonObject app2 = engine.create(app);
		
		System.out.println(app2.encodePrettily());
		
		Assert.assertTrue(!app2.containsKey("lines"));
		Assert.assertTrue(!app2.containsKey("groups"));
		
		Assert.assertTrue(app2.getJsonObject("screens").getJsonObject("s1")!=null);
		Assert.assertTrue(!app2.getJsonObject("screens").containsKey("s2"));
		Assert.assertTrue(!app2.getJsonObject("screens").containsKey("s3"));
		
		Assert.assertTrue(app2.getJsonObject("widgets").containsKey("w1"));
		Assert.assertTrue(app2.getJsonObject("widgets").containsKey("w1"));
		Assert.assertTrue(!app2.getJsonObject("widgets").containsKey("w3"));
		Assert.assertTrue(!app2.getJsonObject("widgets").containsKey("w4"));
		Assert.assertTrue(!app2.getJsonObject("widgets").containsKey("w5"));
		
		
		logger.info("test() > exit");
	}
	
	
	@Test
	public void test2(){
		logger.info("test2() > enter");
		
		JsonObject app = new JsonObject();
		
		JsonObject screens = new JsonObject();
		screens.put("s1", new JsonObject().put("id", "s1")
						.put("children", new JsonArray().add("w1").add("w11"))
						.put("x", 100).put("y", 100));
		screens.put("s2", new JsonObject().put("id", "s2")
						.put("props", new JsonObject().put("start", true))
						.put("parents", new JsonArray().add("s1"))
						.put("children", new JsonArray().add("w2").add("w22"))
						.put("x", 200).put("y", 200));
		
		screens.put("s3", new JsonObject().put("id", "s3").put("props", new JsonObject().put("start", false)));
		app.put("screens", screens);
		
		JsonObject widgets = new JsonObject();
		widgets.put("w1", new JsonObject().put("id", "w1").put("x", 110).put("y", 110));
		widgets.put("w11", new JsonObject().put("id", "w11").put("x", 111).put("y", 111));
		widgets.put("w2", new JsonObject().put("id", "w2").put("x", 220).put("y", 220));
		widgets.put("w22", new JsonObject().put("id", "w22").put("x", 221).put("y", 221));
		widgets.put("w5", new JsonObject().put("id", "w5").put("x", 100).put("y", 100));
		
		app.put("widgets", widgets);
		app.put("lines", new JsonObject());
		app.put("groups", new JsonObject());
		
		
		//System.out.println(app.encodePrettily());
		
		PreviewEngine engine = new PreviewEngine();
		JsonObject app2 = engine.create(app);
		
		System.out.println(app2.encodePrettily());
		
		Assert.assertTrue(!app2.containsKey("lines"));
		Assert.assertTrue(!app2.containsKey("groups"));
		
		Assert.assertTrue(!app2.getJsonObject("screens").containsKey("s1"));
		Assert.assertTrue(app2.getJsonObject("screens").containsKey("s2"));
		Assert.assertTrue(!app2.getJsonObject("screens").containsKey("s3"));
		
		Assert.assertTrue(app2.getJsonObject("widgets").containsKey("w2"));
		Assert.assertTrue(app2.getJsonObject("widgets").containsKey("w22"));
		Assert.assertTrue(app2.getJsonObject("widgets").containsKey("w1@s2"));
		Assert.assertTrue(app2.getJsonObject("widgets").containsKey("w11@s2"));
		
		Assert.assertTrue(!app2.getJsonObject("widgets").containsKey("w1"));
		Assert.assertTrue(!app2.getJsonObject("widgets").containsKey("w11"));
		Assert.assertTrue(!app2.getJsonObject("widgets").containsKey("w5"));
		
		
		logger.info("test() > exit");
	}


}
