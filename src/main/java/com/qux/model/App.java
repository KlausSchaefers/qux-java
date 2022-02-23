package com.qux.model;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.qux.util.DB;


@JsonIgnoreProperties(ignoreUnknown = true)
public class App extends Model{
	
	public static final String FIELD_IS_DELETED = "isDeleted";
	
	public static final String BUS_APP_UPDATE = "matc.app.update";
	
	private String name ="";
	
	private String description =""; 
	
	private String type="";

	private boolean isPublic = false;
	
	private boolean clonable = false;

	private float rating = 0;
	
	private int test = 0;
	
	private int comments = 0;
	
	private Map<String, Integer> screenSize = new HashMap<String, Integer>();

	public App setScreenSize(int w, int h){
		this.screenSize.put("w", w);
		this.screenSize.put("h", h);
		return this;
	}
	
	

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
	
	public boolean getClonable() {
		return clonable;
	}

	public void setClonable(boolean clonable) {
		this.clonable = clonable;
	}

	@JsonProperty("isPublic")
	public boolean getPublic() {
		return isPublic;
	}

	@JsonProperty("isPublic")
	public void setPublic(boolean isPublic) {
		this.isPublic = isPublic;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	
	
	
	



	public float getRating() {
		return rating;
	}

	public void setRating(float rating) {
		this.rating = rating;
	}

	public int getTest() {
		return test;
	}

	public void setTest(int test) {
		this.test = test;
	}

	public int getComments() {
		return comments;
	}

	public void setComments(int comments) {
		this.comments = comments;
	}
	
	public void incComments(int i){
		this.comments +=i;
	}

	
	
	public Map<String, Integer> getScreenSize() {
		return screenSize;
	}

	public void setScreenSize(Map<String, Integer> screenSize) {
		this.screenSize = screenSize;
	}
	
	

	@Override
	public String toString() {
		return "App [id=" + getId() + ", name=" + name + ", isPublic=" + isPublic
				+ "]";
	}

	

	
	/************************************************************
	 *  Query Methods!
	 ************************************************************/
	

 	public static JsonObject findPublicByID(String appID){
 		return new JsonObject()
 			.put("isPublic", true)
 			.put("_id", appID);
 		
	}
 	
 	public static JsonObject findPublic(){
 		return new JsonObject()
 			.put("isPublic", true);
	}
 	
 	public static JsonObject findNotPaid(){
 		return new JsonObject()
 			.put("isPublic", true)
 			.put("domain", "Kyrapp.com");
	}
 	
 	public static JsonObject findByIds(){
 		return new JsonObject()
 			.put("_id", true);
	}
 	
 	public static JsonObject findDirty() {
 		return new JsonObject()
 	 			.put("isDirty", true);
	}

 	
 	public static JsonObject summaryFields(){
 		return new JsonObject()
				.put("widgets", 0)
				.put("screens", 0)
				.put("groups", 0)
				.put("templates", 0)
				.put("grid", 0)
				.put("lines", 0);
 	}
 	
 	

	public static String[] getModelParts() {
		return new String[]{
			DB.getTable(Event.class), 
			DB.getTable(CommandStack.class), 
			DB.getTable(TestSetting.class), 
			DB.getTable(Comment.class),
			DB.getTable(Image.class),
			DB.getTable(Annotation.class),
			DB.getTable(Invitation.class),
			DB.getTable(Team.class),
			DB.getTable(Mouse.class)
		};
	}
	
	

	public static void onUpdate(RoutingContext event, String appID) {
		
		JsonObject request = new JsonObject()
				.put(AppPart.APP_ID,  appID);
		
		event.vertx().eventBus().send(App.BUS_APP_UPDATE, request);
		
	}



	public static boolean isDeleted(JsonObject app) {
		if (app.containsKey(FIELD_IS_DELETED)){
			return app.getBoolean(FIELD_IS_DELETED);
		}
		return false;
	}



	
	


}
