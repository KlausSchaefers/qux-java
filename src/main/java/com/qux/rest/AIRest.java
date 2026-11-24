package com.qux.rest;

import com.qux.acl.Acl;
import com.qux.acl.AgentACL;
import com.qux.acl.AppAcl;
import com.qux.acl.ImageACL;
import com.qux.auth.ITokenService;
import com.qux.model.*;
import com.qux.util.DB;

import com.qux.util.rest.MongoREST;
import com.qux.util.Util;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.util.*;
import java.util.regex.Pattern;

public class AIRest extends MongoREST {

	private final String app_db;

	private final String token_db;

	private final AppAcl appAcl;

	private final AgentACL agentAcl;

	private final ImageACL imageAcl;

	private final ImageREST image;

	private final String inv_db;

	/**
	 * The ids of the model become Mongo keys ("widgets.<id>"), so no dots
	 * and no dollars.
	 */
	private static final Pattern MODEL_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

	private static final List<String> MODEL_PARTS = Arrays.asList("screens", "widgets", "groups", "lines", "designtokens");

	static final int MAX_SCREENS = 50;

	static final int MAX_WIDGETS = 10000;

	static final int MAX_TOKENS = 500;

	static final int MAX_BODY = 20 * 1024 * 1024;

	static final int MIN_SCREEN_SIZE = 100;

	static final int MAX_SCREEN_SIZE = 5000;

	public AIRest(ITokenService tokenService, MongoClient db, ImageREST image) {
		super(tokenService, db, App.class);
		this.app_db = DB.getTable(App.class);
		this.token_db = DB.getTable(AgentToken.class);
		this.appAcl = new AppAcl(db);
		this.agentAcl = new AgentACL(db);
		this.imageAcl = new ImageACL(db);
		this.image = image;
		this.inv_db = DB.getTable(Invitation.class);
	}

	/********************************************************************************************
	 * Per user, authenticated with an agent token. The JWT of the web app is
	 * refused, agents and the web app do not share routes.
	 *
	 *   GET /rest/ai/me.json
	 *   GET /rest/ai/apps.json
	 *   GET /rest/ai/apps/:appID.json
	 *   GET /rest/ai/apps/:appID/images/:image
	 *
	 * With the scope apps:write (see AgentACL):
	 *
	 *   POST /rest/ai/apps.json
	 *   POST /rest/ai/apps/:appID/screens.json
	 *   POST /rest/ai/apps/:appID/images.json
	 *
	 * Everything an agent can see is checked against the Team ACL (or a
	 * public app) and the apps the token is restricted to.
	 ********************************************************************************************/

	public void me(RoutingContext event) {
		agentAcl.canRead(event, allowed -> {
			if (allowed) {
				getMe(event);
			}
		});
	}

	private void getMe(RoutingContext event) {
		User user = getAgent(event);
		if (user == null) {
			return;
		}
		JsonObject result = new JsonObject()
				.put("id", user.getId())
				.put("email", user.getEmail())
				.put("name", user.getName())
				.put("lastname", user.getLastname())
				.put("agent", true);
		mongo.findOne(token_db, App.findById(user.getAgentTokenID()), null, res -> {
			JsonObject token = res.succeeded() ? res.result() : null;
			if (token != null) {
				result.put("token", new JsonObject()
						.put("name", token.getString(AgentToken.NAME))
						.put("clientID", token.getString(AgentToken.CLIENT_ID))
						.put("appIDs", token.getJsonArray(AgentToken.APP_IDS))
						.put("resource", token.getString(AgentToken.RESOURCE))
						.put("expiresAt", token.getLong(AgentToken.EXPIRES_AT)));
			}
			returnJson(event, result);
		});
	}

	public void listApps(RoutingContext event) {
		agentAcl.canRead(event, allowed -> {
			if (allowed) {
				doListApps(event);
			}
		});
	}

