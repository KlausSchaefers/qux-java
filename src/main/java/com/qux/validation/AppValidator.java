package com.qux.validation;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import com.qux.util.MongoREST;

public class AppValidator extends MongValidator implements Validator{

	public AppValidator(MongoClient client, MongoREST rest) {
		super(client);
	}

	@Override
	public void validate(JsonObject obj, boolean isUpdate, Handler<List<String>> handler) {
		handler.handle(new ArrayList<String>());
		
	}

}
