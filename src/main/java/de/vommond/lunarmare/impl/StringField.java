package de.vommond.lunarmare.impl;

import io.vertx.core.json.JsonObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringField extends Field{

	private Pattern pattern;
	
	private int minLength = -1;
	
	private int maxLength = -1;
	
	private FieldFunction<String> readFunction;
	
	
	public StringField(ModelImpl parent, String name) {
		super(parent, name);
	}
	
	public Type getType(){
		return Type.String;
	}

	public StringField setOptional(){
		isRequired = false;
		return this;
	}
	
	public StringField setHidden(){
		hidden = true;
		return this;
	}
	
	public Object read(Object value){
		if(readFunction!=null){
			return readFunction.call((String) value);
		} else {
			return value;
		}
	}
	
	/**
	 * A function that is executed when an unknown object is processed through the 
	 * Model.read() method. For instance a password might be automatically hashed!
	 * 
	 * @param readFunction
	 */
	public StringField setTransform(FieldFunction<String> readFunction){
		this.readFunction = readFunction;
		return this;
	}
	
	public StringField addPattern(String pattern){
		this.pattern = Pattern.compile(pattern);
		return this;
	}
	
	public StringField setMinLenth(int length){
		this.minLength = length;
		return this;
	}
	
	public StringField setMaxLenth(int length){
		this.maxLength = length;
		return this;
	}
	
	
	public String validateMore(JsonObject object){
		
		String value = object.getString(name);
		
		if(value != null){
			
			if(this.minLength >=0 && value.length()< this.minLength){
				return ERROR_MIN;
			}
			
			if(this.maxLength >=0 && value.length() > this.maxLength){	
				return ERROR_MAX;
			}
			
			if(this.pattern!=null){
				Matcher matcher =  pattern.matcher(value);
				if(!matcher.matches()){
					return ERROR_PATTERN;
				}
			} 
				
			return VALID;
			
	
		}
		else {
			return ERROR_NULL;
		}
		
		

	
	}

}
