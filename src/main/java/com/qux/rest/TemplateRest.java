package com.qux.rest;

import java.util.List;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.util.REST;

public class TemplateRest extends REST {

	private Logger logger = LoggerFactory.getLogger(PasswordRest.class);
	
	private boolean disbaleCache = true;
	
	private String cache;
	
	public TemplateRest(boolean disbaleCache) {
		this.disbaleCache = disbaleCache;
		logger.info("constructor() > disbaleCache : " + this.disbaleCache);
	}

	public Handler<RoutingContext> get() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				get(event);
			}

			
		};
	}
	
	private void get(RoutingContext event) {
		
		if(cache== null || this.disbaleCache){
			logger.debug("get() > Read Templates from disk");
			
			JsonArray result = new JsonArray();
			
			FileSystem fileSystem = event.vertx().fileSystem();
			
			readFolder(result, fileSystem, "themes");

			logger.info("get() > Read Templates " +  result.size() +" files from disk");
			cache = result.encodePrettily();
		}
		
		
		if(!this.disbaleCache){
			event.response().putHeader("Cache-Control", "no-transform,public,max-age=86400,s-maxage=86401");
			event.response().putHeader("ETag", "asdasdasdasd");
		}
		
		event.response().end(cache);
	}
	
	
	private void readFolder(JsonArray result, FileSystem fileSystem, String folder) {
		List<String> files = fileSystem.readDirBlocking(folder);
		for(String file : files){
			if(file.endsWith(".js")){
				logger.debug("get() > file  : " + getFileName(file));
				readFile(result, fileSystem, file);
			} else {
				logger.debug("get() > folder :" + getFileName(file));
				this.readFolder(result, fileSystem, file);
			}
		}
	}

	private String getFileName(String file) {
		return file.substring(file.indexOf("themes"),file.length());
	}

	
	private void readFile(JsonArray result, FileSystem fileSystem, String file) {
		try{
			//InputStream is = this.getClass().getClassLoader().getResourceAsStream(getFileName(file));
			
			Buffer b = fileSystem.readFileBlocking(file);
			JsonArray templates = new JsonArray(b.toString());
		
			for(int i=0; i< templates.size(); i++){
				result.add(templates.getJsonObject(i));
			}
		} catch(Exception e){
			logger.error("get() > Error could not read " + file);
			e.printStackTrace();
		}
	}

}
