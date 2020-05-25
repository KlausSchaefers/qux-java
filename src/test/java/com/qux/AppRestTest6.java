package com.qux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.qux.model.User;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class AppRestTest6 extends MatcTestCase {
	
	/**
	 * Test here that stupid % are correctly escaped, so 
	 * we have a fix for these kind of issues
	 * @param context
	 * @throws IOException
	 */
	@Test
	public void testBadEncoding(TestContext context) throws IOException{
		log("testBadEncoding", "enter");
		
		cleanUp();
	
		deploy(new MATC(), context);

		User klaus = postUser("klaus", context);
		assertLogin(context, klaus, "123456789");
		
		String contents = new String(Files.readAllBytes(Paths.get("src/test/resources/apps/basencoding_bug.js"))); 
		JsonObject app = new JsonObject(contents);
		
		context.assertTrue(app.encode().contains("%"), "No % contained");
		
		app.remove("id");
		app.remove("_id");
		JsonObject savedApp = post("/rest/apps", app);

		System.out.println(savedApp.getString("id"));
		
		JsonObject copy = post("/rest/apps/copy/"+ savedApp.getString("id"), new JsonObject().put("name", "klaus_app_public_copy"));
		context.assertNotEquals(savedApp.getString("id"), copy.getString("id"));
		
		copy = get("/rest/apps/" + copy.getString("id") + ".json");
		context.assertTrue(!copy.containsKey("error"));
		context.assertTrue(!copy.containsKey("errors"));
		context.assertTrue(!copy.encode().contains("%"), "% contained in copy");
		context.assertTrue(copy.encode().contains("$perc;"), "NO $perc; contained in copy");
		
		int countApp = countMatches(Pattern.compile("%"), app.encode());
		int copyCount = countMatches(Pattern.compile("\\$perc;"), copy.encode());
		
		context.assertEquals(countApp, copyCount, "Counts not the same");
		
		
		log("testBadEncoding", "exit");
	}
	
	static int countMatches(Pattern pattern, String string)
	{
	    Matcher matcher = pattern.matcher(string);

	    int count = 0;
	    int pos = 0;
	    while (matcher.find(pos))
	    {
	        count++;
	        pos = matcher.start() + 1;
	    }

	    return count;
	}

	
	
}
