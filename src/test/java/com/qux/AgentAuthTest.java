package com.qux;

import com.qux.model.AgentToken;
import com.qux.model.App;
import com.qux.model.User;
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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(VertxUnitRunner.class)
public class AgentAuthTest extends MatcTestCase {

	private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk-0123456789";

	/********************************************************************************************
	 * Personal access tokens
	 ********************************************************************************************/

	@Test
	public void test_pat_apps(TestContext context) {
		log("test_pat_apps", "enter");
		cleanUp();
		deploy(new MATC(), context);

		User klaus = postUser("klaus", context);
		assertLogin(context, klaus.getEmail(), "123456789");
		App app = postApp("klaus_app", false, context);
		App other = postApp("klaus_other", false, context);
		// AppREST saves the owner (Team) after it answered
		sleep(300);

		String token = post("/rest/agent-tokens.json", new JsonObject()
				.put("name", "One app")
				.put("appIDs", new JsonArray().add(app.getId()))).getString("token");
		logout();

		// restricted to one app
		context.assertEquals(200, request("GET", "/rest/ai/apps/" + app.getId() + ".json", token, null, null).status);
		context.assertEquals(403, request("GET", "/rest/ai/apps/" + other.getId() + ".json", token, null, null).status);
		context.assertEquals(404, request("GET", "/rest/images/" + other.getId() + "/photo.png", token, null, null).status);
		JsonArray apps = new JsonArray(request("GET", "/rest/ai/apps.json", token, null, null).body);
		context.assertEquals(1, apps.size());

		// read only: no change of any kind
		context.assertEquals(401, request("POST", "/rest/apps/props/" + app.getId() + ".json", token, "application/json", "{\"name\":\"x\"}").status);
		context.assertEquals(405, request("POST", "/rest/apps", token, "application/json", "{\"name\":\"x\"}").status);
		context.assertEquals(401, request("DELETE", "/rest/apps/" + app.getId() + ".json", token, null, null).status);
		// 200 is ok, because the logic always returned 200s :(
		context.assertTrue(request("POST", "/rest/apps/" + app.getId() + "/team/", token, "application/json", "{}").body.contains("error"));
		context.assertEquals("klaus_app", client.findOne(app_db, App.findById(app.getId())).getString("name"));

		log("test_pat_apps", "exit");
	}

	/********************************************************************************************
	 * Authorization code + PKCE, as an MCP client does it
	 ********************************************************************************************/

