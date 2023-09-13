package com.qux.bus;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import de.vommond.lunarmare.Model;
import de.vommond.lunarmare.ModelFactory;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.HashMap;
import java.util.Map;

public class MailHandler implements Handler<Message<JsonObject>>{

	private static final Logger logger = LoggerFactory.getLogger(MailHandler.class);
	
	/**
	 * Template folder
	 */
	private static final String TEMPLATE_FOLDER = "emails/";

	public static final String MAIL_BUS_QUANT_UX = "matc.mail.send.quant";

	/**
	 * Default sender
	 */
	private final String from;

	/**
	 * Server where links should point to
	 */
	private final String serverHost;
	

	/**
	 * Mail message fields
	 */
	public static final String FIELD_BCC = "bcc";

	public static final String FIELD_PAYLOAD = "payload";

	public static final String FIELD_TEMPLATE = "template";

	public static final String FIELD_SUBJECT = "subject";

	public static final String FIELD_TO = "to";
	
	
	/**
	 * Templates
	 */
	public static final String TEMPLATE_PASSWORD_RESET ="reset_password";
	
	public static final String TEMPLATE_USER_CREATED ="user_created";

	public static final String TEMPLATE_TEAM_ADDED ="team_added";

	public static final String TEMPLATE_LIB_TEAM_ADDED ="lib_team_added";

	public static final String TEMPLATE_CLIENT_ERROR ="client_error";

	public static final String TEMPLATE_CONTACT ="contact";

	public static final String TEMPLATE_HTML_FRAME ="frame";

	private final MailClient mail;
		
	private final Handlebars handlebars;
	
	private final Map<String, Template> txtTemplates = new HashMap<>();
	
	private final Map<String, Template> htmlTemplates = new HashMap<>();
	
	private final Vertx vertx;
	
	private final Model validator;

	public static MailHandler start(Vertx vertx, MailClient client, String from, String serverHost){
		return new MailHandler(vertx, client, from, serverHost);
	}
	
	public MailHandler(Vertx vertx, MailClient mail, String from, String serverHost){
		logger.info("constructor() > "  + from + " @ " + serverHost);

		this.from = from;
		this.serverHost = serverHost;
		
		this.mail = mail;		
		this.vertx = vertx;

		validator = new ModelFactory().create("MailMessage")
				.addString(FIELD_TO)
				.addString(FIELD_SUBJECT)
				.addString(FIELD_TEMPLATE)
				.addObject(FIELD_PAYLOAD)
				.build();

		handlebars = new Handlebars();

		vertx.executeBlocking(handler -> {
			String templateFolder = "qux";
			readTemplate(templateFolder, TEMPLATE_PASSWORD_RESET);
			readTemplate(templateFolder, TEMPLATE_USER_CREATED);
			readTemplate(templateFolder, TEMPLATE_TEAM_ADDED);
			readTemplate(templateFolder, TEMPLATE_CLIENT_ERROR);
			readTemplate(templateFolder, TEMPLATE_CONTACT);
			readTemplate(templateFolder, TEMPLATE_HTML_FRAME);
			handler.complete();
		}, result -> {
			logger.info("constructor() > loaded mails...");
		});

		EventBus eb = vertx.eventBus();
		eb.consumer(MAIL_BUS_QUANT_UX, this);

		logger.info("constructor() > exit");
	}
	
	@Override
	public void handle(Message<JsonObject> message) {
		logger.debug("handle() > enter"); 
		JsonObject msg = message.body();
		send(msg);		
	}

	public void send(JsonObject msg) {
		if(validator.isValid(msg)){
				
			String templateID = msg.getString(FIELD_TEMPLATE);
						
			try{
				
				if (txtTemplates.containsKey(templateID) && htmlTemplates.containsKey(templateID)){
					MailMessage mm = new MailMessage();
					mm.setFrom(from);
					mm.setTo(msg.getString(FIELD_TO));
					mm.setSubject(msg.getString(FIELD_SUBJECT));

					if(msg.containsKey(FIELD_BCC)){
						mm.setBcc(msg.getString(FIELD_BCC));
					}

					Map<String, Object> payload = msg.getJsonObject(FIELD_PAYLOAD).getMap();
					payload.put("quxServerHost", this.serverHost);

					if (txtTemplates.containsKey(templateID)){
						Template txtTemplate = txtTemplates.get(templateID);	
						String txt = txtTemplate.apply(payload);
						mm.setText(txt);					
					}
					
					if (htmlTemplates.containsKey(templateID)){
						Template htmlTemplate = htmlTemplates.get(templateID);	
						String innerHTML = htmlTemplate.apply(payload);

						Template frame = htmlTemplates.get(TEMPLATE_HTML_FRAME);
						Map<String, String> frameData = new HashMap<>();
						frameData.put("inner", innerHTML);
						String wrapped = frame.apply(frameData);
						mm.setHtml(wrapped);
					}
								
				
					mail.sendMail(mm, res->{
						if(res.succeeded()){
							logger.debug("handle() > from: " + mm.getFrom() + " >> to : " + mm.getTo() + " >> subject : " + mm.getSubject() + " >> txt: " + mm.getText());
							logger.debug("handle() > Done");
						} else {
							logger.error("handle() > from: " + mm.getFrom() + " >> to : " + mm.getTo() + " >> subject : " + mm.getSubject() + " >> txt: " + mm.getText());
							logger.error("handle() > Could not send mail to "+ msg.getString(FIELD_TO));
							logger.error("handle() > error: ", res.cause());
							res.cause().printStackTrace();
						}
					});
				} else {
					logger.error("handle() No Template "+ templateID);
					System.err.println("MailHandler.handle() No Template "+ templateID);
				}
			} catch(Throwable e){
				logger.error("handle() > Could not send mail to "+ msg.encodePrettily());
				e.printStackTrace();
			}
		
			
		} else {
			logger.error("handle() Json is shit" + msg.encode());
		}
	}


	private void readTemplate(String folder, String templateName){

		String fileName = folder + "/" + templateName;
		
		FileSystem fileSystem = vertx.fileSystem();
		String templateFileTXT = TEMPLATE_FOLDER + fileName + ".txt";
		fileSystem.readFile(templateFileTXT, res->{
			if(res.succeeded()){
				try{
					Buffer b = res.result();
					String string = b.toString();
					Template template = handlebars.compileInline(string);
					this.txtTemplates.put(templateName, template);
					logger.info("readTemplate() >  Read : "+ fileName + ".txt! ");

				} catch(Exception e){
					logger.error("readTemplate() > Could not read : "+ fileName + ".txt! "+e.getMessage());

				}
			}else {
				logger.error("readTemplate() > Could not read : "+ fileName + ".txt! ", res.cause());
			}
			
		});

		String templateFileHTML = TEMPLATE_FOLDER + fileName + ".html";
		fileSystem.readFile(templateFileHTML, res ->{
			if(res.succeeded()){
				try{
					Buffer b = res.result();
					String string = b.toString();
					Template template = handlebars.compileInline(string);
					this.htmlTemplates.put(templateName, template);		
					logger.info("readTemplate() >  Read : "+ fileName + ".html! ");
				} catch(Exception e){
					logger.error("readTemplate() > Could not read : "+ fileName + ".html! " +e.getMessage());
					e.printStackTrace();
				}
			} else {
				logger.error("readTemplate() > Could not read : "+ fileName + ".html! ", res.cause());
			}
		});	
	}
}
