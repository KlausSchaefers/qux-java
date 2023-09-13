package com.qux.rest;

import com.qux.acl.AppPartAcl;
import com.qux.acl.RoleACL;
import com.qux.auth.ITokenService;
import com.qux.model.AppPart;
import com.qux.model.CommandStack;
import com.qux.model.Model;
import com.qux.util.Mail;
import com.qux.validation.CommandStackValidator;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class CommandStackREST extends AppPartREST<CommandStack>{

	public CommandStackREST(ITokenService tokenService, MongoClient db) {
		super(tokenService, db, CommandStack.class, "commandStackID");
		this.setValidator(new CommandStackValidator());
		this.setACL(new RoleACL( new AppPartAcl(db)));
	}
	
	/*********************************************************
	 * Create Default
	 *********************************************************/

	
	protected JsonObject createInstance(RoutingContext event, String appID){
		return new JsonObject("{\"stack\":[],\"pos\":0,\"lastUUID\":0,\"appID\":\"" + appID + "\"}");
	}

	/**
	 * removes the
	 * @return
	 */
	public Handler<RoutingContext> undo() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				setPos(event, -1);
			}
		};
	}
	
	/**
	 * removes the
	 * @return
	 */
	public Handler<RoutingContext> redo() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				setPos(event, 1);
			}
		};
	}

	public void setPos(RoutingContext event, int dif) {
		String appID  = getId(event, "appID");
		if(appID == null){
			returnError(event, 404);
		}
		
		this.acl.canWrite(getUser(event), event, allowed -> {
			if (allowed) {
				setPos(event, dif, appID);
			} else {
				error("setPos", "User " + getUser(event) + " tried to pop ", event);
				Mail.error(event, "CommandStackRest.setPos() > User "+ getUser(event)+ " tried to setPos without permission");
				returnError(event, 401);
			}
		});
			
	}


	public void setPos(RoutingContext event, int dif, String appID) {

		mongo.findOne(table, AppPart.findByApp(appID), null, res -> {
			
			if(res.succeeded()){
				
				JsonObject stack = res.result();
				String id = stack.getString("_id");
				int newPos = stack.getInteger("pos") + dif;
								
				JsonObject update = new JsonObject()
					.put("$set", new JsonObject().put("pos", newPos));
					
				mongo.update(table,  Model.findById(id), update, res2 -> {
					if(res2.succeeded()){			
						JsonObject response = new JsonObject()
							.put("pos", newPos);
						
						returnJson(event, response);;
					} else {
						res2.cause().printStackTrace();
						returnError(event, "stack.update.error");
					}
					
				});
				
			
			} else {
				logger.error("setPos() > User "+ getUser(event)+ " tried to setPos on not existing app");
				Mail.error(event, "CommandStackRest.setPos() > User "+ getUser(event)+ " tried to setPos on not existing app");
				returnError(event, 404);
			}
		});	
	}
	
	/*********************************************************
	 * Add
	 *********************************************************/

	/**
	 * removes the
	 * @return
	 */
	public Handler<RoutingContext> pop() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				pop(event);
			}
		};
	}

	public void pop(RoutingContext event) {
		String appID  = getId(event, "appID");

		if(appID == null){
			returnError(event, 404);
		}
		
		/**
		 * validate
		 */
		String value = event.request().params().get("count");
		try{
			int count = Integer.parseInt(value);
			this.acl.canWrite(getUser(event), event, allowed -> {
					if (allowed) {
						pop(event, count, appID);
					} else {
						error("pop", "User " + getUser(event) + " tried to pop ", event);
						Mail.error(event, "CommandStackRest.pop() > User " + getUser(event) + " tried to pop ");
						returnError(event, 401);
					}
				});
			
		}catch(Exception e){
			returnError(event, "stack.remove.error");
		}
		
		
		
	}

	/**
	 * http://docs.mongodb.org/manual/reference/operator/update/pop/#up._S_pop
	 */
	public void pop(final RoutingContext event, final int count, final String appID) {

		mongo.findOne(table, AppPart.findByApp(appID), null, res -> {
			
			if(res.succeeded()){
				
				JsonObject commandStack = res.result();
				final int stackLength = commandStack.getJsonArray("stack").size();
				final int pos = commandStack.getInteger("pos") ;
				final int newPos = Math.min(pos,stackLength -count);
				
	
				/**
				 * Mongo $pop does not work here, as it removes only *one* element!
				 */
				JsonArray stack = commandStack.getJsonArray("stack");
				for(int i=0; i< count; i++){
					if(stackLength- (1+i) >= 0){
						stack.remove(stackLength- (1+i));
					}
				}
				commandStack.put("pos", newPos);
	
					
				mongo.save(table, commandStack, res2 -> {
					if(res2.succeeded()){
						
						JsonObject response = new JsonObject()
							.put("pos", newPos);
						
						returnJson(event, response);;
					} else {
						
						res2.cause().printStackTrace();
						returnError(event, "stack.update.error");
					}
					
				});
				
			
			} else {
				Mail.error(event, "CommandStackRest.pop() > User " + getUser(event) + " tried to pop not exiting app ");
				returnError(event, 404);
			}
		});	
	}

	/*********************************************************
	 * Shift
	 *********************************************************/

	public Handler<RoutingContext> shift() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				shift(event);
			}
		};
	}

	public void shift(RoutingContext event) {
		String appID  = getId(event, "appID");

		if(appID == null){
			returnError(event, 404);
		}

		try {
			String value = event.request().params().get("count");
			int count = Integer.parseInt(value);
			this.acl.canWrite(getUser(event), event, allowed -> {
				if (allowed) {
					shift(event, count, appID);
				} else {
					error("add", "User " + getUser(event) + " tried to shift ", event);

					returnError(event, 401);
				}
			});
		} catch (Exception err) {
			error("add", "User " + getUser(event) + " tried to shift ", event);
		}
	}

	private void shift(RoutingContext event, int count, String appID) {
		mongo.findOne(table, AppPart.findByApp(appID), null, res -> {

			if(res.succeeded()){

				JsonObject commandStack = res.result();
				final JsonArray stack = commandStack.getJsonArray("stack");
				final int pos = commandStack.getInteger("pos") ;
				final int newPos = Math.max(0, pos - count);
				final int max = Math.min(count, stack.size());

				for(int i=0; i< max; i++){
					stack.remove(0);
				}
				commandStack.put("pos", newPos);

				mongo.save(table, commandStack, res2 -> {
					if(res2.succeeded()){
						returnJson(event, commandStack);;
					} else {
						res2.cause().printStackTrace();
						returnError(event, "stack.update.error");
					}
				});

			} else {
				returnError(event, 404);
			}
		});
	}


	/*********************************************************
	 * Add
	 *********************************************************/
	

	public Handler<RoutingContext> add() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				add(event);
			}
		};
	}

	public void add(RoutingContext event) {
		String appID  = getId(event, "appID");

		if(appID == null){
			returnError(event, 404);
		}
		
		JsonObject command = event.getBodyAsJson();
		this.validator.validate(command, false, errors ->{
			
			if(errors.isEmpty()){
				this.acl.canWrite(getUser(event), event, allowed -> {
					if (allowed) {
						add(event, command, appID);
					} else {
						error("add", "User " + getUser(event) + " tried to add ", event);
						Mail.error(event, "CommandStackRest.add() > User " + getUser(event) + " tried to add command without write!" + command.encodePrettily());
						returnError(event, 401);
					}
				});
			} else {
				Mail.error(event, "CommandStackRest.add() > User " + getUser(event) + " tried to add shitty command > ");
				returnError(event, errors);
			}
		});
		
	}

	/**
	 * http://docs.mongodb.org/manual/reference/operator/update/addToSet/#up._S_addToSet
	 * @param event
	 * @param command
	 * @param appID
	 */
	public void add(RoutingContext event, JsonObject command, String appID) {

		/**
		 * FIXME: we should just check if the command stack exists. and then
		 * add an element
		 */
		command.put("userID", getUser(event).getId());
		mongo.findOne(table, AppPart.findByApp(appID), null, res -> {
			
			if(res.succeeded()){
				
				JsonObject stack = res.result();
				String id = stack.getString("_id");
				int newPos = stack.getInteger("pos",0) +1;
				int lastUUID = stack.getInteger("lastUUID",0) +1;
				
				
				/**
				 * If stack.length() > 500 
				 * cut off 100?
				 */
					
				JsonObject update = new JsonObject()
					.put("$push", new JsonObject().put("stack", command))
					.put("$set", new JsonObject().put("pos", newPos).put("lastUUID", lastUUID));
					
				mongo.update(table,  Model.findById(id), update, res2 -> {
					if(res2.succeeded()){
						
						JsonObject response = new JsonObject()
							.put("command", command)
							.put("pos", newPos)
							.put("lastUUID", lastUUID);
						
						returnJson(event, response);;
					} else {
						returnError(event, "stack.update.error");
						this.logger.error("CommandStackRest.add() > Could not add", res2.cause());
						Mail.error(event, "CommandStackRest.add() > Mongo error "+ res2.cause() + " >> " + update.encodePrettily());
					}
					
				});
				
			
			} else {
				Mail.error(event, "CommandStackRest.add() > User " + getUser(event) + " tried to add command to not exsiting app");
				returnError(event, 404);
			}
		});	
	}
	
	
}
