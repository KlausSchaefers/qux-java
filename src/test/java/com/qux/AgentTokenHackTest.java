package com.qux;

import com.qux.acl.Acl;
import com.qux.model.*;
import com.qux.util.DB;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Attacks on the agent tokens: a token for an app the user has no access to,
 * and a legit token on the endpoints of the web app.
 */
@RunWith(VertxUnitRunner.class)
public class AgentTokenHackTest extends MatcTestCase {

	private final java.util.List<String> mismatches = new java.util.ArrayList<>();

	/********************************************************************************************
	 * A token for an app of someone else
	 ********************************************************************************************/

	/**
	 * Klaus puts the app of bernd into the appIDs of his token. Whatever the
	 * server does when issuing it, the token must never see or change the app
	 * of bernd: appIDs only restricts, the Team ACL still decides.
	 */
	@Test
	public void test_token_for_foreign_app_is_useless(TestContext context) {
		log("test_token_for_foreign_app_is_useless", "enter");
		cleanUpAgents();
		deployOwn(context);

		User bernd = postUser("bernd", context);
		assertLogin(context, bernd.getEmail(), "123456789");
		App berndApp = postApp("bernd_app", false, context);
		logout();

		User klaus = postUser("klaus", context);
		assertLogin(context, klaus.getEmail(), "123456789");
		App klausApp = postApp("klaus_app", false, context);
		String jwt = getJWT();

		// only the app of bernd, and with write access
		String onlyBernd = issue(jwt, "Only bernd", new JsonArray().add(berndApp.getId()));
		// the app of bernd next to an own one
		String both = issue(jwt, "Both", new JsonArray().add(klausApp.getId()).add(berndApp.getId()));

		for (String token : new String[] { onlyBernd, both }) {
			if (token == null) {
				continue;
			}
			Response read = request("GET", "/rest/ai/apps/" + berndApp.getId() + ".json", token, null, null);
			context.assertEquals(403, read.status, read.body);
			Response write = request("POST", "/rest/ai/apps/" + berndApp.getId() + "/screens.json", token, "application/json", fragment("s1", "w1").encode());
			context.assertEquals(403, write.status, write.body);

			Response apps = request("GET", "/rest/ai/apps.json", token, null, null);
			context.assertEquals(200, apps.status);
			context.assertFalse(apps.body.contains(berndApp.getId()));
		}

		// the app of klaus works with the token that names it
		if (both != null) {
			context.assertEquals(200, request("GET", "/rest/ai/apps/" + klausApp.getId() + ".json", both, null, null).status);
		}

		// the app of bernd has no screen
		JsonObject mongoApp = client.findOne(app_db, App.findById(berndApp.getId()));
		context.assertTrue(mongoApp.getJsonObject("screens", new JsonObject()).isEmpty());

		log("test_token_for_foreign_app_is_useless", "exit");
	}

	/**
	 * A token that names an app of someone else should not be issued at all.
	 */
	@Test
	public void test_token_for_foreign_app_is_not_issued(TestContext context) {
		log("test_token_for_foreign_app_is_not_issued", "enter");
		cleanUpAgents();
		deployOwn(context);

		User bernd = postUser("bernd", context);
		assertLogin(context, bernd.getEmail(), "123456789");
		App berndApp = postApp("bernd_app", false, context);
		logout();

		User klaus = postUser("klaus", context);
		assertLogin(context, klaus.getEmail(), "123456789");

		Response created = request("POST", "/rest/agent-tokens.json", getJWT(), "application/json",
				new JsonObject().put("name", "Bernd").put("appIDs", new JsonArray().add(berndApp.getId())).encode());
		context.assertTrue(created.status == 400 || created.status == 403,
				"A token was issued for an app the user has no access to: " + created.status + " " + created.body);
		context.assertEquals(0L, client.count(DB.getTable(AgentToken.class), new JsonObject().put(AgentToken.USER_ID, klaus.getId())));

		log("test_token_for_foreign_app_is_not_issued", "exit");
	}

