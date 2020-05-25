package de.vommond.lunarmare.impl;


public class ArrayField extends Field{

	public ArrayField(ModelImpl parent, String name) {
		super(parent, name);
	}

	public ArrayField setOptional(){
		isRequired = false;
		return this;
	}
	
	public ArrayField setHidden(){
		hidden = true;
		return this;
	}
	
}