	private void doListApps(RoutingContext event) {
		User user = getAgent(event);
		if (user == null) {
			return;
		}
		mongo.find(team_db, Team.findByUser(user), res -> {
			if (res.failed()) {
				returnError(event, 500);
				return;
			}
			Map<String, Integer> permissions = new HashMap<>();
			JsonArray appIDs = new JsonArray();
			for (JsonObject team : res.result()) {
				String appID = team.getString(Team.APP_ID);
				if (appID != null && user.isAppAllowed(appID) && !permissions.containsKey(appID)) {
					permissions.put(appID, team.getInteger(Team.PERMISSION, Acl.READ));
					appIDs.add(appID);
				}
			}
			FindOptions options = new FindOptions()
					.setFields(App.getFields("name", "description", "type", "screenSize", "lastUpdate", "created", "isDeleted", "screens"))
					.setSort(new JsonObject().put("lastUpdate", -1));
			mongo.findWithOptions(app_db, App.findByIDS(appIDs), options, apps -> {
				if (apps.failed()) {
					returnError(event, 500);
					return;
				}
				JsonArray result = new JsonArray();
				for (JsonObject app : apps.result()) {
					if (App.isDeleted(app)) {
						continue;
					}
					String id = app.getString("_id");
					JsonObject screens = app.getJsonObject("screens", new JsonObject());
					result.add(new JsonObject()
							.put("id", id)
							.put("name", app.getString("name"))
							.put("description", app.getString("description"))
							.put("type", app.getString("type"))
							.put("screenSize", app.getJsonObject("screenSize"))
							.put("screenCount", screens.size())
							.put("permission", toPermission(permissions.get(id)))
							.put("created", app.getLong("created", 0L))
							.put("lastUpdate", app.getLong("lastUpdate", 0L)));
				}
				returnJson(event, result);
			});
		});
	}

	public void getApp(RoutingContext event) {
		agentAcl.canRead(event, allowed -> {
			if (allowed) {
				withApp(event, null, app -> returnJson(event, cleanJson(app)));
			}
		});
	}

	/**
	 * A new, empty app of the user, like the "Create app" dialog of the web
	 * app makes it (ModelFactory.createAppModel), optionally with the design
	 * tokens of the codebase the agent replicates.
	 *
	 * Body: { name, description?, type?, screenSize: {w, h}, designtokens? }
	 */
	public void createApp(RoutingContext event) {
		agentAcl.canCreate(event, allowed -> {
			if (allowed) {
				doCreateApp(event);
			}
		});
	}

	private void doCreateApp(RoutingContext event) {
		User user = getAgent(event);
		if (user == null) {
			return;
		}
		// a token for some apps only must not make more
		if (user.getAgentAppIDs() != null) {
			returnJsonError(event, 403, "app.create.forbidden", "The token is restricted to some apps and cannot create apps");
			return;
		}
		JsonObject body = getJson(event);
		if (body == null) {
			returnJsonError(event, 400, "app.create.invalid", "The body must be JSON");
			return;
		}
		String name = body.getValue("name") instanceof String ? body.getString("name").trim() : "";
		if (name.isEmpty() || name.length() > 100) {
			returnJsonError(event, 400, "app.create.invalid", "The name must have 1 to 100 characters");
			return;
		}
		JsonObject size = body.getValue("screenSize") instanceof JsonObject ? body.getJsonObject("screenSize") : null;
		if (size == null || !isScreenSize(size.getValue("w")) || !isScreenSize(size.getValue("h"))) {
			returnJsonError(event, 400, "app.create.invalid", "screenSize needs w and h between " + MIN_SCREEN_SIZE + " and " + MAX_SCREEN_SIZE);
			return;
		}
		JsonObject screenSize = new JsonObject()
				.put("w", ((Number) size.getValue("w")).intValue())
				.put("h", ((Number) size.getValue("h")).intValue());

		JsonObject tokens = body.getValue("designtokens") instanceof JsonObject ? body.getJsonObject("designtokens") : new JsonObject();
		String tokenError = validateParts(new JsonObject().put("designtokens", tokens), new JsonObject());
		if (tokenError != null) {
			returnJsonError(event, 400, "app.create.invalid", tokenError);
			return;
		}

		long now = System.currentTimeMillis();
		JsonObject app = new JsonObject()
				.put("version", 4)
				.put("name", name)
				.put("description", body.getValue("description") instanceof String ? body.getString("description") : "")
				.put("screenSize", screenSize)
				.put("type", toAppType(body.getValue("type") instanceof String ? body.getString("type") : null, screenSize.getInteger("w")))
				.put("screens", new JsonObject())
				.put("widgets", new JsonObject())
				.put("lines", new JsonObject())
				.put("groups", new JsonObject())
				.put("templates", new JsonObject())
				.put("designtokens", tokens)
				.put("lastUUID", 10000)
				.put("lastUpdate", now)
				.put("created", now)
				.put("startScreen", "")
				.put("domain", event.request().scheme() + "://" + event.request().host())
				.put("grid", new JsonObject()
						.put("w", 8)
						.put("h", 8)
						.put("style", "line")
						.put("color", "#cecece")
						.put("visible", false)
						.put("enabled", false));

		mongo.insert(app_db, app, res -> {
			if (res.failed()) {
				logger.error("createApp() > could not save app", res.cause());
				returnError(event, 500);
				return;
			}
			String appID = res.result();
			mongo.insert(team_db, Team.create(user, appID, Acl.OWNER), team -> {
				if (team.failed()) {
					logger.error("createApp() > could not add owner", team.cause());
					returnError(event, 500);
					return;
				}
				addInvitation(appID, Invitation.TEST);
				addInvitation(appID, Invitation.READ);
				addInvitation(appID, Invitation.WRITE);
				AppEvent.send(event, user.getEmail(), AppEvent.TYPE_APP_CREATE, appID);
				logger.info("createApp() > " + user.getEmail() + " created " + appID);
				returnJson(event, new JsonObject()
						.put("id", appID)
						.put("name", name)
						.put("type", app.getString("type"))
						.put("screenSize", screenSize)
						.put("designtokens", tokens.size()));
			});
		});
	}

