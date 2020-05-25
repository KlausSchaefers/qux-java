package com.qux;

import java.util.List;

import com.qux.acl.Acl;
import com.qux.model.Library;
import com.qux.model.LibraryTeam;
import com.qux.model.User;
import com.qux.util.DB;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class LibraryTest extends MatcTestCase {
	

	
	@Test
	public void test_all_simple(TestContext context){
		log("test_all_simple", "enter");
		
		cleanUp();
		deploy(new MATC(), context);
		
		
		
		JsonObject lib = createLib();
		JsonObject error = post("/rest/libs", lib);
		log("test_SingleUser", "error > "+ error);
		context.assertEquals(error.getInteger("error"), 405);
		
		
		
		/**
		 * create user
		 */
		User klaus = postUser("klaus", context);
		User goran = postUser("goran", context);
		User baake = postUser("baake", context);
		User harry = postUser("harry", context);
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		
		
		JsonObject libCreated = post("/rest/libs", lib);
		context.assertTrue(libCreated.containsKey("id"));
		context.assertTrue(libCreated.containsKey("elements"));
		
		sleep(1000);
		
		List<JsonObject> libs = client.find(DB.getTable(Library.class), LibraryTeam.all());
		context.assertEquals(1, libs.size());
		
		long teamCount = client.count(DB.getTable(LibraryTeam.class), LibraryTeam.all());
		context.assertEquals(1l, teamCount);
		
		
		JsonArray klausLibs = getList("/rest/libs");
		context.assertEquals(1, klausLibs.size());
		
		JsonObject libGet = get("/rest/libs/" + libCreated.getString("_id") + ".json");
		Assert.assertTrue(!libGet.containsKey("error"));
		
		// update
		libGet.getJsonArray("elements").add(new JsonObject().put("name", "symbol"));
		JsonObject libUpdateReponse = post("/rest/libs/" + libCreated.getString("_id") + ".json", libGet);
		Assert.assertTrue(!libUpdateReponse.containsKey("error"));
		
		JsonObject lipUdate = get("/rest/libs/" + libCreated.getString("_id") + ".json");
		Assert.assertTrue(!lipUdate.containsKey("error"));
		Assert.assertEquals(1, lipUdate.getJsonArray("elements").size());
		
		
		// share
		createLibPermission(goran, lipUdate, Acl.READ, context);
		createLibPermission(baake, lipUdate, Acl.WRITE, context);
		
		// check goran, cannot write
		assertLogin(context, "goran@quant-ux.de", "123456789");
		
		JsonObject libGoran = get("/rest/libs/" + libCreated.getString("_id") + ".json");
		Assert.assertTrue(!libGoran.containsKey("error"));
		Assert.assertEquals(1, libGoran.getJsonArray("elements").size());
		
		JsonArray goransLibs = getList("/rest/libs");
		context.assertEquals(1, goransLibs.size());
		
		JsonObject goranUpdateError = post("/rest/libs/" + libCreated.getString("_id") + ".json", libGet);
		Assert.assertTrue(goranUpdateError.containsKey("error"));
		Assert.assertEquals(401, goranUpdateError.getInteger("error").intValue());
		
		// check baake, can write
		assertLogin(context, "baake@quant-ux.de", "123456789");
	
		JsonArray baakesLis = getList("/rest/libs");
		context.assertEquals(1, baakesLis.size());
		
		JsonObject baakeSuccess = post("/rest/libs/" + libCreated.getString("_id") + ".json", libGet);
		Assert.assertFalse(baakeSuccess.containsKey("error"));
		System.out.println(baakeSuccess);
		
		// harry is not allowed
		assertLogin(context, "harry@quant-ux.de", "123456789");
		JsonArray harrysLibs = getList("/rest/libs");
		context.assertEquals(0, harrysLibs.size());
		
		log("test_all_simple", "exit");
	}
	
	public JsonObject createLibPermission(User user, JsonObject lib, int p, TestContext context) {
		JsonObject permission = new JsonObject().put("email", user.getEmail()).put("permission", p);
		JsonObject result = post("/rest/libs/" + lib.getString("id") + "/team/", permission);

		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(!result.containsKey("error"));

		JsonObject mongo_team = client.findOne(lib_team_db, LibraryTeam.findByUserAndLib(user.getId(), lib.getString("id")));
		log("createLibPermission", "findOne(mongo) > " + mongo_team);
		context.assertEquals(mongo_team.getInteger(LibraryTeam.PERMISSION), p, "Wrong permission in db");

		return result;
	}


	private JsonObject createLib() {
		JsonObject lib = new JsonObject();
		lib.put("name", "Lib Test");
		lib.put("elements", new JsonArray());
		return lib;
	}
	


}
