package de.vommond.lunarmare.impl;


public class LongField extends Field{

	public LongField(ModelImpl parent, String name) {
		super(parent, name);
	}
	
	
	public Type getType(){
		return Type.Long;
	}
	

	
	public LongField setOptional(){
		isRequired = false;
		return this;
	}
	
	public LongField setHidden(){
		hidden = true;
		return this;
	}
}
