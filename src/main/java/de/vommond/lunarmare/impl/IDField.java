package de.vommond.lunarmare.impl;


public class IDField extends Field{

	public IDField(ModelImpl parent, String name) {
		super(parent, name);
	}
	
	public Type getType(){
		return Type.ID;
	}
		
	public IDField setOptional(){
		isRequired = false;
		return this;
	}
	
	public IDField setHidden(){
		hidden = true;
		return this;
	}
}