	/**
	 * Adds screens with their widgets, groups, lines and new design tokens
	 * to an app. The ids and positions are final (the MCP server computes
	 * them with the code of the editor), they must not exist in the app yet.
	 * Every item is written with its own key ($set "widgets.<id>"), like the
	 * editor saves its changes, so a user editing the app at the same time
	 * keeps their changes. Existing design tokens are never replaced.
	 *
	 * Body: { screens, widgets, groups?, lines?, designtokens?, lastUUID?, startScreen? }
	 */
	public void addScreens(RoutingContext event) {
		agentAcl.canWrite(event, allowed -> {
			if (allowed) {
				doAddScreens(event);
			}
		});
	}

	private void doAddScreens(RoutingContext event) {
		User user = getAgent(event);
		if (user == null) {
			return;
		}
		String appID = getId(event, "appID");
		if (appID == null || !user.isAppAllowed(appID)) {
			returnForbidden(event, appID);
			return;
		}
		if (event.getBody() != null && event.getBody().length() > MAX_BODY) {
			returnJsonError(event, 413, "app.screens.too.big", "The body is bigger than " + (MAX_BODY / 1024 / 1024) + " MB, send fewer screens per call");
			return;
		}
		JsonObject fragment = getJson(event);
		if (fragment == null) {
			returnJsonError(event, 400, "app.screens.invalid", "The body must be JSON");
			return;
		}
		mongo.count(team_db, Team.canWrite(user, appID), acl -> {
			if (acl.failed() || acl.result() < 1) {
				returnForbidden(event, appID);
				return;
			}
			mongo.findOne(app_db, App.findById(appID), null, res -> {
				JsonObject app = res.succeeded() ? res.result() : null;
				if (app == null || App.isDeleted(app)) {
					returnNotFound(event, "app.not.found", "No app " + appID);
					return;
				}
				String error = validateFragment(fragment, app);
				if (error != null) {
					returnJsonError(event, 400, "app.screens.invalid", error);
					return;
				}
				JsonObject update = toUpdate(fragment, app);
				mongo.updateCollection(app_db, App.findById(appID), update, saved -> {
					if (saved.failed()) {
						logger.error("addScreens() > could not save " + appID, saved.cause());
						returnError(event, 500);
						return;
					}
					App.onUpdate(event, appID);
					JsonObject screens = fragment.getJsonObject("screens");
					long tokens = update.getJsonObject("$set").fieldNames().stream().filter(k -> k.startsWith("designtokens.")).count();
					logger.info("addScreens() > " + user.getEmail() + " added " + screens.size() + " screens to " + appID);
					returnJson(event, new JsonObject()
							.put("appID", appID)
							.put("screens", new JsonArray(Arrays.asList(screens.fieldNames().toArray())))
							.put("widgets", size(fragment, "widgets"))
							.put("designtokens", tokens));
				});
			});
		});
	}

