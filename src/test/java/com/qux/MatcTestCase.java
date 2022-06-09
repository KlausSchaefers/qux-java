package com.qux;

import static java.nio.file.Files.readAllBytes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.qux.acl.Acl;
import com.qux.mocks.SyncMongoClient;
import com.qux.util.*;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;

import com.google.common.io.CharStreams;

import com.qux.model.Annotation;
import com.qux.model.App;
import com.qux.model.Command;
import com.qux.model.CommandStack;
import com.qux.model.Comment;
import com.qux.model.Event;
import com.qux.model.Image;
import com.qux.model.Invitation;
import com.qux.model.Library;
import com.qux.model.LibraryTeam;
import com.qux.model.Model;
import com.qux.model.Mouse;
import com.qux.model.Notification;
import com.qux.model.Team;
import com.qux.model.TestSetting;
import com.qux.model.User;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.TestContext;

public class MatcTestCase {

	public String quxServerUrl = "http://localhost:8082";

	protected Vertx vertx;

	protected MongoClient mongo;

	protected SyncMongoClient client;

	public JsonObject conf;

	public JSONMapper mapper = new JSONMapper();

	protected String team_db, inv_db, app_db, user_db, commandStack_db, comments_db, event_db, image_db,
			annotation_db, command_db,mouse_db, notification_db, testsetting_db, lib_db, lib_team_db;

	protected BasicCookieStore cookieStore;

	protected CloseableHttpClient httpClient;

	protected int loglevel = 2;

	protected HashMap<String, List<Long>> restPerformance = new HashMap<String, List<Long>>();

	private String domain;
	
	private String jwt;


	public void setJWT(String token) {
		this.jwt = token;
	}

	public String getJWT() {
		return this.jwt;
	}