	/**
	 * The same through the OAuth login: the user cannot approve an agent for
	 * an app of someone else on the consent page.
	 */
	@Test
	public void test_oauth_approve_for_foreign_app(TestContext context) throws Exception {
		log("test_oauth_approve_for_foreign_app", "enter");
		cleanUpAgents();
		deployOwn(context);

		User bernd = postUser("bernd", context);
		assertLogin(context, bernd.getEmail(), "123456789");
		App berndApp = postApp("bernd_app", false, context);
		sleep(300);
		logout();

		User klaus = postUser("klaus", context);
		assertLogin(context, klaus.getEmail(), "123456789");
		App own = postApp("klaus_app", false, context);
		sleep(300);
		String jwt = getJWT();

		String redirect = "http://127.0.0.1:53111/callback";
		String clientID = new JsonObject(request("POST", "/oauth/register", null, "application/json", new JsonObject()
				.put("client_name", "Claude Code")
				.put("redirect_uris", new JsonArray().add(redirect))
				.encode()).body).getString("client_id");

		String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk-0123456789";
		String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
				MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
		String authorize = "/oauth/authorize?response_type=code&client_id=" + URLEncoder.encode(clientID, "UTF-8")
				+ "&redirect_uri=" + URLEncoder.encode(redirect, "UTF-8")
				+ "&code_challenge=" + challenge + "&code_challenge_method=S256&state=s1&scope=" + URLEncoder.encode("apps:read apps:write", "UTF-8");

		// an app of bernd, alone or next to an own one
		for (JsonArray appIDs : new JsonArray[] { new JsonArray().add(berndApp.getId()), new JsonArray().add(own.getId()).add(berndApp.getId()) }) {
			String requestID = requestID(request("GET", authorize, null, null, null));
			Response approve = request("POST", "/oauth/requests/" + requestID + "/approve", jwt, "application/json", new JsonObject().put("appIDs", appIDs).encode());
			context.assertEquals(403, approve.status, approve.body);
			context.assertFalse(approve.body.contains("code="), "A code was issued: " + approve.body);

			// the request is still open: the right apps can be approved
			if (appIDs.size() == 2) {
				Response ok = request("POST", "/oauth/requests/" + requestID + "/approve", jwt, "application/json",
						new JsonObject().put("appIDs", new JsonArray().add(own.getId())).encode());
				context.assertEquals(200, ok.status, ok.body);
				context.assertTrue(ok.body.contains("code="));
			}
		}
		context.assertEquals(0L, client.count(agentToken_db, Model.all()));

		log("test_oauth_approve_for_foreign_app", "exit");
	}

	private static String requestID(Response authorize) {
		return authorize.location.substring(authorize.location.indexOf("request=") + 8);
	}

	/********************************************************************************************
	 * Writing to the apps of someone else
	 ********************************************************************************************/

	/**
	 * Klaus has a token that may write (apps:write) and is not restricted to
	 * apps. He still must not change a private app of bernd, a public app of
	 * bernd, or an app of bernd that he may only read: neither the screens
	 * nor the images.
	 */
	@Test
	public void test_write_to_apps_of_others(TestContext context) {
		log("test_write_to_apps_of_others", "enter");
		cleanUpAgents();
		deployOwn(context);

		User bernd = postUser("bernd", context);
		assertLogin(context, bernd.getEmail(), "123456789");
		App priv = postApp("bernd_private", false, context);
		App pub = postApp("bernd_public", true, context);
		App shared = postApp("bernd_shared", false, context);
		// AppREST saves the owner (Team) after it answered
		sleep(300);
		logout();

		User klaus = postUser("klaus", context);
		// klaus may only read the shared app
		client.save(team_db, Team.create(klaus, shared.getId(), Acl.READ));
		assertLogin(context, klaus.getEmail(), "123456789");
		App own = postApp("klaus_app", false, context);
		sleep(300);
		String token = issue(getJWT(), "Writer", null, new JsonArray().add("apps:read").add("apps:write"));
		context.assertNotNull(token);
		logout();

		for (App app : new App[] { priv, pub, shared }) {
			String name = app.getName();
			Response screens = request("POST", "/rest/ai/apps/" + app.getId() + "/screens.json", token, "application/json", fragment("s1", "w1").encode());
			context.assertEquals(403, screens.status, name + " screens > " + screens.status + " " + screens.body);

			Response image = upload("/rest/ai/apps/" + app.getId() + "/images.json", token);
			context.assertTrue(image.status >= 400, name + " image > " + image.status + " " + image.body);

			JsonObject stored = client.findOne(app_db, App.findById(app.getId()));
			context.assertTrue(stored.getJsonObject("screens", new JsonObject()).isEmpty(), name + " got a screen");
			context.assertTrue(stored.getJsonObject("widgets", new JsonObject()).isEmpty(), name + " got a widget");
		}
		context.assertEquals(0L, client.count(image_db, Model.all()));

		// control: the ACL lets the same upload to an own app through (403 is what it answers when it refuses)
		Response ownImage = upload("/rest/ai/apps/" + own.getId() + "/images.json", token);
		context.assertTrue(ownImage.status != 405 && ownImage.status != 403 && ownImage.status != 401, "own image > " + ownImage.status + " " + ownImage.body);

		// the private app of bernd is no more readable than writable
		context.assertEquals(403, request("GET", "/rest/ai/apps/" + priv.getId() + ".json", token, null, null).status);

		// and the token still writes to the app of klaus
		context.assertEquals(200, request("POST", "/rest/ai/apps/" + own.getId() + "/screens.json", token, "application/json", fragment("s1", "w1").encode()).status);

		log("test_write_to_apps_of_others", "exit");
	}
	/********************************************************************************************
	 * Helper
	 ********************************************************************************************/

