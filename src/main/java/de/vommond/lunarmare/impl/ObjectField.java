package de.vommond.lunarmare.impl;



public class ObjectField extends Field {

	public ObjectField(ModelImpl parent, String name) {
		super(parent, name);
	}
	
	
	public Type getType(){
		return Type.Object;
	}
	


	public ObjectField setOptional(){
		isRequired = false;
		return this;
	}
	
	public ObjectField setHidden(){
		hidden = true;
		return this;
	}
}