	@Before
	public void before(TestContext contex) {
		try {
			conf = new JsonObject(new String(readAllBytes(Paths.get("matc.conf"))));
			conf.put("mongo.table_prefix", "test");
			conf.put("image.folder.apps", "test/apps");
			conf.put("image.folder.user", "test/user");
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (conf.getInteger(Config.HTTP_PORT) != 8080) {
			quxServerUrl = "http://localhost:" + conf.getInteger(Config.HTTP_PORT);
		}

		vertx = Vertx.vertx();

		JsonObject mongoConfig = Config.getMongo(conf);
		mongo = MongoClient.createShared(vertx,mongoConfig);
		client = new SyncMongoClient(mongo);

		DB.setPrefix("test");

		app_db = DB.getTable(App.class);
		user_db = DB.getTable(User.class);
		commandStack_db = DB.getTable(CommandStack.class);
		command_db = DB.getTable(Command.class);
		comments_db = DB.getTable(Comment.class);
		event_db = DB.getTable(Event.class);
		image_db = DB.getTable(Image.class);
		annotation_db = DB.getTable(Annotation.class);
		inv_db = DB.getTable(Invitation.class);
		team_db = DB.getTable(Team.class);
		mouse_db = DB.getTable(Mouse.class);
		notification_db = DB.getTable(Notification.class);
		testsetting_db = DB.getTable(TestSetting.class);
		lib_db = DB.getTable(Library.class);
		lib_team_db = DB.getTable(LibraryTeam.class);
		
		cookieStore = new BasicCookieStore();
		jwt = null;

		httpClient = HttpClients.custom().setDefaultCookieStore(cookieStore).build();

	}

	void delete(File f) {
		if (f.isDirectory()) {
			for (File c : f.listFiles())
				delete(c);
		}
		
	}

	@After
	public void after(TestContext contex) {
		try {

			// vertx.undeploy(vertileID);

			vertx.close();

			httpClient.close();
			mongo.close();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	protected void sleep(long m) {
		try {
			Thread.sleep(m);
		} catch (InterruptedException e) {
		}
	}

	protected void sleep() {
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
		}
	}

	private void disableCookies () {
		log("disableCookies()", "enter");
		this.cookieStore.clear();
		httpClient = HttpClients.custom().disableCookieManagement().build();

	}
	
	public void cleanUp() {
		
		this.disableCookies();
		jwt = null;

		client.remove(app_db, App.all());
		client.remove(user_db, Model.all());
		client.remove(commandStack_db, Model.all());
		client.remove(comments_db, Model.all());
		client.remove(event_db, Model.all());
		client.remove(image_db, Model.all());
		client.remove(annotation_db, Model.all());
		client.remove(command_db, Model.all());
		client.remove(inv_db, Model.all());
		client.remove(team_db, Model.all());
		client.remove(mouse_db, Model.all());
		client.remove(notification_db, Model.all());
		client.remove(testsetting_db, Model.all());
		client.remove(lib_db, Model.all());
		client.remove(lib_team_db, Model.all());

		debug("cleanUp", "# " + app_db + "  : " + client.count(app_db, App.all()));
		debug("cleanUp", "# " + user_db + " : " + client.count(user_db, App.all()));
		debug("cleanUp", "# " + commandStack_db + " : " + client.count(commandStack_db, App.all()));
		debug("cleanUp", "# " + event_db + " : " + client.count(event_db, App.all()));
		debug("cleanUp", "# " + image_db + " : " + client.count(image_db, App.all()));
		debug("cleanUp", "# " + annotation_db + " : " + client.count(annotation_db, App.all()));
		debug("cleanup", "# " + command_db + " : " + client.count(command_db, App.all()));
		debug("cleanup", "# " + mouse_db + " : " + client.count(mouse_db, App.all()));
		debug("cleanup", "# " + notification_db + " : " + client.count(notification_db, App.all()));
		debug("cleanup", "# " + testsetting_db + " : " + client.count(testsetting_db, App.all()));
		debug("cleanup", "# " + lib_db + " : " + client.count(lib_db, App.all()));
		debug("cleanup", "# " + lib_team_db + " : " + client.count(lib_team_db, App.all()));

	}

	public void debug(String method, String message) {
		log(4, method, message);
	}

	public void log(String method, String message) {
		log(3, method, message);
	}
	
	public void error(String method, String message) {
		log(0, method, message);
	}
	
	public void log(int level, String method, String message) {
		if (level <= loglevel)
			System.out.println(this.getClass().getSimpleName() + "." + method + "() > " + message);
	}

	public void logPerformance(String method, String url, long time) {
		String key = method + ":" + url;
		if (!restPerformance.containsKey(key)) {
			restPerformance.put(key, new ArrayList<Long>());
		}
		restPerformance.get(key).add(time);
	}

	public void printRestPerformance() {
		for (String key : restPerformance.keySet()) {
			Long count = restPerformance.get(key).stream().count();
			System.out.println(" - " + (count / restPerformance.get(key).size()) + "ms >> "
					+ restPerformance.get(key).size() + "x >>" +  key);
		}
	}

	public void print(List<JsonObject> results) {
		log("print", "#" + results.size() + " ");
		for (JsonObject result : results)
			log("print", result.encodePrettily());

	}

	public void print(JsonArray results) {
		log("print", "#" + results.size() + " ");

		log("print", results.encodePrettily());

	}

	public void deploy(Verticle v, TestContext context) {

		CountDownLatch l = new CountDownLatch(1);

		DeploymentOptions options = new DeploymentOptions(new JsonObject().put("config", conf));

		vertx.deployVerticle(v, options, new Handler<AsyncResult<String>>() {

			@Override
			public void handle(AsyncResult<String> event) {

				if (event.succeeded()) {
					log("deploy", "exit > " + event.result());
					event.result();
				} else {
					// context.fail("Could not deploy verticle");
					event.cause().printStackTrace();
				}

				l.countDown();
			}
		});

		try {
			l.await();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		sleep(500);
	}

	public String getString(String url) {

		url = getUrl(url);
		try {
			long start = System.currentTimeMillis();
			HttpGet httpget = new HttpGet(url);
			if (this.jwt != null) {
				httpget.addHeader("Authorization", "Bearer " + this.jwt);
			}
			CloseableHttpResponse resp = httpClient.execute(httpget);

			if (resp.getStatusLine().getStatusCode() == 200) {
				InputStream is = resp.getEntity().getContent();
				String result = CharStreams.toString(new InputStreamReader(is));
				long end = System.currentTimeMillis();
				log("getString", "exit > " + url + " took :" + (end - start) + "ms");
				resp.close();
				return result;
			} else {
				resp.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;

	}

	public JsonObject post(String url, Object obj) {
		return post(url, mapper.toVertx(obj));
	}

	public JsonObject post(String url, JsonArray obj) {
		return post(url, obj.encode());
	}

	public JsonObject post(String url, JsonObject data) {
		return post(url, data.encode());
	}

	public JsonObject post(String url, String data) {
		log("post", url);
		url = getUrl(url);
		try {

			long start = System.currentTimeMillis();
			HttpPost post = new HttpPost(url);
			if (this.jwt != null) {
				post.addHeader("Authorization", "Bearer " + this.jwt);
			}
			
			if (this.domain != null){
				post.addHeader("app", this.domain);
			}
		
			StringEntity input = new StringEntity(data);

			input.setContentType("application/json");
			post.setEntity(input);

			CloseableHttpResponse resp = httpClient.execute(post);

			if (resp.getStatusLine().getStatusCode() == 200) {

				InputStream is = resp.getEntity().getContent();

				String json = CharStreams.toString(new InputStreamReader(is));

				resp.close();
				long end = System.currentTimeMillis();
				logPerformance("POST", url, end - start);
				return new JsonObject(json);

			} else {
				resp.close();
				return new JsonObject().put("error", resp.getStatusLine().getStatusCode());
			}

		} catch (Exception e) {
			e.printStackTrace();

			return new JsonObject().put("error", "error");
		}

	}

	public JsonObject postFile(String url, String fileName) {
		log("post", url);
		url = getUrl(url);
		try {

			HttpPost post = new HttpPost(url);
			if (this.jwt != null) {
				post.addHeader("Authorization", "Bearer " + this.jwt);
			}

			File file = new File(fileName);
			if (!file.exists()) {
				error("post", "File " + file.getAbsolutePath() + " does not exits");
				return new JsonObject().put("error", "error").put("worngPath", fileName);
			}

			HttpEntity en = MultipartEntityBuilder.create().addBinaryBody(fileName, file).build();

			post.setEntity(en);

			CloseableHttpResponse resp = httpClient.execute(post);

			if (resp.getStatusLine().getStatusCode() == 200) {

				InputStream is = resp.getEntity().getContent();

				String json = CharStreams.toString(new InputStreamReader(is));

				resp.close();
				return new JsonObject(json);

			} else {
				resp.close();
				return new JsonObject().put("error", resp.getStatusLine().getStatusCode());
			}

		} catch (Exception e) {
			e.printStackTrace();

			return new JsonObject().put("error", "error");
		}

	}
	
	public JsonObject get(String url) {
		debug("get", url);
		url = getUrl(url);
		try {
			long start = System.currentTimeMillis();
			HttpGet httpget = new HttpGet(url);
			if (this.jwt != null) {
				httpget.addHeader("Authorization", "Bearer " + this.jwt);
			}
			
			CloseableHttpResponse resp = httpClient.execute(httpget);

			if (resp.getStatusLine().getStatusCode() == 200) {

				InputStream is = resp.getEntity().getContent();

				String json = CharStreams.toString(new InputStreamReader(is));
				resp.close();

				long end = System.currentTimeMillis();
				logPerformance("GET", url, end - start);
				return new JsonObject(json);

			} else {
				resp.close();
				return new JsonObject().put("error", resp.getStatusLine().getStatusCode());
			}

		} catch (Exception e) {
			e.printStackTrace();
			return new JsonObject().put("error", "error");
		}

	}

	public InputStream getRaw(String url) {
		debug("getRaw", url);
		url = getUrl(url);
		try {
			HttpGet httpget = new HttpGet(url);
			CloseableHttpResponse resp = httpClient.execute(httpget);
			if (resp.getStatusLine().getStatusCode() == 200) {
				return resp.getEntity().getContent();
			} else {
				resp.close();
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}


	public <T> T get(String url, Class<T> cls) {
		debug("get", url);
		url = getUrl(url);
		try {
			HttpGet httpget = new HttpGet(url);
			if (this.jwt != null) {
				httpget.addHeader("Authorization", "Bearer " + this.jwt);
			}
			
			CloseableHttpResponse resp = httpClient.execute(httpget);

			if (resp.getStatusLine().getStatusCode() == 200) {

				InputStream is = resp.getEntity().getContent();

				String json = CharStreams.toString(new InputStreamReader(is));
				resp.close();

				return mapper.fromJson(json, cls);

			} else {
				resp.close();
				return null;
			}

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

	}

	private String getUrl(String url) {
		return quxServerUrl + url;
	}

	public void logout() {
		delete("/rest/login");
		this.jwt = null;
	}

	public JsonArray getList(String url) {
		log("getList", url);
		url = getUrl(url);
		String json = null;
		try {

			long start = System.currentTimeMillis();
			HttpGet httpget = new HttpGet(url);
			if (this.jwt != null) {
				httpget.addHeader("Authorization", "Bearer " + this.jwt);
			}
			CloseableHttpResponse resp = httpClient.execute(httpget);

			if (resp.getStatusLine().getStatusCode() == 200) {

				InputStream is = resp.getEntity().getContent();

				json = CharStreams.toString(new InputStreamReader(is));
				resp.close();

				debug("getList", json);
				
				long end = System.currentTimeMillis();
				logPerformance("GET", url, end - start);

				try {
					return new JsonArray(json);
				} catch (Exception e){
					
				}
				return new JsonArray();

			} else {
				debug("getList", "Error :" + resp.getStatusLine().getStatusCode());
				resp.close();
				return new JsonArray().add(resp.getStatusLine().getStatusCode());
			}

		} catch (Exception e) {
			log("getList", "Error > " + json);
			e.printStackTrace();
			return null;
		}

	}

	public JsonObject delete(String url) {
		debug("delete", url);
		url = getUrl(url);
		try {
			long start = System.currentTimeMillis();
			HttpDelete httpget = new HttpDelete(url);
			if (this.jwt != null) {
				httpget.addHeader("Authorization", "Bearer " + this.jwt);
			}
			CloseableHttpResponse resp = httpClient.execute(httpget);

			if (resp.getStatusLine().getStatusCode() == 200) {

				InputStream is = resp.getEntity().getContent();

				String json = CharStreams.toString(new InputStreamReader(is));
				resp.close();

				long end = System.currentTimeMillis();
				logPerformance("DELETE", url, end - start);

				try {
					return new JsonObject(json);
				} catch (Exception e) {
					System.out.println(json);
					return new JsonObject().put("error", "error");
				}

			} else {
				resp.close();
				return new JsonObject().put("error", resp.getStatusLine().getStatusCode());
			}

		} catch (Exception e) {
			e.printStackTrace();
			return new JsonObject().put("error", "error");
		}

	}

	public JsonObject deletePermission(User user, App app, TestContext context) {
		List<JsonObject> acls = client.find(team_db, Team.findByUser(user));
		System.out.println("deletePermission() > user acls" + acls.size());
		int matches = 0;
		for(JsonObject acl : acls){
			String aclAppID = acl.getString("appID");
			if (aclAppID.equals(app.getId())){
				matches++;
			}
		}
		context.assertEquals(1, matches, "ACL not inclued");

		
		JsonObject result = delete("/rest/apps/" + app.getId() + "/team/" + user.getId() + ".json");
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(!result.containsKey("error"));

		List<JsonObject> acls2 = client.find(team_db, Team.findByUser(user));
		context.assertEquals(acls.size()-1, acls2.size(), "Not one less");
		for(JsonObject acl : acls2){
			String aclAppID = acl.getString("appID");
			System.out.println(aclAppID + " ? " + app.getId());
			context.assertFalse(app.getId().equals(aclAppID));
		}

		return result;
	}

	public JsonObject createPermission(User user, App app, int p, TestContext context) {
		JsonObject permission = new JsonObject().put("email", user.getEmail()).put("permission", p);
		JsonObject result = post("/rest/apps/" + app.getId() + "/team/", permission);

		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(!result.containsKey("error"));

		JsonObject mongo_team = client.findOne(team_db, Team.findByUserAndApp(user, app.getId()));
		log("postPermission", "findOne(mongo) > " + mongo_team);
		context.assertEquals(mongo_team.getInteger(Team.PERMISSION), p, "Wrong permission in db");

		return result;
	}

	public JsonObject updatePermission(User user, App app, int p, TestContext context) {
		JsonObject permission = new JsonObject().put("permission", p);

		JsonObject result = post("/rest/apps/" + app.getId() + "/team/" + user.getId() + ".json", permission);
		log("updatePermission", " response > " + result.encode());
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(!result.containsKey("error"));

		JsonObject mongo_team = client.findOne(team_db, Team.findByUserAndApp(user, app.getId()));
		debug("postPermission", "findOne(mongo) > " + mongo_team);
		context.assertEquals(mongo_team.getInteger(Team.PERMISSION), p, "Wrong permission in db");

		return result;
	}

	public JsonObject createPermissionError(User user, App app, int p, TestContext context) {
		JsonObject permission = new JsonObject().put("email", user.getEmail()).put("permission", p);
		JsonObject result = post("/rest/apps/" + app.getId() + "/team/", permission);
		debug("postPermissionError", "> " + result);
		context.assertTrue(result.containsKey("errors") || result.containsKey("error"));
		return result;
	}

	public JsonObject updatePermissionError(User user, App app, int p, TestContext context) {
		JsonObject permission = new JsonObject().put("permission", p);

		JsonObject result = post("/rest/apps/" + app.getId() + "/team/" + user.getId() + ".json", permission);
		debug("postPermissionError", "> " + result);
		context.assertTrue(result.containsKey("errors") || result.containsKey("error"));

		return result;
	}

	public JsonObject getApp(App app, TestContext context) {

		JsonObject result = get("/rest/apps/" + app.getId() + ".json");

		context.assertTrue(!result.containsKey("errors"), result.encode());
		context.assertTrue(!result.containsKey("error"), result.encode());
		context.assertTrue(result.containsKey("id"));

		return result;
	}
	
	

	public JsonObject getAppError(App app, TestContext context) {

		JsonObject result = get("/rest/apps/" + app.getId() + ".json");

		context.assertTrue(result.containsKey("errors") || result.containsKey("error"));
		context.assertTrue(!result.containsKey("id"));

		return result;
	}

	public JsonObject updateApp(App app, String name, TestContext context) {

		JsonObject update = new JsonObject().put("name", name);

		JsonObject result = post("/rest/apps/" + app.getId() + ".json", update);

		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(!result.containsKey("error"));

		JsonObject mongo_app = client.findOne(app_db, App.findById(app.getId()));
		context.assertEquals(name, mongo_app.getString("name"));

		return result;
	}

	public JsonObject updateApp(App app, JsonObject update, TestContext context) {

		JsonObject result = post("/rest/apps/" + app.getId() + ".json", update);

		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(!result.containsKey("error"));

		JsonObject mongo_app = client.findOne(app_db, App.findById(app.getId()));

		debug("udpateApp", mongo_app.encodePrettily());

		return result;
	}

	public JsonObject updateAppError(App app, String name, TestContext context) {

		JsonObject update = new JsonObject().put("name", name);

		JsonObject result = post("/rest/apps/" + app.getId() + ".json", update);
		context.assertTrue(result.containsKey("errors") || result.containsKey("error"));

		return result;
	}

	public App postApp(String name, boolean pub, TestContext context) {
		App app = createApp(name, pub);
		JsonObject result = post("/rest/apps", app);
	

		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("users"));
		context.assertTrue(!result.containsKey("invitations"));
		context.assertEquals(result.getBoolean("isPublic"), pub);

		app = mapper.fromVertx(result, App.class);
		context.assertEquals(result.getString("_id"), app.getId());

		return app;
	}

	public JsonObject createApp(String name, int w, int h, TestContext context) {
		JsonObject app = new JsonObject();
		
		app.put("name", name);
		app.put("screenSize", new JsonObject().put("w" ,w).put("h", h));
		app.put("screens", new JsonObject());
		app.put("widgets", new JsonObject());
		app.put("isPublic", false);
		
		JsonObject result = post("/rest/apps", app);
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("users"));
		context.assertTrue(!result.containsKey("invitations"));

		
		return result;
	}
	
	public JsonObject postAppAsJson(String name, boolean pub, TestContext context) {
		App app = createApp(name, pub);
		JsonObject result = post("/rest/apps", app);
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("users"));
		context.assertTrue(!result.containsKey("invitations"));
		context.assertEquals(result.getBoolean("isPublic"), pub);

		context.assertEquals(result.getString("_id"), app.getId());

		return result;
	}

	public User postUser(String name, TestContext context) {
		User user = createUser(name);
		user.setPassword("123456789");
		JsonObject result = post("/rest/user", user);
		debug("postUser", result.encode());
		if (result.containsKey("errors")) {
			System.err.println("postUser(): Error" + result.toString());
		}
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("_id"));
		user.setId(result.getString("_id"));

		return user;
	}

	public User postUser(String name, String email, TestContext context) {
		User user = createUser(name);
		user.setEmail(email);
		user.setPassword("123456789");
		JsonObject result = post("/rest/user", user);
		debug("postUser", result.encode());
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("_id"));
		user.setId(result.getString("_id"));

		return user;
	}

	public JsonArray assertList(String url, int x, TestContext context) {
		JsonArray list = getList(url);
		log("assertList", "" + list.size() + " ?= " + x);
		if (list.size() == 1) {
			try {
				context.assertNotEquals(404, list.getInteger(0));
			} catch (Exception e) {
			}
		}
		context.assertEquals(x, list.size(), url);

		return list;
	}

	public void assertListError(String url, TestContext context) {
		JsonArray list = getList(url);
		log("assertListError", "" + list);
		context.assertEquals(1, list.size());
		context.assertEquals(401, list.getInteger(0));
	}

	public void assertUserList(int size, TestContext context) {
		JsonArray apps = getList("/rest/apps/");
		context.assertEquals(size, apps.size());
	}

	public void assertPublicList(int size, TestContext context) {
		JsonArray apps = getList("/rest/apps/public");
		context.assertEquals(size, apps.size());
	}

	public void assertLogin(TestContext context, User user, String password) {
		JsonObject login = new JsonObject().put("email", user.getEmail()).put("password", password);
		JsonObject result = post("/rest/login", login);
		debug("assertLogin", "" + !result.containsKey("errors"));
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("password"));
		context.assertTrue(result.containsKey("token"));
		setJWT(result.getString("token"));
	}

	public JsonObject assertLogin(TestContext context, String email, String password) {
		JsonObject login = new JsonObject().put("email", email).put("password", password);
		JsonObject result = post("/rest/login", login);
		debug("assertLogin", "" + !result.containsKey("errors"));
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("password"));
		context.assertTrue(result.containsKey("token"));
		context.assertEquals(email, result.getString("email"));

		setJWT(result.getString("token"));
		return result;
	}

	public JsonObject assertLoginError(TestContext context, String email, String password) {
		JsonObject login = new JsonObject().put("email", email).put("password", password);
		JsonObject result = post("/rest/login", login);
		debug("test", "loginError > " + result);
		context.assertTrue(result.containsKey("errors"), "assertLoginError() > login worked but should not!");
		context.assertTrue(!result.containsKey("_id"));
		context.assertTrue(!result.containsKey("password"));
		return result;
	}

	public App createApp(String name, boolean isPublic) {

		App app = new App();
		app.setName(name);
		app.setPublic(isPublic);
		app.setCreated(System.currentTimeMillis());
		app.setLastUpdate(System.currentTimeMillis());
		app.setScreenSize(375, 667);
	

		return app;

	}
	

	

	protected User createUser(String name) {

		User user = new User();
		user.setName(name);
		user.setLastname("Tester");
		user.setTos(true);
		user.setRole(User.USER);
		user.setEmail(name + "@quant-ux.de");

		return user;
	}

	protected User createUser(String name, SyncMongoClient client) {

		User user = new User();
		user.setName(name);
		user.setRole(User.USER);
		user.setEmail(name + "@quant-ux.de");

		JsonObject json = mapper.toVertx(user);

		String id = client.insert(user_db, json);
		user.setId(id);

		return user;
	}

	public void assertComments(String url, int x, TestContext context) {
		JsonArray comments = getList(url);
		context.assertEquals(x, comments.size());
		log("assertComments", "" + comments);

		for (int i = 0; i < comments.size(); i++) {
			JsonObject c = comments.getJsonObject(0);
			context.assertTrue(c.containsKey("user"));
		}
	}

	public void assertCommentsError(String url, TestContext context) {
		JsonArray comments = getList(url);
		context.assertEquals(401, comments.getInteger(0));
		log("assertCommentsError", "" + comments);
	}

	public Comment deleteComment(Comment comment, TestContext context) {

		JsonObject result = delete("/rest/comments/apps/" + comment.getAppID() + "/" + comment.getId() + ".json");
		log("deleteComment", "" + result);

		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(!result.containsKey("errors"));

		return comment;
	}

	public void deleteCommentError(Comment comment, TestContext context) {

		JsonObject result = delete("/rest/comments/apps/" + comment.getAppID() + "/" + comment.getId() + ".json");
		log("deleteCommentError", "" + result);

		context.assertTrue(result.containsKey("error") || result.containsKey("errors"));

	}

	public Comment updateComment(Comment comment, String message, TestContext context) {

		comment.setMessage(message);

		log("updateComment", "" + comment);
		JsonObject result = post("/rest/comments/apps/" + comment.getAppID() + "/" + comment.getId() + ".json",
				mapper.toVertx(comment));
		log("updateComment", "" + result);

		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("userID"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(result.containsKey("lastUpdate"));
		context.assertEquals(result.getString("message"), message);

		return comment;
	}

	public Comment updateCommentError(Comment comment, String message, TestContext context) {

		comment.setMessage(message);

		JsonObject result = post("/rest/comments/apps/" + comment.getAppID() + "/" + comment.getId() + ".json",
				mapper.toVertx(comment));
		log("updateCommentError", "" + result);

		context.assertTrue(result.containsKey("error") || result.containsKey("errors"));

		return comment;
	}

	public Comment postComment(App app, String type, String msg, String ref, TestContext context) {

		Comment comment = new Comment();
		comment.setAppID("Evil");
		comment.setMessage(msg);
		comment.setType(type);
		if (ref != null)
			comment.setReference(ref);

		JsonObject result = post("/rest/comments/apps/" + app.getId(), mapper.toVertx(comment));
		log("postComment", "" + result);

		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(result.containsKey("userID"));
		context.assertTrue(result.containsKey("appID"));
		context.assertTrue(result.containsKey("created"));
		context.assertEquals(app.getId(), result.getString("appID"));

		comment.setId(result.getString("_id"));
		comment.setAppID(result.getString("appID"));

		return comment;
	}

	public void postCommentError(App app, String type, String msg, String ref, TestContext context) {

		Comment comment = new Comment();
		comment.setAppID(app.getId());
		comment.setMessage(msg);
		comment.setType(type);
		if (ref != null)
			comment.setReference(ref);

		JsonObject result = post("/rest/comments/apps/" + app.getId(), mapper.toVertx(comment));
		log("postCommentError", "" + result);

		context.assertTrue(!result.containsKey("_id"));
		context.assertTrue(result.containsKey("error") || result.containsKey("errors"));

	}

	public JsonObject createApp() {
		JsonObject app = new JsonObject();

		app.put("name", "APP");

		JsonObject screens = new JsonObject();
		screens.put("s1", new JsonObject().put("id", "s1").put("children", new JsonArray().add("w1").add("w2"))
				.put("props", new JsonObject().put("start", true)));
		screens.put("s2", new JsonObject().put("id", "s2").put("props", new JsonObject()));
		screens.put("s3", new JsonObject().put("id", "s3").put("props", new JsonObject().put("start", false)));
		app.put("screens", screens);

		JsonObject widgets = new JsonObject();
		widgets.put("w1", new JsonObject().put("id", "w1"));
		widgets.put("w2", new JsonObject().put("id", "w2"));
		widgets.put("w3", new JsonObject().put("id", "w3"));
		widgets.put("w4", new JsonObject().put("id", "w4"));
		widgets.put("w5", new JsonObject().put("id", "w5"));

		JsonObject users = new JsonObject();
		users.put("klaus", Acl.OWNER);
		users.put("dennis", Acl.READ);

		JsonObject invitations = new JsonObject();
		invitations.put("123", Invitation.TEST);
		invitations.put("abc", Invitation.READ);

		app.put("widgets", widgets);
		app.put("lines", new JsonObject());
		app.put("groups", new JsonObject());
		app.put("users", users);
		app.put("invitations", invitations);
		return app;
	}
	
	
	public void postImage(App app, TestContext context, String name) {
		
		JsonObject result = postFile("/rest/images/"+app.getId(), "src/test/resources/" +name);
		log("postImage", result.encode());
		

		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(result.containsKey("uploads"));
		
		JsonArray uploads = result.getJsonArray("uploads");
		context.assertEquals(1, uploads.size());
		
		JsonObject upload = uploads.getJsonObject(0);
		context.assertNotNull(upload);
		context.assertTrue(upload.containsKey("id"));
		context.assertTrue(upload.getInteger("width") > 0);
		//context.assertTrue(upload.getInteger("width") <= Image.MAX_IMAGE_WIDTH * Image.SCALE_FACTOR );
		//context.assertTrue(upload.getInteger("width") <= app.getScreenSize().get("w").intValue() * Image.SCALE_FACTOR);
		
		context.assertTrue(upload.getInteger("height") > 0);
		
		
		sleep();
	}


	public void postImageError(App app, TestContext context) {
		
		JsonObject result = postFile("/rest/images/"+app.getId()+"/", "src/test/resources/test.png");
		log("postImage", result.encode());
		context.assertTrue(result.containsKey("errors") || result.containsKey("error"), app.toString());
		
	}
	

	public App setupApp(TestContext context) {
		User klaus = postUser("klaus", context);
		User bernd = postUser("bernd", context);
		
		assertLogin(context, klaus, "123456789");
		App app = postApp("klaus_app_public", true, context);
		

		postImage(app, context, "test.png");
		postImage(app, context, "test.png");
		JsonArray images = assertList("/rest/images/" + app.getId() + ".json", 2, context);

		
		/**
		 * Now add an images
		 */
		JsonObject fullApp = get("/rest/apps/"+ app.getId() + ".json");		
		addScreen(images, fullApp);
		addWidget(images, fullApp);		
		post("/rest/apps/"+ app.getId() + ".json", fullApp);
		
		/**
		 * Add commands
		 */
		assertStack(app, context);
		JsonObject command = new JsonObject().put("type", "AddScreen").put("new", new JsonObject().put("x",10)).put("id", "c1");
		command = postCommand(app, command, 1, 1, context);
		command = new JsonObject().put("type", "AddScreen").put("new", new JsonObject().put("x",10)).put("id", "c2");
		command = postCommand(app, command, 2, 2, context);
			
		/**
		 * Add events
		 */
		postEvent(app, "session1", "Click", context);
		postEvent(app, "session2", "Click", context);
		postEvent(app, "session3", "Click", context);
		postEvent(app, "session1", "Click", context);
		postEvent(app, "session1", "Click", context);
		assertList("/rest/events/" + app.getId() +".json", 5, context);
		
		/**
		 * Add ACL
		 */
		createPermission(bernd, app, Acl.READ, context);
		
		/**
		 * Add test settings
		 */
		JsonObject test = TestSetting.createEmpty(klaus, app.getId());
		post("/rest/test/" + app.getId() + ".json", test);
		
		/**
		 * Add comments
		 */
		postComment(app, "overview", "com 1", null, context);
		postComment(app, "overview", "com 2", null, context);
		postComment(app, "overview", "com 3", null, context);
		
		/**
		 * Add invitation
		 */
		getInvitation(app, context);
		
		/**
		 * Add Mouse
		 */
		fullApp = get("/rest/apps/"+ app.getId() + ".json");	
		log(4, "app", fullApp.encodePrettily());
		return app;
	}
	
	public JsonObject getInvitation(App app,TestContext context ){
		JsonObject result = get("/rest/invitation/" +app.getId() + ".json");
		log("getInvitation", ""+result);
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(!result.containsKey("errors"));
		return result;
	}

	public JsonObject postCommand(App app, JsonObject command, int expectedPos, int extpectedLength, TestContext context){
		JsonObject result = post("/rest/commands/" +app.getId()+"/add", command);
		
		log("postCommand", "result > " + result.encode());
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("command"));
		context.assertEquals(expectedPos, result.getInteger("pos"));
		
		JsonObject stack = get("/rest/commands/" + app.getId() + ".json");
		context.assertEquals(expectedPos, stack.getInteger("pos"));
		context.assertEquals(extpectedLength, stack.getJsonArray("stack").size());
		
		log("postCommand", "stack > "+  stack.encode());
		return result;
	}
	
	private void addScreen(JsonArray images, JsonObject fullApp) {
		JsonObject screens = new JsonObject();
		fullApp.put("screens", screens);
		
		JsonObject image = images.getJsonObject(0);
		
		JsonObject backgroundImage = new JsonObject()
				.put("w", image.getInteger("w"))
				.put("h", image.getInteger("h"))
				.put("url", image.getString("url"));
		
		JsonObject screenStyle = new JsonObject()
				.put("backgroundImage", backgroundImage);
				
				
		JsonObject screen = new JsonObject()
				.put("id", "s10000")
				.put("name", "Screen 1")
				.put("style", screenStyle);

		screens.put("s10000", screen);
	}


	private void addWidget(JsonArray images, JsonObject fullApp) {
		JsonObject widgets = new JsonObject();
		fullApp.put("widgets", widgets);
		
		JsonObject image = images.getJsonObject(0);
		
		JsonObject backgroundImage = new JsonObject()
				.put("w", image.getInteger("w"))
				.put("h", image.getInteger("h"))
				.put("url", image.getString("url"));
		
		JsonObject screenStyle = new JsonObject()
				.put("backgroundImage", backgroundImage);
				
				
		JsonObject widget = new JsonObject()
				.put("id", "w10001")
				.put("name", "Widget 1")
				.put("style", screenStyle);

		widgets.put("w10001", widget);
	}
	
	
	public void postEvent(App app, String session, String type, TestContext context){
		JsonObject event = new JsonObject()
			.put("session", session)
			.put("user", "user")
			.put("screen", "s1")
			.put("widget", "w1")
			.put("type", type)
			.put("user", "user")
			.put("time", System.currentTimeMillis())
			.put("x", 3)
			.put("y", 4);
		
		JsonObject result = post("/rest/events/" + app.getId() +".json", event);
		log("postEvent", ""+result);
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(!result.containsKey("errors"));
	
	}
	
	public void assertStack(App app, TestContext context){
		JsonObject stack = get("/rest/commands/" + app.getId() + ".json");
		log("assertStack", "get(stack) : " + stack);
		context.assertTrue(!stack.containsKey("error"));
		context.assertTrue(!stack.containsKey("errors"));
		context.assertEquals(stack.getString("appID"), app.getId());
	}


	public JsonObject postChanges(App app, JsonArray changes, TestContext context) {

		JsonObject result = post("/rest/apps/" +app.getId() + "/update", changes);
		context.assertTrue(!result.containsKey("error"), "Error contained");
		context.assertEquals("app.changes.succcess", result.getString("details"));

		JsonObject updateApp = client.findOne(app_db, App.findById(app.getId()));

		debug("postChanges", result.encode());
		log("postChanges", "App : " + updateApp.encodePrettily());

		return updateApp;
	}


	public JsonObject createChange(String type,String name, JsonObject newValue){
		return new JsonObject()
				.put("type", type)
				.put("name", name)
				.put("object", newValue);
	}

	public JsonObject createChange(String type,String name, JsonObject newValue,  String parent){
		return new JsonObject()
				.put("type", type)
				.put("parent", parent)
				.put("name", name)
				.put("object", newValue);
	}

	public JsonObject createChange(String type, String name,String newValue){
		return new JsonObject()
				.put("type", type)
				.put("name", name)
				.put("object", newValue);
	}


	public JsonObject createChange(String type, String name,int newValue){
		return new JsonObject()
				.put("type", type)
				.put("name", name)
				.put("object", newValue);
	}


	public void assertJsonPath(JsonObject object, String path, TestContext  context){
		JsonPath jp = new JsonPath(object);
		Object obs = jp.getValue(path);
		context.assertNotNull(obs, "Path '" + path + "' could not be macthed" );
	}

	public void assertJsonPathNull(JsonObject object, String path, TestContext  context){
		JsonPath jp = new JsonPath(object);
		Object obs = jp.getValue(path);
		context.assertNull(obs, "Path '" + path + "' could be macthed" );
	}


	public void assertJsonPath(JsonObject object, String path, int exp, TestContext  context){
		JsonPath jp = new JsonPath(object);
		int obs = jp.getInteger(path);
		context.assertEquals(exp,obs, "Path '" + path + "' could not be macthed" );
	}

}
