package com.qux.validation;

import com.qux.util.JSONMapper;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PojoValidator<T> implements Validator {
	
	private Class<T> cls;
	
	private JSONMapper mapper = new JSONMapper();
	
	public PojoValidator(Class<T> cls){
		this.cls = cls;
	}

	@Override
	public void validate(JsonObject json, boolean isUpdate,Handler<List<String>> handler) {
		
		T obj = mapper.fromVertx(json, cls);
		if(obj == null){
			ArrayList<String> errors = new ArrayList<String>();
			errors.add(cls.getSimpleName() + ".validation.error");
			handler.handle(errors);
		} else {
			handler.handle(Collections.emptyList());
		}
		
		
	}

}
