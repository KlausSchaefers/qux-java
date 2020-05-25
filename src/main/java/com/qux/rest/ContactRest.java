package com.qux.rest;

import com.qux.MATC;
import com.qux.bus.MailHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.util.Mail;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ContactRest {


	private Logger logger = LoggerFactory.getLogger(ContactRest.class);
	
	public void send(RoutingContext event) {

		
		JsonObject json = event.getBodyAsJson();
		if(json!=null){
		
			String message = json.getString("message");
			logger.info("send() >" + message);
			
			Mail.to(MATC.ADMIN)
				.subject("Contact Form")
				.payload(json)
				.template(MailHandler.TEMPLATE_CONTACT)
				.send(event);
			
		}
		
		event.response().end("{}");
	}
}
