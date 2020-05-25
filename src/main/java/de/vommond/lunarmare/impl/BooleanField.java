package de.vommond.lunarmare.impl;


public class BooleanField extends Field{

	public BooleanField(ModelImpl parent, String name) {
		super(parent, name);
	}

	public Type getType(){
		return Type.Boolean;
	}
	
	public BooleanField setOptional(){
		isRequired = false;
		return this;
	}
	
	public BooleanField setHidden(){
		hidden = true;
		return this;
	}
}
