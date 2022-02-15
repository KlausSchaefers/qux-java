package com.qux.rest;

import com.qux.acl.Acl;
import com.qux.acl.CommentAcl;
import com.qux.acl.RoleACL;
import com.qux.auth.ITokenService;
import com.qux.model.Comment;
import com.qux.model.Model;
import com.qux.model.User;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.qux.util.DB;
import com.qux.util.rest.MongoREST;
import com.qux.validation.PojoValidator;

public class CommentREST extends MongoREST{
	
	private final String user_db;
	
	private final Set<String> exludedKeys = new HashSet<String>();
	
	public CommentREST(ITokenService tokenService, MongoClient db) {
		this(tokenService, db, new RoleACL( new CommentAcl(db)).read(User.GUEST));
	}
	
	
	public CommentREST(ITokenService tokenService, MongoClient db, Acl acl) {
		super(tokenService, db, Comment.class);
		
		/**
		 * use simple pojo validation
		 */
		setValidator(new PojoValidator<Comment>(Comment.class));
		
		/**
		 * only users with the correct role can create an app.
		 */
		setACL(acl);
		
		/**
		 * set the mongo id parameter!
		 */
		setIdParameter("commentID");	
				
		this.user_db = DB.getTable(User.class);
	}
	
	public CommentREST exlcudeUrlParameter(String name){
		this.exludedKeys.add(name);		
		return this;
	}
	
	public Handler<RoutingContext> count() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				count(event);
			}
		};
	}
	
	private void count(RoutingContext event){

		this.acl.canRead(this.getUser(event), event, allowed ->{
			
			if(allowed){
				JsonObject query = getPathQuery(event, exludedKeys);
				mongo.count(table, query, res->{
					
					if(res.succeeded()){
						JsonObject result = new JsonObject()
							.put("count", res.result());
						
						returnJson(event, result);
					} else {
						error("count", "Mongo issue at "+ event.request().path());
						returnError(event, 405);
					}
				});
				
			} else {
				error("count", "User "+ this.getUser(event) + " tried to count "+ event.request().path());
				returnError(event, 405);
			}
		});
		
	}
	
		
	
	/********************************************************************************************
	 * as app part
	 ********************************************************************************************/
	
	protected void beforeCreate(RoutingContext event, JsonObject json){
		User u = getUser(event);
		json.put("userID", u.getId());
		
		if(hasParam(event, "appID")){
			json.put("appID", getId(event, "appID"));	
		}
		
		if(hasParam(event, "contentID")){
			json.put("contentID", getId(event, "contentID"));	
		}
		
		json.put("created", System.currentTimeMillis());
	}
	

	
	protected void beforeUpdate(RoutingContext event, JsonObject json){
		User u = getUser(event);
		
		if(hasParam(event, "appID")){
			json.put("appID", getId(event, "appID"));	
		}
		if(hasParam(event, "contentID")){
			json.put("contentID", getId(event, "contentID"));	
		}
		
		json.put("userID", u.getId());
		json.put("lastUpdate", System.currentTimeMillis());
	}
	
	public void afterUpdate(RoutingContext event, String id, JsonObject result){
		
	}
	
	
	protected void findBy(RoutingContext event){
		
		JsonObject query = getPathQuery(event, exludedKeys);
				
		mongo.find(table, query, res -> {
			
			if(res.succeeded()){
				
				HashSet<String> userIds = new HashSet<String>();
				List<JsonObject> comments = res.result();
				for(JsonObject  comment :  comments){
					userIds.add(comment.getString("userID"));
				}
				
				addUsers(event,comments, userIds );
				
	
				
			} else {
				returnError(event, 404);
			}
		});
		
		
	}

	protected void addUsers(RoutingContext event,List<JsonObject> comments, HashSet<String> userIDs) {
		
		JsonArray ids = new JsonArray();
		
		for(String id : userIDs){
			ids.add(id);
		}
		
		mongo.findWithOptions(user_db, User.findByIDS(ids), Model.getFindOptions("name", "lastname", "email", "image"), res -> {
			
			if(res.succeeded()){
				
				List<JsonObject> json = res.result();
				HashMap<String, JsonObject> users = new HashMap<String, JsonObject>();
				
				
				for(JsonObject  user :  json){
					user.put("id", user.getString("_id"));
					users.put(user.getString("_id"), user);
				}


				for(JsonObject comment :  comments){
					String userID = comment.getString("userID");
					if(users.containsKey(userID)){
						comment.put("user",users.get(userID));
					}
				}
				
				JsonArray result = toArray(comments);
				returnJson(event, result);
				
			} else {
				returnError(event, 404);
			}
		});
		
		
	}
	
}
