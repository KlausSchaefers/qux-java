package com.qux.validation;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.Collections;
import java.util.List;

public class CommandStackValidator implements Validator{

	@Override
	public void validate(JsonObject obj, boolean isUpdate, Handler<List<String>> handler) {
		handler.handle(Collections.emptyList());
		
	}

}
