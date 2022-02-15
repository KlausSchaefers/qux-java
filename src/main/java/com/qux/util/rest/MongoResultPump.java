package com.qux.util.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class MongoResultPump implements Handler<AsyncResult<JsonObject>>{
	
	private final Logger logger = LoggerFactory.getLogger(MongoResultPump.class);
	
	private int count = 0;
	
	private final RoutingContext event;
	
	private MongoFilter<JsonObject, JsonObject> filter;
	
	private long start;
	
	public MongoResultPump(RoutingContext event){
		this(event, null);
	}
	
	public MongoResultPump(RoutingContext event, MongoFilter<JsonObject, JsonObject> filter){
		this.event = event;
		this.event.response().setChunked(true);
		this.event.response().write("[");
		this.filter = filter;
		this.start = System.currentTimeMillis();
	}
	
	public void pump(JsonObject json){
		if (filter != null){
			json = filter.filter(json);
		}
		if (count++ > 0) {
			event.response().write(",");
		}
		event.response().write(json.encode());
		count++;
	}
	
	public void end(){
		event.response().end("]");
		this.logger.info("end() > " + this.count + " took " + (System.currentTimeMillis() - this.start) + "ms");
	}

	public void error() {
		event.response().setStatusCode(405);
		event.response().end();
	}

	@Override
	public void handle(AsyncResult<JsonObject> res) {
		if(res.succeeded()){
			JsonObject json = res.result();
			if(json!= null){
				this.pump(json);
			} else {
				this.end();
			}
		} else {
			this.error();
		}
	}
}