	@Test
	public void test_authorization_code(TestContext context) throws Exception {
		log("test_authorization_code", "enter");
		cleanUp();
		deploy(new MATC(), context);

		User klaus = postUser("klaus", context);
		assertLogin(context, klaus.getEmail(), "123456789");
		App app = postApp("klaus_app", false, context);
		String jwt = getJWT();
		logout();

		Response metadata = request("GET", "/.well-known/oauth-authorization-server", null, null, null);
		context.assertEquals(200, metadata.status);
		JsonObject as = new JsonObject(metadata.body);
		context.assertTrue(as.getString("token_endpoint").endsWith("/oauth/token"));
		context.assertTrue(as.getJsonArray("code_challenge_methods_supported").contains("S256"));

		/*
		 * 1) dynamic client registration
		 */
		Response registered = request("POST", "/oauth/register", null, "application/json", new JsonObject()
				.put("client_name", "Test Agent (quant-ux)")
				.put("redirect_uris", new JsonArray().add("http://127.0.0.1/callback"))
				.encode());
		context.assertEquals(201, registered.status);
		String clientID = new JsonObject(registered.body).getString("client_id");

		context.assertEquals(400, request("POST", "/oauth/register", null, "application/json", new JsonObject()
				.put("redirect_uris", new JsonArray().add("http://evil.com/callback")).encode()).status);
		context.assertEquals(400, request("POST", "/oauth/register", null, "application/json", new JsonObject()
				.put("redirect_uris", new JsonArray().add("javascript:alert(1)")).encode()).status);

		/*
		 * 2) authorize: redirects to the consent page of the web app. A
		 * loopback redirect may use any port.
		 */
		String redirect = "http://127.0.0.1:53111/callback";
		String authorizeUrl = "/oauth/authorize?response_type=code&client_id=" + enc(clientID)
				+ "&redirect_uri=" + enc(redirect)
				+ "&code_challenge=" + challenge(VERIFIER) + "&code_challenge_method=S256"
				+ "&state=xyz&scope=apps:read&resource=" + enc("http://localhost:8080/mcp");
		Response authorize = request("GET", authorizeUrl, null, null, null);
		context.assertEquals(302, authorize.status);
		context.assertTrue(authorize.location.contains("/#/agent/authorize.html?request="));
		String requestID = authorize.location.substring(authorize.location.indexOf("request=") + 8);

		// unknown redirect uri: no redirect, an error page
		Response badRedirect = request("GET", authorizeUrl.replace(enc(redirect), enc("http://127.0.0.1:53111/other")), null, null, null);
		context.assertEquals(400, badRedirect.status);

		// the consent page needs the logged in user
		context.assertEquals(401, request("GET", "/oauth/requests/" + requestID + ".json", null, null, null).status);

		/*
		 * 3) the user approves
		 */
		setJWT(jwt);
		JsonObject consent = get("/oauth/requests/" + requestID + ".json");
		context.assertEquals("Test Agent", consent.getJsonObject("client").getString("name"));
		context.assertEquals("127.0.0.1", consent.getString("redirectHost"));

		JsonObject approved = post("/oauth/requests/" + requestID + "/approve", new JsonObject());
		String callback = approved.getString("redirect");
		log("test_authorization_code", callback);
		context.assertTrue(callback.startsWith(redirect + "?code="));
		Map<String, String> params = query(callback);
		context.assertEquals("xyz", params.get("state"));
		context.assertNotNull(params.get("iss"));
		String code = params.get("code");

		// decided once only
		context.assertEquals(404, request("POST", "/oauth/requests/" + requestID + "/approve", jwt, "application/json", "{}").status);
		logout();

		/*
		 * 4) token: PKCE and the redirect uri must match
		 */
		Response wrongVerifier = request("POST", "/oauth/token", null, "application/x-www-form-urlencoded",
				form("grant_type", "authorization_code", "code", code, "redirect_uri", redirect, "client_id", clientID, "code_verifier", VERIFIER.replace('0', '1')));
		context.assertEquals(400, wrongVerifier.status);
		context.assertEquals("invalid_grant", new JsonObject(wrongVerifier.body).getString("error"));

		Response tokenRes = request("POST", "/oauth/token", null, "application/x-www-form-urlencoded",
				form("grant_type", "authorization_code", "code", code, "redirect_uri", redirect, "client_id", clientID, "code_verifier", VERIFIER));
		log("test_authorization_code", tokenRes.body);
		context.assertEquals(200, tokenRes.status);
		JsonObject tokens = new JsonObject(tokenRes.body);
		String access = tokens.getString("access_token");
		String refresh = tokens.getString("refresh_token");
		context.assertTrue(access.startsWith("qux_at_"));
		context.assertTrue(refresh.startsWith("qux_rt_"));
		context.assertEquals("Bearer", tokens.getString("token_type"));
		context.assertEquals(3600L, tokens.getLong("expires_in"));

		JsonObject me = new JsonObject(request("GET", "/rest/ai/me.json", access, null, null).body);
		context.assertEquals(klaus.getEmail(), me.getString("email"));
		context.assertEquals("http://localhost:8080/mcp", me.getJsonObject("token").getString("resource"));
		context.assertEquals("Test Agent", me.getJsonObject("token").getString("name"));
		context.assertEquals(200, request("GET", "/rest/ai/apps/" + app.getId() + ".json", access, null, null).status);

		/*
		 * 5) refresh rotates: the old refresh token is dead, using it again
		 * revokes everything of this login
		 */
		Response refreshed = request("POST", "/oauth/token", null, "application/x-www-form-urlencoded",
				form("grant_type", "refresh_token", "refresh_token", refresh, "client_id", clientID));
		context.assertEquals(200, refreshed.status);
		JsonObject tokens2 = new JsonObject(refreshed.body);
		String access2 = tokens2.getString("access_token");
		context.assertNotEquals(refresh, tokens2.getString("refresh_token"));
		context.assertEquals(401, request("GET", "/rest/ai/me.json", access, null, null).status);
		context.assertEquals(200, request("GET", "/rest/ai/me.json", access2, null, null).status);

		// still one connected agent, the refresh replaced its tokens
		setJWT(jwt);
		context.assertEquals(1, getList("/rest/agent-tokens.json").size());
		logout();

		Response replay = request("POST", "/oauth/token", null, "application/x-www-form-urlencoded",
				form("grant_type", "refresh_token", "refresh_token", refresh, "client_id", clientID));
		context.assertEquals(400, replay.status);
		sleep(200);
		context.assertEquals(401, request("GET", "/rest/ai/me.json", access2, null, null).status);

		/*
		 * 6) a code works once only
		 */
		Response codeReplay = request("POST", "/oauth/token", null, "application/x-www-form-urlencoded",
				form("grant_type", "authorization_code", "code", code, "redirect_uri", redirect, "client_id", clientID, "code_verifier", VERIFIER));
		context.assertEquals(400, codeReplay.status);

		log("test_authorization_code", "exit");
	}

