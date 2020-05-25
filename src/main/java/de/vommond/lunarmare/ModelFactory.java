package de.vommond.lunarmare;

import io.vertx.core.json.JsonObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.vommond.lunarmare.impl.ModelImpl;

public class ModelFactory {
	
	private Logger logger = LoggerFactory.getLogger(ModelFactory.class);
	
	private static Map<String, ModelImpl> models = new HashMap<>();

	public ModelBuilder create(String name){
		ModelImpl schema =  new ModelImpl(name);
		models.put(name, schema);
		return schema;
	}
	
	
	public List<String> validate(String name, JsonObject object){
		if(models.containsKey(name)){
			return get(name).validate(object);
		}
		logger.warn("validate() > The schema '" + name +"' is not managed. Return true!");
		return Collections.emptyList();
	}
	
	public Model get(String name){
		return models.get(name);
	}
}
