package com.qux.model;

import de.vommond.lunarmare.util.MonitoringVerticle;
import io.vertx.core.json.JsonObject;

public class PerformanceEvent extends Model {

	
	public static JsonObject newerThan(long date){
		 return new JsonObject()
	    	.put(MonitoringVerticle.CREATED,  new JsonObject().put("$gte", date));
	}
	
	

	public static JsonObject olderThan(long date){
		 return new JsonObject()
	    	.put(MonitoringVerticle.CREATED,  new JsonObject().put("$lte", date));
	}
}
