package de.vommond.lunarmare.impl;


public class DateField extends Field{

	public DateField(ModelImpl parent, String name) {
		super(parent, name);
	}
	
	public Type getType(){
		return Type.Date;
	}
	

	public DateField setOptional(){
		isRequired = false;
		return this;
	}
	
	public DateField setHidden(){
		hidden = true;
		return this;
	}
}
