package de.vommond.lunarmare.impl;


public class DoubleField extends Field {

	public DoubleField(ModelImpl parent, String name) {
		super(parent, name);
	}

	public Type getType(){
		return Type.Double;
	}
	
	public DoubleField setOptional(){
		isRequired = false;
		return this;
	}
	
	public DoubleField setHidden(){
		hidden = true;
		return this;
	}
}
