package com.qux.validation;

import com.qux.model.User;
import com.qux.util.DB;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

import java.util.ArrayList;
import java.util.List;

public class UserValidator extends MongValidator implements Validator{
	
	private String user_db = DB.getTable(User.class);

	public UserValidator(MongoClient client) {
		super(client);
	}


	
	@Override
	public void validate(JsonObject json, boolean isCreate, Handler<List<String>> handler) {
		List<String> errors = new ArrayList<String>();
		
		User user = mapper.fromVertx(json, User.class);
		
		if(user!=null){

			if(user.getEmail()== null || user.getEmail().isEmpty()){
				errors.add("user.email.invalid");
			}
			
			if(user.getPassword()!=null && user.getPassword().length() < 6){
				errors.add("user.password.invalid");
			}
			
			if(isCreate){
				if(!user.getTos()){
					errors.add("user.tos.invalid");
				}
				client.count(user_db, User.findByEmail(user.getEmail()), event -> {
					if(event.succeeded()){
						if(event.result() > 0){
							errors.add("user.email.not.unique");
						}
					} else {
						errors.add("user.validation.db");
					}
					handler.handle(errors);
				});
			} else {
				handler.handle(errors);
			}
		} else {
			errors.add("user.validation.json");
			handler.handle(errors);
		}
	}
}
