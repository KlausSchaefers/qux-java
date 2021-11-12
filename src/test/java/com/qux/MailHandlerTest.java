package com.qux;

import com.qux.MatcTestCase;
import com.qux.bus.MailHandler;
import com.qux.util.DebugMailClient;
import com.qux.util.Mail;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
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
		
		DebugMailClient client = new DebugMailClient(false);
		client.getMails().clear();

		new MailHandler(
				vertx,
				client,
				"support@quant-ux.com",
				"https://test-qux-server.com"
		);

		// sleep a little until all templates are loaded
		sleep(500);

		JsonObject reset_request = new JsonObject()
			.put("passwordRestKey", "123123")
			.put("name", "Klaus");

		Mail.to("klaus.schaefers@gmail.com")
			.subject("test")
			.template(MailHandler.TEMPLATE_PASSWORD_RESET)
			.payload(reset_request)
			.send(eb);
		
		
		sleep(500);


		context.assertEquals(1, client.getMails().size());
		MailMessage message = client.getMails().get(0);
		context.assertTrue(message.getText().contains("https://test-qux-server.com"));

		
		log("test", "exit");
	}

}