	@Test
	public void test_authorization_code_deny_and_disconnect(TestContext context) throws Exception {
		log("test_authorization_code_deny_and_disconnect", "enter");
		cleanUp();
		deploy(new MATC(), context);

		User klaus = postUser("klaus", context);
		assertLogin(context, klaus.getEmail(), "123456789");
		String jwt = getJWT();

		String redirect = "http://localhost:4000/cb";
		String clientID = new JsonObject(request("POST", "/oauth/register", null, "application/json", new JsonObject()
				.put("client_name", "Cursor")
				.put("redirect_uris", new JsonArray().add(redirect))
				.encode()).body).getString("client_id");

		/*
		 * The user says no: the agent gets access_denied
		 */
		String requestID = startAuthorize(clientID, redirect, "s1");
		JsonObject denied = post("/oauth/requests/" + requestID + "/deny", new JsonObject());
		context.assertTrue(denied.getString("redirect").contains("error=access_denied"));
		context.assertTrue(denied.getString("redirect").contains("state=s1"));

		/*
		 * The user says yes, and later disconnects the agent in My account
		 */
		requestID = startAuthorize(clientID, redirect, "s2");
		String code = query(post("/oauth/requests/" + requestID + "/approve", new JsonObject()).getString("redirect")).get("code");
		JsonObject tokens = new JsonObject(request("POST", "/oauth/token", null, "application/x-www-form-urlencoded",
				form("grant_type", "authorization_code", "code", code, "redirect_uri", redirect, "client_id", clientID, "code_verifier", VERIFIER)).body);
		String access = tokens.getString("access_token");
		context.assertEquals(200, request("GET", "/rest/ai/me.json", access, null, null).status);

		// the list of connected agents shows it with the name of the client
		setJWT(jwt);
		JsonArray list = getList("/rest/agent-tokens.json");
		context.assertEquals(1, list.size());
		context.assertEquals("Cursor", list.getJsonObject(0).getString("name"));

		delete("/rest/agent-tokens/" + list.getJsonObject(0).getString("id") + ".json");
		context.assertEquals(401, request("GET", "/rest/ai/me.json", access, null, null).status);
		Response refreshed = request("POST", "/oauth/token", null, "application/x-www-form-urlencoded",
				form("grant_type", "refresh_token", "refresh_token", tokens.getString("refresh_token"), "client_id", clientID));
		context.assertEquals(400, refreshed.status);

		log("test_authorization_code_deny_and_disconnect", "exit");
	}

	/********************************************************************************************
	 * apps:write
	 ********************************************************************************************/

