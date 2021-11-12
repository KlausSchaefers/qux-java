package com.qux.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.MailResult;

import java.util.ArrayList;
import java.util.List;

public class DebugMailClient implements MailClient{
	
	private boolean printMails = false;
	


	private static final List<MailMessage> mails = new ArrayList<>();

	public DebugMailClient() {

	}

	public DebugMailClient(boolean print) {
		this.printMails = print;
	}

	@Override
	public void close() {
	}

	public static List<MailMessage> getMails() {
		return mails;
	}

	@Override
	public MailClient sendMail(MailMessage mail,Handler<AsyncResult<MailResult>> handler) {

		mails.add(mail);
		
		if(printMails){
			System.out.println("---------------------------------------------------------------------------------- ");
			System.out.println("");
			System.out.println("TO      : " + mail.getTo());
			System.out.println("BCC     : " + mail.getBcc());
			System.out.println("Subject : " + mail.getSubject());
			System.out.println("");
			System.out.println(mail.getText());
			System.out.println("---------------------------------------------------------------------------------- ");
		}
		
		handler.handle(new AsyncResult<MailResult>() {

			@Override
			public Throwable cause() {
				return null;
			}

			@Override
			public boolean failed() {
				return false;
			}

			@Override
			public MailResult result() {
				return null;
			}

			@Override
			public boolean succeeded() {
				// TODO Auto-generated method stub
				return true;
			}
		});
		
		return this;
	}

	@Override
	public String toString() {
		return "DebugMailClient []";
	}

}
