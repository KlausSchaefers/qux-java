package de.vommond.lunarmare.impl;




public class IntegerField extends Field {

	public IntegerField(ModelImpl parent, String name) {
		super(parent, name);
	}
	
	public IntegerField setOptional(){
		isRequired = false;
		return this;
	}
	
	
	public Type getType(){
		return Type.Integer;
	}
	
	
	public IntegerField setHidden(){
		hidden = true;
		return this;
	}

}
