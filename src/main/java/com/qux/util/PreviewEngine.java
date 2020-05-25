package com.qux.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreviewEngine {
	
	private Logger logger = LoggerFactory.getLogger(PreviewEngine.class);
	
	private boolean active = true;
	
	public PreviewEngine(){
		this(true);
	}
	
	public PreviewEngine(boolean active){
		this.active =active;
	}


	public JsonObject create(JsonObject app){
		if(!this.active){
			return app;
		}
		long start = System.currentTimeMillis();

		try{
			if(!app.containsKey("screens") || !app.containsKey("widgets")){
				logger.debug("create() > Encounterd strange app with screens or widgets! Test??"); 
				return app;
			}
			
			JsonObject screens = app.getJsonObject("screens");
			JsonObject widgets = app.getJsonObject("widgets");
			
			
			List<String> screensToRemove = new ArrayList<String>();
			Set<String> screenIDs = screens.fieldNames();
			
			JsonObject startScreen = null;
			int count = 0;
			for(String id : screenIDs){
				JsonObject screen = screens.getJsonObject(id);
				if(isStartScreen(screen)){
					startScreen = screen;
					count++;
				} else {
					screensToRemove.add(id);
				}
			}
			
			
			
			/**
			 * There can be some shit in JS and we have two start screens. This leads to some strange effects.
			 */
			if(count >=2){
				logger.debug("create() > Two or more start screens! > id : " + app.getString("id") + " > name : " + app.getString("name"));
				return app;
			}
			
			Set<String> childIds = new HashSet<String>();
			if(startScreen!=null){
				
				
			
				if(startScreen.containsKey("parents")){
					JsonArray parents = startScreen.getJsonArray("parents");
					if(parents.size() > 0 ){		
						for(int i=0; i< parents.size(); i++){
							String parentID = parents.getString(i);
							/**
							 * Add copy if the widgets
							 */
							extendScreen(screens, widgets, startScreen, parentID);
						}
					}
					/**
					 * Remove so we don't have to worry in js that it will 
					 * try to extend extended stuff
					 */
					startScreen.remove("parents");
				}
				
				/**
				 * Now check which children to save...
				 */
				if(startScreen.containsKey("children")){
					JsonArray children = startScreen.getJsonArray("children");
					for(int i=0; i< children.size(); i++){
						childIds.add(children.getString(i));
					}
				}
				
			} else {
				logger.debug("create() > No Start screen for " + app.getString("_id"));
			}
			
			
			
			/**
			 * Remove not needed screens
			 */
			filterScreens(screens, screensToRemove);
			
			/**
			 * Remove not needed widgets
			 */
			filterWidgets(widgets, childIds);
	
			
			if(app.containsKey("lines")){
				app.remove("lines");
			}
			if(app.containsKey("groups")){
				app.remove("groups");
			}
			
			long end = System.currentTimeMillis();
			logger.debug("create() > exit > " + (end-start) + "ms");
			
			
			
		} catch(Exception e){
			logger.error("create() > Error for app "+ app.encode() );
			e.printStackTrace();
		}
		
		return app;
	}

	private void filterScreens(JsonObject screens, List<String> screensToRemove) {
		for(String id : screensToRemove){
			screens.remove(id);
		}
	}

	private void filterWidgets(JsonObject widgets, Set<String> childIds) {
		Set<String> widgetIds = widgets.fieldNames();
		List<String> widgetsToRemove = new ArrayList<String>();
		for(String id : widgetIds){
			if(!childIds.contains(id)){
				widgetsToRemove.add(id);
			}
		}
		filterScreens(widgets, widgetsToRemove);
	}

	private void extendScreen(JsonObject screens, JsonObject widgets, JsonObject startScreen, String screenID) {
		
		try{
			if(screens.containsKey(screenID)){
				JsonObject parentScreen = screens.getJsonObject(screenID);
				
				/**
				 * Keep in sync with Layout.js createInheritedModel();
				 */
				int difX = parentScreen.getInteger("x") - startScreen.getInteger("x");
				int difY = parentScreen.getInteger("y") - startScreen.getInteger("y");
				
				JsonArray parentChildren = parentScreen.getJsonArray("children");
				for(int j=0; j < parentChildren.size(); j++){
					String parentChildrenID = parentChildren.getString(j);
					
					if(widgets.containsKey(parentChildrenID)){
						
						
						JsonObject parentChild = widgets.getJsonObject(parentChildrenID);
						
						JsonObject copy = parentChild.copy();
						copy.put("id", parentChild.getString("id") + "@"+ startScreen.getString("id"));
						copy.put("inherited", parentChildrenID);
						copy.put("x", parentChild.getInteger("x") - difX);
						copy.put("y", parentChild.getInteger("y") - difY);
						
						widgets.put(copy.getString("id"), copy);
						
						startScreen.getJsonArray("children").add(copy.getString("id"));
						
					} else {
						logger.warn("extendScreen() > No parent child with id: " + parentChildrenID);
					}
				}
				
				
			} else {
				logger.warn("extendScreen() > Missing parent : " + screenID);
			}
		} catch(Exception e){
			logger.error("extendScreen() > Error", e);
		}
		

	}
	
	private boolean isStartScreen(JsonObject screen){
		return 	screen.containsKey("props") && 
				screen.getJsonObject("props").containsKey("start") && 
				screen.getJsonObject("props").getBoolean("start") == true;
	}
}


