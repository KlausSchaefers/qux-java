package com.qux.model;

import java.util.HashMap;
import java.util.Map;


public class Template extends Model{
	
	private String name, os, version, category;
	
	public boolean isPublic;
	
	private String description =""; 
		
	private String json = "";
	
	private Map<String, Integer> users = new HashMap<String, Integer>();

	
	public Template setUser(User user, int permission){
		this.users.put(user.getId(), permission);
		return this;
	}
	
	public Template removeUser(User user){
		this.users.remove(user.getId());
		return this;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getOs() {
		return os;
	}

	public void setOs(String os) {
		this.os = os;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getJson() {
		return json;
	}

	public void setJson(String json) {
		this.json = json;
	}

	public boolean isPublic() {
		return isPublic;
	}

	public void setPublic(boolean isPublic) {
		this.isPublic = isPublic;
	}
	
	
	
}