	@Test
	public void test_write_scope(TestContext context) {
		log("test_write_scope", "enter");
		cleanUp();
		deploy(new MATC(), context);

		User bernd = postUser("bernd", context);
		assertLogin(context, bernd.getEmail(), "123456789");
		App berndApp = postApp("bernd_app", false, context);
		logout();

		User klaus = postUser("klaus", context);
		assertLogin(context, klaus.getEmail(), "123456789");
		App app = postApp("klaus_app", false, context);

		String reader = post("/rest/agent-tokens.json", new JsonObject().put("name", "Reader")).getString("token");
		JsonObject created = post("/rest/agent-tokens.json", new JsonObject().put("name", "Writer")
				.put("scopes", new JsonArray().add("apps:write").add("admin:all")));
		String writer = created.getString("token");
		context.assertEquals(new JsonArray().add("apps:read").add("apps:write"), created.getJsonArray("scopes"));
		String restricted = post("/rest/agent-tokens.json", new JsonObject().put("name", "One app")
				.put("scopes", new JsonArray().add("apps:write"))
				.put("appIDs", new JsonArray().add(app.getId()))).getString("token");
		logout();

		/*
		 * A read only token cannot write
		 */
		Response denied = request("POST", "/rest/ai/apps.json", reader, "application/json", newApp("Nope").encode());
		context.assertEquals(403, denied.status);
		context.assertTrue(denied.wwwAuthenticate.contains("insufficient_scope"));
		context.assertEquals(403, request("POST", "/rest/ai/apps/" + app.getId() + "/screens.json", reader, "application/json", fragment("s1", "w1").encode()).status);

		/*
		 * create_app
		 */
		JsonObject tokens = new JsonObject().put("dtPrimary", new JsonObject()
				.put("id", "dtPrimary").put("name", "Primary").put("type", "color").put("value", "#3366ff"));
		Response newApp = request("POST", "/rest/ai/apps.json", writer, "application/json", newApp("From the repo").put("designtokens", tokens).encode());
		log("test_write_scope", newApp.body);
		context.assertEquals(200, newApp.status);
		JsonObject newAppJson = new JsonObject(newApp.body);
		String newAppID = newAppJson.getString("id");
		context.assertEquals("smartphone", newAppJson.getString("type"));

		JsonObject stored = client.findOne(app_db, App.findById(newAppID));
		context.assertEquals("From the repo", stored.getString("name"));
		context.assertEquals("#3366ff", stored.getJsonObject("designtokens").getJsonObject("dtPrimary").getString("value"));
		context.assertEquals(375, stored.getJsonObject("screenSize").getInteger("w"));

		// the user owns it: it is in the list of the agent, and the web app shows it
		JsonArray apps = new JsonArray(request("GET", "/rest/ai/apps.json", writer, null, null).body);
		context.assertEquals(2, apps.size());
		context.assertEquals("owner", apps.getJsonObject(0).getString("permission"));

		context.assertEquals(400, request("POST", "/rest/ai/apps.json", writer, "application/json", new JsonObject().put("name", "No size").encode()).status);
		context.assertEquals(400, request("POST", "/rest/ai/apps.json", writer, "application/json", newApp("").encode()).status);
		context.assertEquals(400, request("POST", "/rest/ai/apps.json", writer, "application/json", newApp("Bad token")
				.put("designtokens", new JsonObject().put("a.b", new JsonObject())).encode()).status);

		// a token for one app cannot make more
		context.assertEquals(403, request("POST", "/rest/ai/apps.json", restricted, "application/json", newApp("Nope").encode()).status);

		/*
		 * create_screens
		 */
		JsonObject fragment = fragment("s20001_1", "w20002_1")
				.put("designtokens", new JsonObject()
						.put("dtPrimary", new JsonObject().put("id", "dtPrimary").put("type", "color").put("value", "#000000"))
						.put("dtText", new JsonObject().put("id", "dtText").put("type", "color").put("value", "#111111")))
				.put("lastUUID", 20003)
				.put("startScreen", "s20001_1");
		Response added = request("POST", "/rest/ai/apps/" + newAppID + "/screens.json", writer, "application/json", fragment.encode());
		log("test_write_scope", added.body);
		context.assertEquals(200, added.status);
		context.assertEquals(1, new JsonObject(added.body).getLong("designtokens").intValue());

		stored = client.findOne(app_db, App.findById(newAppID));
		context.assertEquals("Login", stored.getJsonObject("screens").getJsonObject("s20001_1").getString("name"));
		context.assertEquals("Button", stored.getJsonObject("widgets").getJsonObject("w20002_1").getString("type"));
		context.assertEquals("s20001_1", stored.getString("startScreen"));
		context.assertEquals(20003L, stored.getLong("lastUUID"));
		// an existing token is never replaced
		context.assertEquals("#3366ff", stored.getJsonObject("designtokens").getJsonObject("dtPrimary").getString("value"));
		context.assertEquals("#111111", stored.getJsonObject("designtokens").getJsonObject("dtText").getString("value"));

		// the same ids again: nothing is overwritten
		context.assertEquals(400, request("POST", "/rest/ai/apps/" + newAppID + "/screens.json", writer, "application/json", fragment.encode()).status);
		// a child that is not there, a bad id, no screens
		JsonObject broken = fragment("s3", "w3");
		broken.getJsonObject("screens").getJsonObject("s3").getJsonArray("children").add("w_missing");
		context.assertEquals(400, request("POST", "/rest/ai/apps/" + newAppID + "/screens.json", writer, "application/json", broken.encode()).status);
		context.assertEquals(400, request("POST", "/rest/ai/apps/" + newAppID + "/screens.json", writer, "application/json", fragment("s.$4", "w4").encode()).status);
		context.assertEquals(400, request("POST", "/rest/ai/apps/" + newAppID + "/screens.json", writer, "application/json", "{}").status);

		// only apps the user can write, and the ones the token is for
		context.assertEquals(403, request("POST", "/rest/ai/apps/" + berndApp.getId() + "/screens.json", writer, "application/json", fragment("s5", "w5").encode()).status);
		context.assertEquals(403, request("POST", "/rest/ai/apps/" + newAppID + "/screens.json", restricted, "application/json", fragment("s6", "w6").encode()).status);
		context.assertEquals(200, request("POST", "/rest/ai/apps/" + app.getId() + "/screens.json", restricted, "application/json", fragment("s7", "w7").encode()).status);

		// and still no other route of the web app
		context.assertEquals(401, request("POST", "/rest/apps/" + app.getId() + ".json", writer, "application/json", "{\"name\":\"hacked\"}").status);
		context.assertEquals(401, request("POST", "/rest/apps/" + app.getId() + "/update", writer, "application/json", "[]").status);
		context.assertEquals(401, request("DELETE", "/rest/apps/" + app.getId() + ".json", writer, null, null).status);

		log("test_write_scope", "exit");
	}

