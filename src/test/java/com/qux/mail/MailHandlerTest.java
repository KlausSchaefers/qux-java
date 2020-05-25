package com.qux.mail;

import com.qux.MatcTestCase;
import com.qux.bus.MailHandler;
import com.qux.util.DebugMailClient;
import com.qux.util.Mail;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.StartTLSOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class MailHandlerTest extends MatcTestCase {


	@Test
	public void test(TestContext context){
		log("test", "enter");
			
		EventBus eb = vertx.eventBus();
		
		DebugMailClient client = new DebugMailClient("mail.test.com");
		
		new MailHandler(vertx, client, "support@quant-ux.com", "mail.bus.test", "qux");
		

		JsonObject reset_request = new JsonObject()
			.put("passwordRestKey", "123123")
			.put("name", "Klaus");
		
		JsonObject msg = MailHandler.createMessage(
				"klaus.schaefers@gmail.com", 
				"Quant-UX - Password Reset", 
				"reset_password",
				reset_request);
		
		eb.send("mail.bus.test", msg);

		
		Mail.to("klaus.schaefers@gmail.com")
			.subject("test")
			.template(MailHandler.TEMPLATE_USER_CREATED)
			.payload(new JsonObject())
			.send(eb);
		
		
		sleep(500);

		
		log("test", "exit");
	}
	
	
	
	private MailClient createMail(JsonObject config){
		
		MailConfig mailConfig = new MailConfig();
		mailConfig.setHostname(config.getString("host"));
		mailConfig.setPort(587);
		mailConfig.setStarttls(StartTLSOptions.REQUIRED);
		mailConfig.setUsername(config.getString("user"));
		mailConfig.setPassword(config.getString("password"));
		
		return MailClient.createShared(vertx, mailConfig);
	}
}
