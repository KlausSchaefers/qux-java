package com.qux.bus;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.vommond.lunarmare.util.MonitoringVerticle;
import com.qux.model.PerformanceEvent;
import com.qux.util.DB;

public class PerformanceHandler implements Handler<Message<JsonObject>> {

	private Logger logger = LoggerFactory.getLogger(MailHandler.class);
	
	private MongoClient client;
	
	private String table = DB.getTable(PerformanceEvent.class);
	
	private static PerformanceHandler instance;
	
	public static synchronized PerformanceHandler start(Vertx vertx, MongoClient client){
		if(instance == null){
			instance =new PerformanceHandler(vertx, client);
		}
		return instance;
	}
	
	
	private PerformanceHandler(Vertx vertx, MongoClient client){
		
		this.client = client;
		
		MonitoringVerticle monitor = new MonitoringVerticle();
		vertx.deployVerticle(monitor, h ->{
			if(h.succeeded()){
				vertx.eventBus().consumer(MonitoringVerticle.TOPIC, this);
			} else {
				logger.error("constructor() > Could not deploy MonitoringVerticle");
			}
		});
		
		
	}
	
	@Override
	public void handle(Message<JsonObject> event) {
		JsonObject msg = event.body();
		logger.info("onMessage() > CPU : " + msg.getDouble(MonitoringVerticle.SYSTEM_CPU_LOAD) + " > Java : " + msg.getDouble(MonitoringVerticle.PROCESS_CPU_LOAD));
		
		client.insert(table, msg, res ->{
			if(!res.succeeded()){
				logger.error("handle() > Could not write to mongo");
			}
		});
	}

}
