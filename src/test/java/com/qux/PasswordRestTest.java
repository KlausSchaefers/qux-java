package com.qux;

import com.qux.model.User;
import com.qux.util.DB;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class PasswordRestTest extends MatcTestCase{
	
	

	@Test
	public void test_SingleUser(TestContext context){
		log("test_SingleUser", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		
		/**
		 * create user
		 */
		User klaus = postUser("klaus", "klaus.schaefers@gmail.com", context);
		log("test_SingleUser", klaus.toString());
				
		assertLogin(context, "klaus.schaefers@gmail.com", "123456789");

		logout();
		
		/**
		 * Now request password
		 */
		JsonObject req = new JsonObject();
		req.put("email","klaus.schaefers@gmail.com");
		
		JsonObject res = post("/rest/user/password/request", req);
		context.assertTrue(!res.containsKey("error"));
		context.assertTrue(!res.containsKey("errors"));
		debug("test_singleUser", res.encode());
		
		JsonObject dbUser= this.client.findOne(DB.getTable(User.class), User.findByEmail("klaus.schaefers@gmail.com"));
		log("rest_SingleUser", dbUser.encodePrettily());
		context.assertTrue(dbUser.containsKey("passwordResetCount"));
		context.assertTrue(dbUser.containsKey("passwordRestKey"));
		
		
		/**
		 * Try to guess the password
		 */
		req = new JsonObject();
		req.put("email","klaus.schaefers@gmail.com");
		req.put("key","sadfasdfaw");
		req.put("password","abcabc");		
		JsonObject err= post("/rest/user/password/set", req);
		log("", err.encode());
		context.assertTrue(err.containsKey("errors"));
		
		
		
		/**
		 * Now reset the password
		 */
		req = new JsonObject();
		req.put("email","klaus.schaefers@gmail.com");
		req.put("key",dbUser.getString("passwordRestKey"));
		req.put("password","abcabc");
		
		JsonObject res2= post("/rest/user/password/set", req);
		log(" ->", res2.encode());
		context.assertTrue(!res2.containsKey("error"));
		context.assertTrue(!res.containsKey("errors"));
		
		
		assertLoginError(context, "klaus.schaefers@gmail.com", "123456789");
		
		/**
		 * check if we cab login with the new password
		 */
		assertLogin(context, "klaus.schaefers@gmail.com", "abcabc");
		
		
		dbUser= this.client.findOne(DB.getTable(User.class), User.findByEmail("klaus.schaefers@gmail.com"));
		log("rest_SingleUser", dbUser.encodePrettily());
		context.assertTrue(dbUser.containsKey("passwordResetCount"));
		context.assertTrue(!dbUser.containsKey("passwordRestKey"));
		context.assertTrue(dbUser.containsKey("failedLoginAttempts"));
		
		log("test_SingleUser", "exit");
	}

}
