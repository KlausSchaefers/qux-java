package com.qux.model;

import io.vertx.core.json.JsonObject;


public class Comment extends AppPart {
	
	public static final String TYPE_CANVAS = "ScreenComment";
	

	private String type;
	
	private String reference;
	
	private String parent;
	
	private int x,y,w,h = 0;
	
	private String annotation;
	
	private String message;
		
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public long getCreated() {
		return created;
	}

	public void setCreated(long created) {
		this.created = created;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getReference() {
		return reference;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}



	public String getParent() {
		return parent;
	}

	public void setParent(String parent) {
		this.parent = parent;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public int getW() {
		return w;
	}

	public void setW(int w) {
		this.w = w;
	}

	public int getH() {
		return h;
	}

	public void setH(int h) {
		this.h = h;
	}

	public String getAnnotation() {
		return annotation;
	}

	public void setAnnotation(String annotation) {
		this.annotation = annotation;
	}

	@Override
	public String toString() {
		return "Comment [type=" + type + ", reference=" + reference + ", _id="
				+ _id + "]";
	}
	
	
	public static JsonObject isAuthor(String userID, String id){
		 return new JsonObject()
		 	.put("_id",id)
	    	.put("userID", userID);
	}

}
