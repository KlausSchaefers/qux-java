package com.qux;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class StatusRestRest extends MatcTestCase{


	@Test
	public void test(TestContext context){
		log("test", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);

		JsonObject status = get("/rest/status.json");
		context.assertNotNull(status);
		System.out.println(status.encode());
		context.assertEquals(MATC.VERSION, status.getString("version"));
	}
}
