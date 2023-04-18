package com.qux.model;

import com.qux.bus.AppEventHandler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class AppEvent {
	
	/**
	 * Fields
	 */
	public static final String FIELD_USER = "user";
	
	public static final String FIELD_TYPE = "type";

	public static final String FIELD_SCREEN = "screen";

	public static final String FIELD_WIDGET = "widget";

	public static final String FIELD_CREATED = "created";
	
	public static final String FIELD_VALUE = "value";

	public static final String TYPE_USER_LOGIN = "USER_LOGIN";

	public static final String TYPE_USER_LOGOUT = "USER_LOGOUT";
	
	public static final String TYPE_USER_LOGIN_ERROR = "TYPE_USER_LOGIN_ERROR";
	
	public static final String TYPE_USER_SIGNUP = "USER_SIGNUP";

	public static final String TYPE_USER_RESET = "TYPE_USER_RESET";

	public static final String TYPE_USER_CHANGE_EMAIL = "USER_CHANGE_EMAIL";
	
	public static final String TYPE_USER_RETIRED = "USER_RETIRED";
	
	public static final String TYPE_USER_UPDATE_PRIVACY = "USER_UPDATE_PRIVACY";

	public static final String TYPE_APP_COPY = "APP_COPY";
	
	public static final String TYPE_APP_CREATE = "APP_CREATE";

	public static final String TYPE_APP_DELETE = "APP_DELETE";

	public static final String TYPE_APP_INVITATION_REST = "APP_INVITATION_RESTE";
	
	public static final String TYPE_APP_TEST = "APP_TEST";
		
	public static final String TYPE_APP_CREATE_PERMISSION ="TYPE_APP_CREATE_PERMISSION";
	
	public static final String TYPE_APP_COPY_PUBLIC ="TYPE_APP_COPY_PUBLIC";
	
	public static final String TYPE_APP_COPY_PRIVATE ="TYPE_APP_COPY_PRIVATE";
	
	public static final String TYPE_LIB_CREATE = "LIB_CREATE";

	
	public static void send(RoutingContext event, String email, String type){
		JsonObject appEvent =  new JsonObject()
				.put(FIELD_USER, email)
				.put(FIELD_CREATED, System.currentTimeMillis())
				.put(FIELD_TYPE, type);
		
		EventBus eb = event.vertx().eventBus();
		eb.send(AppEventHandler.APP_EVENT_BUS, appEvent);
	}
	
	public static void send(EventBus eb, String email, String type){
		JsonObject appEvent =  new JsonObject()
				.put(FIELD_USER, email)
				.put(FIELD_CREATED, System.currentTimeMillis())
				.put(FIELD_TYPE, type);
		
		eb.send(AppEventHandler.APP_EVENT_BUS, appEvent);
	}
	
	
	public static void send(RoutingContext event, String email, String type, String value){
		JsonObject appEvent =  new JsonObject()
				.put(FIELD_USER, email)
				.put(FIELD_CREATED, System.currentTimeMillis())
				.put(FIELD_TYPE, type)
				.put(FIELD_VALUE, value);
		
		EventBus eb = event.vertx().eventBus();
		eb.send(AppEventHandler.APP_EVENT_BUS, appEvent);
	}
	
	public static void send(EventBus eb, String email, String type, String value){
		JsonObject appEvent =  new JsonObject()
				.put(FIELD_USER, email)
				.put(FIELD_CREATED, System.currentTimeMillis())
				.put(FIELD_TYPE, type)
				.put(FIELD_VALUE, value);
	
		eb.send(AppEventHandler.APP_EVENT_BUS, appEvent);
	}
}
