package com.qux.bus;

import de.vommond.lunarmare.Model;
import de.vommond.lunarmare.ModelFactory;
import com.qux.model.AppEvent;
import com.qux.util.DB;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppEventHandler implements Handler<Message<JsonObject>> {

	private Logger logger = LoggerFactory.getLogger(AppEventHandler.class);
	
	/**
	 * EventBus Address
	 */
	public static final String APP_EVENT_BUS = "matc.app.events";
	
	
	private MongoClient client;
	
	private String table = DB.getTable(AppEvent.class);

	private Model validator;
	
	public static AppEventHandler create(Vertx vertx,MongoClient client){
		return new AppEventHandler(vertx, client);
	}
	
	
	private AppEventHandler(Vertx vertx,MongoClient client ){
		
		/**
		 * Link to event bus.
		 */
		EventBus eb = vertx.eventBus();
		eb.consumer(APP_EVENT_BUS, this);
		
		
		/**
		 * Create model for validation	
		 */
		validator = new ModelFactory().create("AppEvent")
				.addString(AppEvent.FIELD_USER)
				.addString(AppEvent.FIELD_TYPE)
				.addString(AppEvent.FIELD_SCREEN).setOptional()
				.addString(AppEvent.FIELD_WIDGET).setOptional()
				.addString(AppEvent.FIELD_VALUE).setOptional()
				.build();
		
		this.client = client;
	}
	
	

	@Override
	public void handle(Message<JsonObject> message) {
		
		JsonObject msg = message.body();
		
		handle(msg);	
			
	}
	

	public void handle(JsonObject msg) {
		if(validator.isValid(msg)){
			logger.info("handle() > " + msg.encode()); 
			msg.put(AppEvent.FIELD_CREATED, System.currentTimeMillis());
			client.save(table, msg, res->{
				if(!res.succeeded()){
					logger.error("handle() > Could not save in mongo");
				}
			});
			
		} else {
			logger.error("handle() > Data is crap");
		}
	}
}
