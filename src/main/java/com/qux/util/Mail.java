package com.qux.util;

import com.qux.MATC;
import com.qux.bus.MailHandler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mail {
	
	private JsonObject msg = new JsonObject();
	
	private static Logger logger = LoggerFactory.getLogger(Mail.class);
	
	
	private Mail(){
	}
	
	public static void error(RoutingContext event, String message){
		
		try{
			String expiredAt = TokenService.getExpiresAt(event);

			JsonObject json = new JsonObject()
					.put("url", event.request().path())
					.put("user", "")
					.put("platform", "Vertx")
					.put("ua", "-")
					.put("token", expiredAt)
					.put("queue", "-")
					.put("stack", "-")
					.put("message", message)
					.put("plugins", "-");
			
			
			Mail.to("klaus.schaefers@quant-ux.com")
				.subject("Server Error")
				.payload(json)
				.template(MailHandler.TEMPLATE_CLIENT_ERROR)
				.send(event);
			
		} catch(Exception e){
			logger.error("Cannot send error mail", e);
		}
		
	}
	

	public static void error(EventBus bus, String message){
		
		try{
			
			JsonObject json = new JsonObject()
					.put("url", "none")
					.put("user", "")
					.put("platform", "Vertx")
					.put("ua", "-")
					.put("queue", "-")
					.put("stack", "-")
					.put("message", message)
					.put("plugins", "-");
			
			
			Mail.to(MATC.ADMIN)
				.subject("Server Error")
				.payload(json)
				.template(MailHandler.TEMPLATE_CLIENT_ERROR)
				.send(bus);
			
		} catch(Exception e){
			logger.error("Cannot send error mail", e);
		}
		
	}
	
	public static Mail to(String email){
		Mail mail = new Mail();
		mail.msg.put(MailHandler.FIELD_TO, email);
		return mail;
	}
	
	public Mail subject(String subject){
		this.msg.put(MailHandler.FIELD_SUBJECT, subject);
		return this;
	}
	
	public Mail template(String template){
		this.msg.put(MailHandler.FIELD_TEMPLATE, template);
		return this;
	}
	
	public Mail bcc(String bcc){
		this.msg.put(MailHandler.FIELD_BCC, bcc);
		return this;
	}
	
	public Mail payload(JsonObject payload){
		this.msg.put(MailHandler.FIELD_PAYLOAD, payload);
		return this;
	}

	public void send(EventBus bus){	
		bus.send(MailHandler.MAIl_BUS_QUANT_UX, msg);	
	}
	
	public void send(EventBus bus, String address){	
		bus.send(address, msg);	
	}
	
	public void send(RoutingContext event){
		EventBus bus = event.vertx().eventBus();
		String app = event.request().getHeader("app");
		logger.info("send() > " + app);
		bus.send(MailHandler.MAIl_BUS_QUANT_UX, msg);

	}
	

}

