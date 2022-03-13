package com.qux.bus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.model.App;
import com.qux.util.DB;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.ext.mongo.MongoClient;

/**
 * Hacky class that does from time to time some stupid stuff to keep the VM hot
 * 
 * @author Klaus
 *
 */
public class VMHeater extends AbstractVerticle implements Handler<Long> {
	
	private final Logger logger = LoggerFactory.getLogger(VMHeater.class);

	private MongoClient client;	
	
	public VMHeater(MongoClient client){
		this.client = client;		
	}
	
	@Override
	public void start() {
		this.logger.info("start() > enter");
		
		vertx.setPeriodic(60*1000, (Handler<Long>) this);
			
		this.logger.info("start() > exit");

	}

	@Override
	public void handle(Long event) {
		this.logger.debug("handle() > enter");
		long start = System.currentTimeMillis();		
		touchMongo(start);	
	}

	/**
	 * Load some stuff from mongo
	 * @param start
	 */
	private void touchMongo(long start) {
		try {
			client.count(DB.getTable(App.class), App.findByIds(), res -> {
				if (res.succeeded()) {
					client.find(DB.getTable(App.class), App.findPublic(), res2 -> {
						if (res2.succeeded()) {
							touchCPU(start);
						} else {
							this.logger.error("touchMongo() > error finding public apps", res2.cause());
						}
					});
				} else {
					this.logger.error("touchMongo() > error > Count tables", res.cause());
				}
			});
		} catch (Exception es) {
			this.logger.error("touchMongo() > error > Something went wrong", es);
		}
	}

	/**
	 * Do some stupid calculations that will take some time
	 * @param start
	 */
	private void touchCPU(long start) {
		this.logger.debug("touchCPU() > enter");
		long end = System.currentTimeMillis();
		this.logger.info("handle() > exit > ms: " + (end-start));
	}

}