	@Test
	public void test_authorization_code_scopes(TestContext context) throws Exception {
		log("test_authorization_code_scopes", "enter");
		cleanUp();
		deploy(new MATC(), context);

		User klaus = postUser("klaus", context);
		assertLogin(context, klaus.getEmail(), "123456789");

		JsonObject as = new JsonObject(request("GET", "/.well-known/oauth-authorization-server", null, null, null).body);
		context.assertTrue(as.getJsonArray("scopes_supported").contains("apps:write"));

		String redirect = "http://127.0.0.1:53111/callback";
		String clientID = new JsonObject(request("POST", "/oauth/register", null, "application/json", new JsonObject()
				.put("client_name", "Claude Code")
				.put("redirect_uris", new JsonArray().add(redirect))
				.encode()).body).getString("client_id");

		/*
		 * The agent asks for apps:write, the consent page shows it, the user approves
		 */
		String requestID = startAuthorize(clientID, redirect, "s1", "apps:read apps:write");
		context.assertEquals(new JsonArray().add("apps:read").add("apps:write"), get("/oauth/requests/" + requestID + ".json").getJsonArray("scopes"));
		String code = query(post("/oauth/requests/" + requestID + "/approve", new JsonObject()).getString("redirect")).get("code");
		JsonObject tokens = new JsonObject(request("POST", "/oauth/token", null, "application/x-www-form-urlencoded",
				form("grant_type", "authorization_code", "code", code, "redirect_uri", redirect, "client_id", clientID, "code_verifier", VERIFIER)).body);
		context.assertEquals("apps:read apps:write", tokens.getString("scope"));
		context.assertEquals(200, request("POST", "/rest/ai/apps.json", tokens.getString("access_token"), "application/json", newApp("Agent app").encode()).status);

		// the refresh keeps the scopes
		JsonObject refreshed = new JsonObject(request("POST", "/oauth/token", null, "application/x-www-form-urlencoded",
				form("grant_type", "refresh_token", "refresh_token", tokens.getString("refresh_token"), "client_id", clientID)).body);
		context.assertEquals("apps:read apps:write", refreshed.getString("scope"));

		/*
		 * The user unchecks apps:write: the token can only read
		 */
		requestID = startAuthorize(clientID, redirect, "s2", "apps:write");
		code = query(post("/oauth/requests/" + requestID + "/approve", new JsonObject().put("scopes", new JsonArray().add("apps:read"))).getString("redirect")).get("code");
		tokens = new JsonObject(request("POST", "/oauth/token", null, "application/x-www-form-urlencoded",
				form("grant_type", "authorization_code", "code", code, "redirect_uri", redirect, "client_id", clientID, "code_verifier", VERIFIER)).body);
		context.assertEquals("apps:read", tokens.getString("scope"));
		context.assertEquals(403, request("POST", "/rest/ai/apps.json", tokens.getString("access_token"), "application/json", newApp("Agent app").encode()).status);

		/*
		 * Without a scope: read only, and the page cannot add what the agent did not ask for
		 */
		requestID = startAuthorize(clientID, redirect, "s3");
		context.assertEquals(new JsonArray().add("apps:read"), get("/oauth/requests/" + requestID + ".json").getJsonArray("scopes"));
		code = query(post("/oauth/requests/" + requestID + "/approve", new JsonObject().put("scopes", new JsonArray().add("apps:write"))).getString("redirect")).get("code");
		tokens = new JsonObject(request("POST", "/oauth/token", null, "application/x-www-form-urlencoded",
				form("grant_type", "authorization_code", "code", code, "redirect_uri", redirect, "client_id", clientID, "code_verifier", VERIFIER)).body);
		context.assertEquals("apps:read", tokens.getString("scope"));

		log("test_authorization_code_scopes", "exit");
	}

