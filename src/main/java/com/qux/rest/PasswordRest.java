package com.qux.rest;

import com.qux.auth.ITokenService;
import com.qux.bus.MailHandler;
import com.qux.model.AppEvent;
import com.qux.model.User;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.vommond.lunarmare.Model;
import de.vommond.lunarmare.ModelFactory;
import com.qux.util.DB;
import com.qux.util.Mail;
import com.qux.util.rest.REST;
import com.qux.util.Util;

public class PasswordRest extends REST{

	private final MongoClient db;
	
	private final String table = DB.getTable(User.class);
	
	private Logger logger = LoggerFactory.getLogger(PasswordRest.class);
	
	private final Model resetRequest, setRequest;
	
	
	public PasswordRest(ITokenService tokenService, MongoClient db) {
		super(tokenService);
		this.db = db;	
		
		resetRequest = new ModelFactory().create("User")
				.addString("email")
				.build();
		
		setRequest = new ModelFactory().create("User")
				.addString("email")
				.addString("key")
				.addString("password").setMinLenth(6)
				.build();
	}
	
	

	public Handler<RoutingContext> resetPassword() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				resetPassword(event);
			}
		};
	}
	
	
	
	private void resetPassword(RoutingContext event) {
		logger.warn("resetPassword() > enter");
		
		JsonObject req = resetRequest.read(event);		
		List<String> errors = resetRequest.validate(req);
		
		if(errors.isEmpty()){
			
			String email = req.getString("email");
			
			db.findOne(table, User.findByEmail(email), null, res ->{
				
				if(res.succeeded()){
					
					JsonObject user = res.result();
					if(user!=null){
						
						if(!user.containsKey("passwordResetCount")){
							user.put("passwordResetCount", 1);
						} else {
							user.put("passwordResetCount", user.getInteger("passwordResetCount") +1);
						}
						
						int resetCount = user.getInteger("passwordResetCount");
						if(resetCount < 10){
							
							/**
							 * Store a key for the user!
							 */
							String resetKey = Util.getRandomString();
							user.put("passwordRestKey", resetKey);
							
							db.save(table, user, write->{
								
								if(write.succeeded()){
									
									logger.warn("resetPassword() > exit " + resetKey);

									JsonObject payload = new JsonObject()
										.put("name", user.getString("name"))
										.put("lastname", user.getString("lastname"))
										.put("passwordRestKey", resetKey);

									Mail.to(user.getString("email"))
										.subject("Password Reset")
										.template(MailHandler.TEMPLATE_PASSWORD_RESET)
										.payload(payload)
										.send(event);
									
									returnOk(event, "user.password.reset");
									AppEvent.send(event, user.getString("email"), AppEvent.TYPE_USER_RESET);
									
								} else {
									logger.error("resetPassword() > Could not store user");
									returnError(event, 405);
								}
							});
							
		
						} else {
							logger.error("resetPassword() > Too many attempts for " + email);
							returnOk(event, "user.password.reset");
						}
						
					} else {
						logger.error("resetPassword() > No user with email :" + email);
						returnOk(event, "user.password.reset");
					}
					
					
				} else {
					logger.error("resetPassword() > Some mongo issue");
					returnOk(event, "user.password.reset");
				}
				
				
			});
			
		} else {
			logger.error("resetPassword() > Parameters are wrong!"); 
			returnError(event, errors);
		}

		
		
	}
	
	private void setPassword(RoutingContext event) {
	
		JsonObject req = setRequest.read(event);		
		List<String> errors = setRequest.validate(req);
		
		if(errors.isEmpty()){
						
			JsonObject query= new JsonObject()
				.put("email", req.getString("email"))
				.put("passwordRestKey", req.getString("key"));
			
			db.findOne(table, query, null, res ->{
				
				if(res.succeeded()){
					
					JsonObject user = res.result();
					if(user!=null){
					
						
						user.put("passwordResetCount", 0);
						user.remove("passwordRestKey");
						user.put("password", Util.hashPassword(req.getString("password")));
						
						db.save(table, user, write->{
							
							if(write.succeeded()){
								logger.warn("setPassword() >New password for user " + req.getString("email"));
								returnOk(event, "user.password.reset.done");
							} else {
								logger.error("setPassword() > Mongo shit");
								returnError(event, "user.password.error");
							}
							
						});
						
						
						
					} else {
						logger.error("setPassword() > No user with email & key :" + query);
						returnError(event, "user.password.error");
					}
					
					
				} else {
					logger.error("resetPassword() > Some mongo issue");
					returnOk(event, "user.password.reset");
				}
				
				
			});
			
		} else {
			logger.error("setPassword() > Data invalid in request");
			returnError(event, errors);
		}
		
	
		
	}
	

	public Handler<RoutingContext> setPassword() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				setPassword(event);
			}
		};
	}
	
	

}
