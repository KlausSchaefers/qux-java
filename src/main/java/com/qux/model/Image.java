package com.qux.model;


public class Image extends AppPart {
	
	public static final int MAX_IMAGE_WIDTH = 1536;
	
	public static final int SCALE_FACTOR = 2;
	
	private String name ="";
	
	private String url="";
	
	private String userID;
	
	private int width = 0;
	
	private int height = 0;
	
	public Image(){
		
	}
	

	@Override
	public String toString() {
		return "Image [name=" + name + ", url=" + url + "]";
	}


	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUserID() {
		return userID;
	}

	public void setUserID(String user) {
		this.userID = user;
	}


	public String getUrl() {
		return url;
	}


	public void setUrl(String url) {
		this.url = url;
	}


	public int getWidth() {
		return width;
	}


	public void setWidth(int width) {
		this.width = width;
	}


	public int getHeight() {
		return height;
	}


	public void setHeight(int height) {
		this.height = height;
	}
	
	
	
	public static String replaceAppIDinImageUrl(String url, String newID){
		String oldFileName = url.substring(url.indexOf("/"));
		return newID + oldFileName;
	}

	

	

}