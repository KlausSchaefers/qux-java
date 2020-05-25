package com.qux.rest;

import java.util.Set;

import com.qux.acl.Acl;
import com.qux.acl.AppAcl;
import com.qux.model.AppEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.util.REST;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

public class ImportREST extends REST {
	
	private Logger logger = LoggerFactory.getLogger(ImageREST.class);
	
	private final String bus;
	
	private final Acl acl;

	public ImportREST(MongoClient db, String folder, String bus) {
		logger.debug("constructor() > enter bus : "+ bus);
		this.acl = new AppAcl(db);
		this.bus = bus;
		this.setIdParameter("appID");
	}
	
	
	
	public void importSketch(RoutingContext event){
		logger.debug("importSketch() > enter");
		
		JsonArray files = new JsonArray();
		Set<FileUpload> uploads = event.fileUploads();
		
		String appID = getId(event);
		acl.canWrite(getUser(event), event, res->{
			if(res){		
				importSketch(event, files, uploads, appID);
			} else {
				logger.error("importSketch() > User "+ getUser(event) +" tried to import sketch >> " + event.request().path());
				returnError(event, 404);
				deleteUploads(event, uploads);
			}
		});
	
	}
	
	private int readIntParam(RoutingContext event, String name, int defaultValue){
		int x = 0;
		if(event.request().getParam(name) != null){
			x = Integer.parseInt(event.request().getParam(name) );
		}
		return x;
	}


	private void importSketch(RoutingContext event, JsonArray files, Set<FileUpload> uploads, String appID) {
		
		AppEvent.send(event, getUser(event).getEmail(),AppEvent.TYPE_APP_IMPORT_SKETCH);
		
		for(FileUpload upload : uploads){
			files.add(upload.uploadedFileName());
		}
		
		int x = readIntParam(event, "x", 0);
		int y = readIntParam(event, "y", 0);
		
		JsonObject request = new JsonObject()
				.put("appID",  appID)
				.put("x", x)
				.put("y", y)
				.put("files", files);
		
		event.vertx().eventBus().request(this.bus, request, busRes ->{
			
			deleteUploads(event, uploads);
			
			if(busRes.succeeded()){
				
				Message<Object> message = busRes.result();
				Object obj = message.body();
				if(obj instanceof JsonObject){
					JsonObject app = (JsonObject)obj;
					returnJson(event, app);
				} else {
					logger.error("importSketch() > Bus returned shit " + event.request().path());
					returnError(event, 404);
				}
				
				
			} else {
				logger.error("importSketch() > Cannot process import " + event.request().path());
				returnError(event, 404);	
			}
		});
	}


	private void deleteUploads(RoutingContext event, Set<FileUpload> uploads) {
		FileSystem fs = event.vertx().fileSystem();
		for(FileUpload upload : uploads){
			fs.delete(upload.uploadedFileName(), deleted -> {
				if(!deleted.succeeded()){
					logger.error("importSketch() > Cannot delete > " + upload.uploadedFileName());
				}
			});
		}
	}
	

}
