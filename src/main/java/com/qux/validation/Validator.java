package com.qux.validation;

import java.util.List;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public interface Validator {

	public void validate(JsonObject obj, boolean isUpdate, Handler<List<String>> handler);
	
}
