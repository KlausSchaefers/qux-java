package com.qux.rest;

import com.qux.MATC;
import com.qux.acl.AppAcl;
import com.qux.bus.MailHandler;
import com.qux.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.vommond.lunarmare.Model;
import de.vommond.lunarmare.ModelFactory;
import com.qux.util.Mail;
import com.qux.util.MongoREST;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class CrowdRest extends MongoREST{

	private final Model model;
	
	private Logger logger = LoggerFactory.getLogger(CrowdRest.class);

	public CrowdRest(MongoClient db){
		super(db, Model.class);
		setIdParameter("appID");
		setACL(new AppAcl(db));
		
		model =  new ModelFactory().create("crowdSourcingRequest")
			.addString("appType")
			.addString("appName")
			.addInteger("testUserCount")
			.addInteger("testAgeFrom")
			.addInteger("testAgeTo")
			.addString("testUrl")
			.addString("previewUrl")
			.addString("testGender")
			.build();
	}
	

	public Handler<RoutingContext> sendMail() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				sendMail(event);
			}
		};
	}
	
	private void sendMail(RoutingContext event){
		User user = getUser(event);
		JsonObject request = event.getBodyAsJson();
		if(model.isValid(request)){
			this.acl.canWrite(user, event, canWrite ->{
				if(canWrite){
					
					this.logger.info("sendMail() > The user " + user +" send crowd sourcing mail");
					
			
					request.put("userName", user.getName());
					request.put("userLastname", user.getLastname());
					request.put("userEmail", user.getEmail());
					
					
					Mail.to("info@nutzerbrille.de")
						.subject("Quant-UX - Crowd Testing Request")
						.bcc(MATC.ADMIN)
						.payload(request)
						.template(MailHandler.TEMPLATE_CROWD_REQUEST)
						.send(event);
					
					returnOk(event,"mail.send");
				} else {
					returnError(event, 404);
					this.logger.error("sendMail() > The user " + user +" tried to send crowd sourcing mail >>  " + event.request().path());
				}
			});
		} else {
			returnError(event, 405);
			this.logger.error("sendMail() > The user " + user +" send bad request >>  " + event.request().path());

		}
	}
}
