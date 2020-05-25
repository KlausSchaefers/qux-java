package com.qux.bus;

import com.qux.MATC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * A verticle which will just hook the ImageUploadHanlder to the bus. The only difference is that,
 * the verticle should run as a Worker as the PSD convertion might be quite slow...
 *
 * @author Klaus
 *
 */
public class ImageVerticle  extends AbstractVerticle {
	
	private final Logger logger = LoggerFactory.getLogger(ImageVerticle.class);

	private MongoClient client;
	
	private final JsonObject config;
	
	public ImageVerticle(MongoClient client, JsonObject config){
		this.client = client;
		this.config = config;
	}
	
	@Override
	public void start() {
		this.logger.info("start() > enter");
		
		EventBus eb = vertx.eventBus();

		String folder  = config.getString("image.folder.apps");
		eb.consumer(MATC.BUS_IMAGES_UPLOADED, new ImageUploadHandler(client, folder ));
			
		this.logger.info("start() > exit");

	}

}
