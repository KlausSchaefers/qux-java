package com.qux;

import com.qux.model.App;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;

//@RunWith(VertxUnitRunner.class)
public class CommandStackRestTest2 extends MatcTestCase{
	
	//@Test
	public void xtest_Command(TestContext context){
		log("test_Command", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		postUser("klaus", context);
		
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		App app = postApp("klaus_app_public", true, context);
		
	
		log("test_Command", "Created App > "+ app.getId());
		assertStack(app, context);
		
		
		for(int i=1; i< 10000; i++){
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
	
}
