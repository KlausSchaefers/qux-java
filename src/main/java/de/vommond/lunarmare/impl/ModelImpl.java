package de.vommond.lunarmare.impl;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

import de.vommond.lunarmare.Model;
import de.vommond.lunarmare.ModelBuilder;


public class ModelImpl implements Model, ModelBuilder {

	private final List<Field> fields = new ArrayList<Field>();
	
	private final String name;
	
	
	public ModelImpl(String name) {
		this.name = name;
		/**
		 * We always add here some meta data.
		 */
		this.fields.add(new IDField(this, ID).setOptional());
		this.fields.add(new LongField(this, CREATED).setOptional());
		this.fields.add(new LongField(this, UPDATE).setOptional());
		this.fields.add(new LongField(this, VERSION).setOptional());
	}
	
	public List<Field> getFields(){
		return fields;
	}

	@Override
	public String getName(){
		return name;
	}
	
	public void foreach(Handler<Field> handler){
		for(Field field : fields){
			handler.handle(field);
		}
	}
	
	public boolean isValid(JsonObject object){
		return validate(object, false).isEmpty();
	}
	
	
	@Override
	public List<String> validate(JsonObject object){
		return validate(object, false);
	}
	
	@Override
	public List<String> validate(JsonObject object, boolean partial){
		
		List<String> errors = new ArrayList<String>();
		
		
		if(partial){
			
			/**
			 * check only the fields that were in the json
			 */
			for(Field field : fields){
	
				if(object.containsKey(field.getName())){
					String valid = field.validate(object);
					if(valid != Field.VALID){
						errors.add(name +"."+ field.getName() +"."+ valid);
					}	
				}	
			}
		} else {
			
			/**
			 * check for every field
			 */
			for(Field field : fields){
				String valid = field.validate(object);
				if(valid != Field.VALID){
					errors.add(name +"."+ field.getName() +"."+ valid);
				}		
			}
			
		}
		
		
		
		return errors;
	}

	
	@Override
	public IntegerField addInteger(String name){
		IntegerField field = new IntegerField(this, name);
		this.fields.add(field);
		return field;
	}
	
	
	@Override
	public DoubleField addDouble(String name){
		DoubleField field = new DoubleField(this, name);
		this.fields.add(field);
		return field;
	}
	
	@Override
	public StringField addString(String name){
		StringField field = new StringField(this, name);
		this.fields.add(field);
		return field;
	}
	
	
	@Override
	public LongField addLong(String name){
		LongField field = new LongField(this, name);
		this.fields.add(field);
		return field;
	}
	
	
	@Override
	public DateField addDate(String name){
		DateField field = new DateField(this, name);
		this.fields.add(field);
		return field;
	}

	
	@Override
	public ObjectField addObject(String name){
		ObjectField field = new ObjectField(this, name);
		this.fields.add(field);
		return field;
	}

	
	@Override
	public ArrayField addArray(String name){
		ArrayField field = new ArrayField(this, name);
		this.fields.add(field);
		return field;
	}
	
	
	@Override
	public IntArrayField addIntArray(String name){
		IntArrayField field = new IntArrayField(this, name);
		this.fields.add(field);
		return field;
	}
	
	

	@Override
	public FloatField addFloat(String name){
		FloatField field = new FloatField(this, name);
		this.fields.add(field);
		return field;
	}

	@Override
	public BooleanField addBoolean(String name){
		BooleanField field = new BooleanField(this, name);
		this.fields.add(field);
		return field;
	}

	@Override
	public Model build() {
		return this;
	}


	
	@Override
	public JsonObject write(JsonObject object) {
		JsonObject result = new JsonObject();

		foreach(field -> {
			String key = field.getName();
			/**
			 * if the field is not hidden and it exists in the other 
			 * object, we will copy it.
			 */
		
			if(!field.isHidden() && object.containsKey(key)){
				result.put(key, object.getValue(key));	
			}
		});
		
		return result;
	}
	
	@Override
	public void write(JsonObject object, RoutingContext event) {
		JsonObject result = write(object);
		event.response().end(result.encode());
	}

	@Override
	public JsonObject read(JsonObject object) {
		JsonObject result = new JsonObject();

		foreach(field -> {
			String key = field.getName();
			/**
			 * if the field is not hidden and it exists in the other 
			 * object, we will copy it.
			 */
		
			if(object.containsKey(key)){
				result.put(key, field.read(object.getValue(key)));	
			}
		});
		
		return result;
	}

	@Override
	public JsonObject read(RoutingContext event) {
		JsonObject object = event.getBodyAsJson();
		return read(object);
	}

}