	/**
	 * The endpoint of the web app does not know agent tokens: a token is no JWT,
	 * so the caller is a guest and gets a 401. The body must not give the user away.
	 */
	private void denied(TestContext context, String token, String method, String path, String body) {
		denied(context, token, method, path, body, 401);
	}

	/**
	 * @param status what the web app answers a guest on this route
	 */
	private void denied(TestContext context, String token, String method, String path, String body, int status) {
		Response res = request(method, path, token, body != null ? "application/json" : null, body);
		if (res.status != status) {
			mismatches.add(method + " " + path + " > " + res.status + ", expected " + status + ": " + res.body);
		}
		if (res.body.contains("klaus@") || res.body.contains("klaus_app")) {
			mismatches.add(method + " " + path + " leaks: " + res.body);
		}
	}

	private String issue(String jwt, String name, JsonArray appIDs) {
		return issue(jwt, name, appIDs, new JsonArray().add("apps:read").add("apps:write"));
	}

	/**
	 * @return the token, null when the server refused to issue it
	 */
	private String issue(String jwt, String name, JsonArray appIDs, JsonArray scopes) {
		JsonObject body = new JsonObject().put("name", name).put("scopes", scopes);
		if (appIDs != null) {
			body.put("appIDs", appIDs);
		}
		Response res = request("POST", "/rest/agent-tokens.json", jwt, "application/json", body.encode());
		return res.status == 200 ? new JsonObject(res.body).getString("token") : null;
	}

	private void deployOwn(TestContext context) {
		deploy(new MATC(), context);
		Response metadata = request("GET", "/.well-known/oauth-authorization-server", null, null, null);
		if (metadata.status != 200) {
			context.fail("The test server did not start on port " + conf.getInteger("http.port") + ", is it taken?");
			throw new IllegalStateException("Wrong server on port " + conf.getInteger("http.port"));
		}
	}

	private void cleanUpAgents() {
		cleanUp();
		client.remove(DB.getTable(AgentToken.class), Model.all());
		client.remove(DB.getTable(AgentGrant.class), Model.all());
		client.remove(DB.getTable(AgentClient.class), Model.all());
	}

	private static JsonObject fragment(String screenID, String widgetID) {
		return new JsonObject()
				.put("screens", new JsonObject().put(screenID, new JsonObject()
						.put("id", screenID).put("name", "Login")
						.put("x", 0).put("y", 0).put("w", 375).put("h", 667)
						.put("children", new JsonArray().add(widgetID))))
				.put("widgets", new JsonObject().put(widgetID, new JsonObject()
						.put("id", widgetID).put("type", "Button")
						.put("x", 20).put("y", 20).put("w", 100).put("h", 40)));
	}

	/**
	 * A multipart upload of a (tiny) image with a token.
	 */
	private Response upload(String path, String bearer) {
		Response result = new Response();
		String boundary = "----agenthack";
		byte[] png = new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
		try {
			ByteArrayOutputStream body = new ByteArrayOutputStream();
			body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"hack.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
			body.write(png);
			body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

			HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:" + conf.getInteger("http.port") + path).openConnection();
			con.setInstanceFollowRedirects(false);
			con.setRequestMethod("POST");
			con.setRequestProperty("Authorization", "Bearer " + bearer);
			con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
			con.setDoOutput(true);
			try (OutputStream os = con.getOutputStream()) {
				os.write(body.toByteArray());
			}
			result.status = con.getResponseCode();
			InputStream is = result.status < 400 ? con.getInputStream() : con.getErrorStream();
			if (is != null) {
				result.body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			e.printStackTrace();
			result.status = -1;
		}
		return result;
	}

	private static class Response {

		int status;

		String body = "";

		String location;
	}

	/**
	 * A raw request with an own Authorization header.
	 */
	private Response request(String method, String path, String bearer, String contentType, String body) {
		Response result = new Response();
		try {
			HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:" + conf.getInteger("http.port") + path).openConnection();
			con.setInstanceFollowRedirects(false);
			con.setRequestMethod(method);
			if (bearer != null) {
				con.setRequestProperty("Authorization", "Bearer " + bearer);
			}
			if (body != null) {
				con.setDoOutput(true);
				con.setRequestProperty("Content-Type", contentType);
				try (OutputStream os = con.getOutputStream()) {
					os.write(body.getBytes(StandardCharsets.UTF_8));
				}
			}
			result.status = con.getResponseCode();
			result.location = con.getHeaderField("Location");
			InputStream is = result.status < 400 ? con.getInputStream() : con.getErrorStream();
			if (is != null) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buffer = new byte[4096];
				int n;
				while ((n = is.read(buffer)) > 0) {
					out.write(buffer, 0, n);
				}
				result.body = new String(out.toByteArray(), StandardCharsets.UTF_8);
			}
			log(3, "request", method + " " + path + " > " + result.status);
		} catch (Exception e) {
			e.printStackTrace();
			result.status = -1;
		}
		return result;
	}
}