	/**
	 * null when the fragment can be added to the app, else what is wrong.
	 */
	static String validateFragment(JsonObject fragment, JsonObject app) {
		if (!(fragment.getValue("screens") instanceof JsonObject) || fragment.getJsonObject("screens").isEmpty()) {
			return "screens is missing";
		}
		if (size(fragment, "screens") > MAX_SCREENS) {
			return "At most " + MAX_SCREENS + " screens per call";
		}
		if (size(fragment, "widgets") > MAX_WIDGETS) {
			return "At most " + MAX_WIDGETS + " widgets per call";
		}
		String error = validateParts(fragment, app);
		if (error != null) {
			return error;
		}

		JsonObject widgets = fragment.getJsonObject("widgets", new JsonObject());
		JsonObject screens = fragment.getJsonObject("screens");
		JsonObject groups = fragment.getJsonObject("groups", new JsonObject());
		for (String id : screens.fieldNames()) {
			JsonObject screen = screens.getJsonObject(id);
			if (!hasBox(screen)) {
				return "The screen " + id + " needs x, y, w and h";
			}
			String missing = findMissing(screen.getValue("children"), widgets);
			if (missing != null) {
				return "The screen " + id + " has the child " + missing + ", which is not in widgets";
			}
		}
		for (String id : widgets.fieldNames()) {
			JsonObject widget = widgets.getJsonObject(id);
			if (!(widget.getValue("type") instanceof String) || !hasBox(widget)) {
				return "The widget " + id + " needs a type, x, y, w and h";
			}
		}
		for (String id : groups.fieldNames()) {
			JsonObject group = groups.getJsonObject(id);
			String missing = findMissing(group.getValue("children"), widgets);
			if (missing == null) {
				missing = findMissing(group.getValue("groups"), groups);
			}
			if (missing != null) {
				return "The group " + id + " has the member " + missing + ", which is not in the fragment";
			}
		}
		JsonObject lines = fragment.getJsonObject("lines", new JsonObject());
		JsonObject appScreens = app.getJsonObject("screens", new JsonObject());
		for (String id : lines.fieldNames()) {
			JsonObject line = lines.getJsonObject(id);
			String from = line.getValue("from") instanceof String ? line.getString("from") : null;
			String to = line.getValue("to") instanceof String ? line.getString("to") : null;
			if (from == null || !widgets.containsKey(from)) {
				return "The line " + id + " must start at a widget of the fragment";
			}
			if (to == null || !(screens.containsKey(to) || appScreens.containsKey(to) || widgets.containsKey(to))) {
				return "The line " + id + " goes to " + to + ", which is no screen of the app";
			}
		}
		return null;
	}

	/**
	 * Every part is an object of objects keyed by their id, and no id but
	 * the one of a design token may exist in the app yet.
	 */
	static String validateParts(JsonObject fragment, JsonObject app) {
		for (String part : MODEL_PARTS) {
			Object value = fragment.getValue(part);
			if (value == null) {
				continue;
			}
			if (!(value instanceof JsonObject)) {
				return part + " must be an object keyed by id";
			}
			JsonObject items = (JsonObject) value;
			if ("designtokens".equals(part) && items.size() > MAX_TOKENS) {
				return "At most " + MAX_TOKENS + " design tokens";
			}
			JsonObject existing = app.getJsonObject(part, new JsonObject());
			for (String id : items.fieldNames()) {
				if (!MODEL_ID.matcher(id).matches()) {
					return "The id " + id + " in " + part + " may only have letters, digits, _ and -";
				}
				if (!(items.getValue(id) instanceof JsonObject)) {
					return part + "." + id + " must be an object";
				}
				Object ownId = items.getJsonObject(id).getValue("id");
				if (ownId != null && !id.equals(ownId)) {
					return part + "." + id + " has the id " + ownId;
				}
				// a design token the app has is kept, see toUpdate()
				if (existing.containsKey(id) && !"designtokens".equals(part)) {
					return "The id " + id + " exists already in " + part;
				}
			}
		}
		return null;
	}

	static JsonObject toUpdate(JsonObject fragment, JsonObject app) {
		JsonObject set = new JsonObject();
		for (String part : MODEL_PARTS) {
			JsonObject items = fragment.getJsonObject(part, new JsonObject());
			JsonObject existing = app.getJsonObject(part, new JsonObject());
			for (String id : items.fieldNames()) {
				if (!existing.containsKey(id)) {
					set.put(part + "." + id, items.getJsonObject(id));
				}
			}
		}
		String start = fragment.getValue("startScreen") instanceof String ? fragment.getString("startScreen") : null;
		if (start != null && fragment.getJsonObject("screens").containsKey(start) && app.getString("startScreen", "").isEmpty()) {
			set.put("startScreen", start);
		}
		set.put("lastUpdate", System.currentTimeMillis());
		JsonObject update = new JsonObject().put("$set", set);
		if (fragment.getValue("lastUUID") instanceof Number) {
			update.put("$max", new JsonObject().put("lastUUID", ((Number) fragment.getValue("lastUUID")).longValue()));
		}
		return update;
	}

	private static String findMissing(Object ids, JsonObject in) {
		if (ids == null) {
			return null;
		}
		if (!(ids instanceof JsonArray)) {
			return String.valueOf(ids);
		}
		Set<Object> seen = new HashSet<>();
		for (Object id : (JsonArray) ids) {
			if (!(id instanceof String) || !in.containsKey((String) id) || !seen.add(id)) {
				return String.valueOf(id);
			}
		}
		return null;
	}

