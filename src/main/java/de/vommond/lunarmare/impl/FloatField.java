package de.vommond.lunarmare.impl;


public class FloatField extends Field{

	public FloatField(ModelImpl parent, String name) {
		super(parent, name);
	}
	
	public Type getType(){
		return Type.Float;
	}
	

	
	public FloatField setOptional(){
		isRequired = false;
		return this;
	}
	
	public FloatField setHidden(){
		hidden = true;
		return this;
	}
}
