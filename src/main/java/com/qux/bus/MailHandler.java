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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class MailHandler implements Handler<Message<JsonObject>>{

	private static Logger logger = LoggerFactory.getLogger(MailHandler.class);
	
	/**
	 * Template folder
	 */
	private static final String TEMPLATE_FOLDER = "emails/";
	
	public static final String MAIl_BUS_QUANT_UX = "matc.mail.send.quant";
	
	public static final String MAIl_BUS_KYRA = "matc.mail.send.kyra";
	
	/**
	 * EventBus Address
	 */
	public final String busAdress;
	
	/**
	 * Default sender
	 */
	private final String from;
	

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
	
	public static final String TEMPLATE_USER_LOGN_NOT_PAID ="user_login_not_paid";

	public static final String TEMPLATE_TEAM_ADDED ="team_added";
	
	public static final String TEMPLATE_LIB_TEAM_ADDED ="lib_team_added";
	
	public static final String TEMPLATE_CROWD_REQUEST ="crowd_request";
	
	public static final String TEMPLATE_CLIENT_ERROR ="client_error";

	public static final String TEMPLATE_CONTACT ="contact";

	public static final String MAIl_TEMPLATE_FOLDER_QUANT_UX ="qux";

	public static final String TEMPLATE_HTML_FRAME ="frame";

	private final MailClient mail;
		
	private Handlebars handlebars;
	
	private Map<String, Template> txtTemplates = new HashMap<String, Template>();
	
	private Map<String, Template> htmlTemplates = new HashMap<String, Template>();
	
	private Vertx vertx;
	
	private Model validator;
	
	public static MailHandler instance;
	
	public static MailHandler start(Vertx vertx, MailClient client, String from, String bus, String templateFolder){
	
		return new MailHandler(vertx, client, from, bus, templateFolder);
	}
	
	public MailHandler(Vertx vertx, MailClient mail, String from, String bus, String templateFolder){	
		logger.info("constructor() > "  + from + " @ " + bus);
		this.busAdress = bus;
		this.from = from;
		
		this.mail = mail;		
		this.vertx = vertx;

		/**
		 * Create model for validation
		 */
		validator = new ModelFactory().create("MailMessage")
				.addString(FIELD_TO)
				.addString(FIELD_SUBJECT)
				.addString(FIELD_TEMPLATE)
				.addObject(FIELD_PAYLOAD)
				.build();

		handlebars = new Handlebars();

		/**
		 * Load mail templates here. Load as blocking code,
		 * because in fat-jar the files will be unzipped first.
		 */
		vertx.executeBlocking(handler -> {
			readTemplate(templateFolder, TEMPLATE_PASSWORD_RESET);
			readTemplate(templateFolder, TEMPLATE_USER_CREATED);
			readTemplate(templateFolder, TEMPLATE_TEAM_ADDED);
			readTemplate(templateFolder, TEMPLATE_CROWD_REQUEST);
			readTemplate(templateFolder, TEMPLATE_CLIENT_ERROR);
			readTemplate(templateFolder, TEMPLATE_CONTACT);
			readTemplate(templateFolder, TEMPLATE_PASSWORD_RESET);
			readTemplate(templateFolder, TEMPLATE_USER_LOGN_NOT_PAID);
			readTemplate(templateFolder, TEMPLATE_HTML_FRAME);
			handler.complete();
		}, result -> {
			logger.info("constructor() > loaded mails...");
		});


		/**
		 * Link to event bus.
		 */
		EventBus eb = vertx.eventBus();
		eb.consumer(busAdress, this);		

		
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
				
				if(txtTemplates.containsKey(templateID) && htmlTemplates.containsKey(templateID)){
					MailMessage mm = new MailMessage();
					mm.setFrom(from);
					mm.setTo(msg.getString(FIELD_TO));
					mm.setSubject(msg.getString(FIELD_SUBJECT));
					if(msg.containsKey(FIELD_BCC)){
						mm.setBcc(msg.getString(FIELD_BCC));
					}

					if(txtTemplates.containsKey(templateID)){					
						Template txtTemplate = txtTemplates.get(templateID);	
						String txt = txtTemplate.apply(msg.getJsonObject(FIELD_PAYLOAD).getMap());
						mm.setText(txt);					
					}
					
					if(htmlTemplates.containsKey(templateID)){
						Template htmlTemplate = htmlTemplates.get(templateID);	
						String innerHTML = htmlTemplate.apply(msg.getJsonObject(FIELD_PAYLOAD).getMap());

						Template frame = htmlTemplates.get(TEMPLATE_HTML_FRAME);
						Map<String, String> frameData = new HashMap();
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


	
	/**
	 * Helper method to construct valid eventbus messages
	 * 
	 * @param to
	 * @param subject
	 * @param template
	 * @param payload
	 * @return
	 */
	public static JsonObject createMessage(String to, String subject, String template, JsonObject payload){
		return new JsonObject()
			.put(FIELD_TO, to)
			.put(FIELD_SUBJECT, subject)
			.put(FIELD_TEMPLATE,template)
			.put(FIELD_PAYLOAD, payload);
	};
	
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