	private static boolean hasBox(JsonObject item) {
		return item.getValue("x") instanceof Number && item.getValue("y") instanceof Number
				&& item.getValue("w") instanceof Number && item.getValue("h") instanceof Number;
	}

	private static int size(JsonObject fragment, String part) {
		return fragment.getValue(part) instanceof JsonObject ? fragment.getJsonObject(part).size() : 0;
	}

	private static boolean isScreenSize(Object value) {
		return value instanceof Number && ((Number) value).intValue() >= MIN_SCREEN_SIZE && ((Number) value).intValue() <= MAX_SCREEN_SIZE;
	}

	/**
	 * The type the "Create app" dialog would pick for the size.
	 */
	static String toAppType(String type, int width) {
		if ("smartphone".equals(type) || "tablet".equals(type) || "desktop".equals(type)) {
			return type;
		}
		if (width < 600) {
			return "smartphone";
		}
		return width < 1100 ? "tablet" : "desktop";
	}

	private void addInvitation(String appID, int permission) {
		mongo.insert(inv_db, Invitation.create(Util.getRandomString(), appID, permission), res -> {
			if (res.failed()) {
				logger.error("addInvitation() > could not add invitation " + permission, res.cause());
			}
		});
	}


	/**
	 * Loads the app of :appID after the ACL said yes.
	 */
	private void withApp(RoutingContext event, JsonObject fields, Handler<JsonObject> handler) {
		User user = getAgent(event);
		if (user == null) {
			return;
		}
		String appID = getId(event, "appID");
		if (appID == null || !user.isAppAllowed(appID)) {
			returnForbidden(event, appID);
			return;
		}
		appAcl.canRead(user, event, appID, allowed -> {
			if (!allowed) {
				returnForbidden(event, appID);
				return;
			}
			mongo.findOne(app_db, App.findById(appID), fields, res -> {
				JsonObject app = res.succeeded() ? res.result() : null;
				if (app == null || App.isDeleted(app)) {
					returnNotFound(event, "app.not.found", "No app " + appID);
					return;
				}
				handler.handle(app);
			});
		});
	}

	/**
	 * Uploads images to an app (multipart, like POST /rest/images/:appID of the
	 * web app). The user needs write access to the app.
	 */
	public void addImage(RoutingContext event) {
		agentAcl.canWrite(event, allowed -> {
			User user = allowed ? getAgent(event) : null;
			if (user == null) {
				return;
			}
			String appID = getId(event, "appID");
			imageAcl.canWrite(user, event, canWrite -> {
				if (canWrite) {
					image.setImage(event, appID);
				} else {
					returnForbidden(event, appID);
				}
			});
		});
	}

	/**
	 * Downloads an image of an app the user can read.
	 */
	public void getImage(RoutingContext event) {
		agentAcl.canRead(event, allowed -> {
			User user = allowed ? getAgent(event) : null;
			if (user == null) {
				return;
			}
			String appID = getId(event, "appID");
			imageAcl.canRead(user, event, canRead -> {
				if (canRead) {
					image.getImage(event, appID, event.request().getParam("image"));
				} else {
					returnForbidden(event, appID);
				}
			});
		});
	}

	private User getAgent(RoutingContext event) {
		/*
		 * An agent token, resolved by the AgentACL
		 */
		User agentUser = event.get(AgentACL.AGENT_USER);
		if (agentUser == null || agentUser.isGuest() || agentUser.hasClaim()) {
			AgentACL.unauthorized(event, null, "Log in with an agent token, see /.well-known/oauth-authorization-server");
			return null;
		}
		return agentUser;
	}

	private void returnForbidden(RoutingContext event, String appID) {
		event.response()
				.putHeader("content-type", "application/json")
				.setStatusCode(403)
				.end(new JsonObject()
						.put("error", "app.forbidden")
						.put("error_description", "No access to the app " + appID + ". Call apps.json for the apps you can access.")
						.encode());
	}

	private void returnJsonError(RoutingContext event, int status, String error, String description) {
		event.response()
				.putHeader("content-type", "application/json")
				.setStatusCode(status)
				.end(new JsonObject().put("error", error).put("error_description", description).encode());
	}

	private void returnNotFound(RoutingContext event, String error, String description) {
		event.response()
				.putHeader("content-type", "application/json")
				.setStatusCode(404)
				.end(new JsonObject().put("error", error).put("error_description", description).encode());
	}

	private static String toPermission(Integer permission) {
		if (permission == null) {
			return "read";
		}
		if (permission >= Acl.OWNER) {
			return "owner";
		}
		if (permission >= Acl.WRITE) {
			return "write";
		}
		return "read";
	}

}
