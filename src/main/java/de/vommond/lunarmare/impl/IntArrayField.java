package de.vommond.lunarmare.impl;


public class IntArrayField extends Field{

	public IntArrayField(ModelImpl parent, String name) {
		super(parent, name);
	}

	
	public Type getType(){
		return Type.IntArray;
	}
	
	
	public IntArrayField setOptional(){
		isRequired = false;
		return this;
	}
	
	public IntArrayField setHidden(){
		hidden = true;
		return this;
	}
	
}
