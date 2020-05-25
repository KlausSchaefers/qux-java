package com.qux;

import com.qux.model.User;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class UserRESTTestCase extends MatcTestCase {

	@Test
	public void testDefaultUser(TestContext context){
		log("testDefaultUser", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
		

		JsonObject user = get("/rest/user");
		System.out.println(user);
		context.assertNotNull(user);
		context.assertEquals(User.GUEST, user.getString("role"));
		
	}

	@Test
	public void test(TestContext context){
		log("test", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
		

		JsonObject user = get("/rest/user");
		System.out.println(user);
		context.assertNotNull(user);
		context.assertEquals(User.GUEST, user.getString("role"));

		
		User klaus = createUser("Klaus");
		klaus.setPassword("123456789");
		
		JsonObject result = post("/rest/user", klaus);
		log("test", "post > " + result);
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("password"));
		context.assertEquals("klaus@quant-ux.de", result.getString("email"));
		
		List<JsonObject> users = client.find(user_db, User.findById(result.getString("_id")));
		//context.assertEquals(1, users.size());
		
		result = users.get(0);
		log("test", "find(id) > " + result);
		
		
		/**
		 * now try too create the user again!
		 */
		User klaus2 = createUser("klaus");
		klaus2.setPassword("123");
		result = post("/rest/user", klaus2);
		log("test", "post2 > " + result);
		context.assertTrue(result.containsKey("errors"));
		context.assertTrue(result.getJsonArray("errors").contains("user.password.invalid"));
		context.assertTrue(result.getJsonArray("errors").contains("user.email.not.unique"));
		
		
		User dennis = createUser("DeNNis");
		dennis.setPassword("123123");
		result = post("/rest/user", dennis);
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("password"));
		context.assertEquals("dennis@quant-ux.de", result.getString("email"));
		
		users = client.find(user_db, User.all());
		//context.assertEquals(3, users.size());
		
		log("test", "exit");
	}
	
	
	@Test
	public void test2(TestContext context){
		log("test2", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		
		User klaus = createUser("Klaus");
		klaus.setPassword("123456789");
		
		JsonObject result = post("/rest/user", klaus);
		log("test2", "create > " + result);
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("password"));
		context.assertEquals("klaus@quant-ux.de", result.getString("email"));
		
		
		/**
		 * login
		 */
		log("test2", "Login");
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		assertLoginError(context, "klaus@quant-ux.com", "123456789");
		assertLoginError(context, "klaus@quant-ux.de", "sdfsdfdsf");
		assertLogin(context, "klaus@quant-ux.de", "123456789");
	
		/**
		 * get current user
		 */
		result = get("/rest/user/");
		log("test", "get(current) > " + result);
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("password"));
		context.assertEquals("klaus@quant-ux.de", result.getString("email"));
		
		/**
		 * get user
		 */
		result = get("/rest/user/" + result.getString("id") + ".json");
		log("test", "get(id) > " + result);
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("password"));
		context.assertEquals("klaus@quant-ux.de", result.getString("email"));
		
		
		/**
		 * partical update
		 */
		JsonObject update = new JsonObject()
			.put("name", "Klaus2")
			.put("lastname", "Schaefers");
		
		log("test", "partial update : "+ result.getString("id"));
		JsonObject result2 = post("/rest/user/" + result.getString("id") + ".json", update);
		log("test", "update(id) > " + result2);
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("password"));
		
		/**
		 * make sure all props are updated
		 */
		context.assertEquals("Klaus2", result2.getString("name"));
		context.assertEquals("Schaefers", result2.getString("lastname"));
		
		/**
		 * make sure other stuff is still there!
		 */
		context.assertEquals("klaus@quant-ux.de", result2.getString("email"));
		
		/**
		 * Attention there is also an admin
		 */
		List<JsonObject> users = client.find(user_db, User.all());
		context.assertTrue(1 <= users.size(), "User count not +1");
		
		
		/**
		 * now change password
		 */
		update = new JsonObject()
			.put("name", "Klaus3")
			.put("lastname", "Schaefers3")
			.put("password", "abcdefg");
	
		result2 = post("/rest/user/" + result.getString("id") + ".json", update);
		log("test", "update(id)2 > " + result2);
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("password"));
		
		/**
		 * Login
		 */
		assertLogin(context, "klaus@quant-ux.de", "abcdefg");
		assertLoginError(context, "klaus@quant-ux.de", "123456789");
	
		/**
		 * logout
		 */
		logout();

		
		/**
		 * change payed
		 */
		JsonObject error = post("/rest/user/" + result.getString("id") + ".json", update);
		System.out.println("Get payed " + error.encode());
		log("test", "update(id)3  > Error: " + error);
		context.assertEquals(error.getInteger("error"), 401);
		
		
		
		
		log("test2", "exit");
		
	}
	
	@Test
	public void test3(TestContext context){
		log("test3", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		
		User klaus = createUser("Klaus");
		klaus.setPassword("123456789");
		
		JsonObject result = post("/rest/user", klaus);
		log("test3", "create > " + result);
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("password"));
		context.assertEquals("klaus@quant-ux.de", result.getString("email"));
		
		
		/**
		 * login
		 */
		log("test3", "Login");
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		assertLoginError(context, "klaus@quant-ux.com", "123456789");
		assertLoginError(context, "klaus@quant-ux.de", "sdfsdfdsf");
		assertLogin(context, "klaus@quant-ux.de", "123456789");
	
		/**
		 * get current user
		 */
		result = get("/rest/user/");
		log("test3", "get(current) > " + result);
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("password"));
		context.assertEquals("klaus@quant-ux.de", result.getString("email"));
	
		/**
		 * logout
		 */
		JsonObject logout = delete("/rest/login");
		logout();
		log(-1,"test3", "logout > " + logout);
		System.out.println("----------------------------");
		
		
		
		
		//cookieStore.clear();
		
		result = get("/rest/user/");
		log("test3", "get(current) > " + result);
		context.assertEquals("guest@quant-ux.com", result.getString("email"));
		
		
		log("test3", "exit");
		
	}
	
	@Test
	public void testRetire(TestContext context){
		log("testRetire", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
		User klaus = postUser("klaus", context);
		log("testRetire", klaus.toString());
	
		/**
		 * Make sure we cannot retire guest
		 */
		JsonObject response = this.get("/rest/retire");
		context.assertTrue(response.containsKey("error"), "Guest can rtire");
		
		/**
		 * now login and retire
		 */
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		this.get("/rest/retire");
		logout();
	
		/**
		 * make sure user is logged out
		 */
		JsonObject guest = get("/rest/user/");
		context.assertEquals(guest.getString("role"), User.GUEST, "Not guest after retire");
		
		/**
		 * We can not login anymore
		 */
		assertLoginError(context, "klaus@quant-ux.de", "123456789");
		
		log("test", "exit");
	}

	
	

}
