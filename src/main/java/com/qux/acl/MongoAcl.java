package com.qux.acl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.util.Mail;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.mongo.MongoClient;

public abstract class MongoAcl  {

	private Logger logger = LoggerFactory.getLogger(MongoAcl.class);

	protected final MongoClient client;
	
	protected final String id;
	
	public MongoAcl(MongoClient client ){
		this(client, "id");
	}
	
	public MongoAcl(MongoClient client, String id){
		this.client = client;
		this.id = id;
	}
	
	protected void assertOne(AsyncResult<Long> res, Handler<Boolean> handler, RoutingContext event){
		if(res.succeeded()){
			
			if(res.result() == 1l){
				handler.handle(true);
			} else if(res.result() >=1 ){
				logger.error("assertOne() > count bigger 1"); 
				Mail.error(event, "MongoACL.assertOne() > Found " + res.result() + " results. URL: " + event.request().absoluteURI());
				handler.handle(true);
			}else {
				logger.error("assertOne() > count is 0"); 
				handler.handle(false);
			}
		}else {
			logger.error("assertOne() > error",res.cause()); 
			handler.handle(false);
			res.cause().printStackTrace();
		}
	}
	
	protected String getId(RoutingContext event) {
		return event.request().getParam(id);
	}
	

}