	private static JsonObject newApp(String name) {
		return new JsonObject().put("name", name).put("screenSize", new JsonObject().put("w", 375).put("h", 667));
	}

	private static JsonObject fragment(String screenID, String widgetID) {
		return new JsonObject()
				.put("screens", new JsonObject().put(screenID, new JsonObject()
						.put("id", screenID).put("name", "Login")
						.put("x", 0).put("y", 0).put("w", 375).put("h", 667)
						.put("children", new JsonArray().add(widgetID))))
				.put("widgets", new JsonObject().put(widgetID, new JsonObject()
						.put("id", widgetID).put("type", "Button")
						.put("x", 20).put("y", 20).put("w", 100).put("h", 40)
						.put("props", new JsonObject().put("label", "Log in"))
						.put("style", new JsonObject().put("background", "#3366ff"))));
	}

	/********************************************************************************************
	 * Helper
	 ********************************************************************************************/

//	/**
//	 * Deploys the server and makes sure the tests talk to it: when the port
//	 * is taken (e.g. by the Vue dev server, which proxies /rest to
//	 * production), the requests would go somewhere else.
//	 */
//	private void deployOwn(TestContext context) {
//		deploy(new MATC(), context);
//		Response metadata = request("GET", "/.well-known/oauth-authorization-server", null, null, null);
//		if (metadata.status != 200) {
//			context.fail("The test server did not start on port " + conf.getInteger("http.port") + ", is it taken?");
//			throw new IllegalStateException("Wrong server on port " + conf.getInteger("http.port"));
//		}
//	}
//


	private void setScreens(App app, JsonObject screens) {
		JsonObject mongoApp = client.findOne(app_db, App.findById(app.getId()));
		mongoApp.put("screens", screens);
		client.save(app_db, mongoApp);
	}

	private String startAuthorize(String clientID, String redirect, String state) throws Exception {
		return startAuthorize(clientID, redirect, state, null);
	}

	private String startAuthorize(String clientID, String redirect, String state, String scope) throws Exception {
		Response authorize = request("GET", "/oauth/authorize?response_type=code&client_id=" + enc(clientID)
				+ "&redirect_uri=" + enc(redirect)
				+ "&code_challenge=" + challenge(VERIFIER) + "&code_challenge_method=S256&state=" + state
				+ (scope != null ? "&scope=" + enc(scope) : ""), null, null, null);
		return authorize.location.substring(authorize.location.indexOf("request=") + 8);
	}

	private static String challenge(String verifier) throws Exception {
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
	}

	private static String enc(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static String form(String... params) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i + 1 < params.length; i += 2) {
			if (sb.length() > 0) {
				sb.append('&');
			}
			sb.append(params[i]).append('=').append(enc(params[i + 1]));
		}
		return sb.toString();
	}

	private static Map<String, String> query(String url) {
		Map<String, String> result = new LinkedHashMap<>();
		String q = url.substring(url.indexOf('?') + 1);
		for (String pair : q.split("&")) {
			String[] kv = pair.split("=", 2);
			try {
				result.put(kv[0], kv.length > 1 ? URLDecoder.decode(kv[1], "UTF-8") : "");
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return result;
	}

	static class Response {
		int status;
		String body = "";
		String location;
		String wwwAuthenticate = "";
	}

	/**
	 * A raw request: own Authorization header, no redirects followed.
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
			String www = con.getHeaderField("WWW-Authenticate");
			result.wwwAuthenticate = www != null ? www : "";
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
