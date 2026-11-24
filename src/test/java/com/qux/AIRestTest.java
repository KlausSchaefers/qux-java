package com.qux;

import com.qux.model.App;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The AI API (/rest/ai) is for agents: it takes an agent token and refuses the
 * JWT of the web app. The tokens themselves (PAT, OAuth) are tested in
 * AgentAuthTest.
 */
@RunWith(VertxUnitRunner.class)
public class AIRestTest extends MatcTestCase {

	@Test
	public void test_getApp(TestContext context) {
		log("test_getApp", "enter");

		cleanUp();
		deploy(new MATC(), context);

		postUser("klaus", context);
		assertLogin(context, "klaus@quant-ux.de", "123456789");

		App app = postApp("klaus_app", false, context);
		// AppREST saves the owner (Team) after it answered
		sleep(300);
		String token = createToken();
		String jwt = getJWT();

		/**
		 * The app comes without any private fields
		 */
		getApp(token, app.getId(), context);

		/**
		 * The JWT of the web app is not for the AI API, even of the owner
		 */
		context.assertEquals(401, getStatus(jwt, "/rest/ai/apps/" + app.getId() + ".json"));

		/**
		 * Without a token there is nothing
		 */
		logout();
		getAppError(null, app.getId(), context);
		context.assertEquals(401, getStatus(null, "/rest/ai/apps/" + app.getId() + ".json"));

		log("test_getApp", "exit");
	}

	@Test
	public void test_hack(TestContext context) {
		log("test_hack", "enter");

		cleanUp();
		deploy(new MATC(), context);

		postUser("klaus", context);
		postUser("bernd", context);

		assertLogin(context, "bernd@quant-ux.de", "123456789");
		App berndApp = postApp("bernd_app", false, context);

		assertLogin(context, "klaus@quant-ux.de", "123456789");
		postApp("klaus_app", true, context);
		sleep(300);
		String token = createToken();

		/**
		 * The app of another user, and guessed / forged ids, must not
		 * resolve to an app.
		 */
		getAppError(token, berndApp.getId(), context);
		getAppError(token, "i-am-not-a-valid-id", context);
		getAppError(token, "' OR '1'='1", context);
		getAppError(token, "{\"$gt\": \"\"}", context);

		log("test_hack", "exit");
	}

	public JsonObject getApp(String token, String appID, TestContext context) {
		JsonObject result = get(token, "/rest/ai/apps/" + appID + ".json");
		log("getApp", "" + result);

		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(!result.containsKey("errors"));

		context.assertTrue(!result.containsKey("users"));
		context.assertTrue(!result.containsKey("invitations"));

		context.assertEquals(appID, result.getString("id"));
		return result;
	}

	public JsonObject getAppError(String token, String appID, TestContext context) {
		JsonObject result = get(token, "/rest/ai/apps/" + encode(appID) + ".json");
		log("getAppError", "" + result);
		context.assertTrue(result.containsKey("error") || result.containsKey("errors"));
		return result;
	}

	/**
	 * An agent token of the logged in user (PAT), read only.
	 */
	private String createToken() {
		return post("/rest/agent-tokens.json", new JsonObject().put("name", "AIRestTest")).getString("token");
	}

	private JsonObject get(String bearer, String path) {
		String body = request(bearer, path)[1];
		return body.isEmpty() ? new JsonObject() : new JsonObject(body);
	}

	private int getStatus(String bearer, String path) {
		return Integer.parseInt(request(bearer, path)[0]);
	}

	/**
	 * A GET with an own Authorization header (the helpers of the test case
	 * send the JWT of the login).
	 *
	 * @return the status and the body
	 */
	private String[] request(String bearer, String path) {
		try {
			java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL("http://localhost:" + conf.getInteger("http.port") + path).openConnection();
			if (bearer != null) {
				con.setRequestProperty("Authorization", "Bearer " + bearer);
			}
			int status = con.getResponseCode();
			java.io.InputStream is = status < 400 ? con.getInputStream() : con.getErrorStream();
			String body = is != null ? new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) : "";
			return new String[] { "" + status, body };
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String encode(String value) {
		try {
			return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
		} catch (java.io.UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

}
