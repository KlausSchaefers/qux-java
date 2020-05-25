package com.qux.util;

import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.qux.acl.Acl;
import com.qux.model.User;
import com.qux.validation.Validator;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public abstract class CrudREST extends REST {
	
	protected Validator valicator;
	
	protected Acl acl;
	

	public void setValidator(Validator v) {
		this.valicator = v;
	}
	
	public void setACL(Acl a) {
		this.acl = a;
	}


	abstract void getACLList(User user, Handler<String> handler);

	/********************************************************************************************
	 * Create
	 ********************************************************************************************/

	public Handler<RoutingContext> create() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				createAcl(event);
			}
		};
	}
	
	

	public void createAcl(RoutingContext event) {
		event.response().putHeader("content-type", "application/json");
		if (this.acl != null) {
			this.acl.canCreate(getUser(event), event , allowed -> {
				if (allowed) {
					this.createAllowed(event);
				} else {
					error("create", "User " + getUser(event) + " tried to  create " + event.request().path());
					returnError(event, 405);
				}
			});
		} else {
			this.createAllowed(event);
		}
	}

	private void createAllowed(RoutingContext event) {
		JsonObject json = getJson(event);
		if(json!=null){
			if (this.valicator != null) {
				this.valicator.validate(json, true, err -> {
					if (err.isEmpty()) {
						beforeCreate(event, json);
						create(event, json);
					} else {
						returnError(event, err);
					}
				});
			} else {
				beforeCreate(event, json);
				create(event, json);
			}
		} else{
			returnError(event, 405);
		}
		
	}
	
	
	protected void beforeCreate(RoutingContext event, JsonObject json){
		
	}
	




	protected abstract void create(RoutingContext event, JsonObject json);

	
	

	/********************************************************************************************
	 * Create
	 ********************************************************************************************/


	public Handler<RoutingContext> update() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				update(event);
			}
		};
	}

	
	public void update(RoutingContext event) {
	
		String id  = getId(event);
		if (this.acl != null) {
			this.acl.canWrite(getUser(event), event, allowed -> {
				if (allowed) {
					update(event, id);
				} else {
					User user = getUser(event);
					error("update", "User " + user + " tried to  update " + event.request().path());
					this.getACLList(user, userApps -> {
						error("update", "User " + user + " can read: " + userApps);
						Mail.error(event, "CrudRest.update() > User "+ getUser(event)+ " tried to UPDATE without permission. ACL: " + userApps);
					});
					returnError(event, 401);
				}
			});
		} else {
			this.update(event, id);
		}
	}
	
	public void update(RoutingContext event, String id) {
		
		JsonObject json = getJson(event);
		
		if(json!=null){
			if(this.valicator!=null){
				this.valicator.validate(json, false, new Handler<List<String>>() {
	
					@Override
					public void handle(List<String> errors) {
						
						if(errors.isEmpty()){
							update(event, id, json);
						} else {
							returnError(event, errors);
						}
						
					}
				});
			} else {
				this.update(event, id, json);
			}
		}else{
			returnError(event, 405);
		}
		
	
		
	}
	
	protected abstract void update(RoutingContext event, String id, JsonObject json);
	
	

	/********************************************************************************************
	 * Find
	 ********************************************************************************************/

	public Handler<RoutingContext> find() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				find(event);
			}
		};
	}

	public void find(RoutingContext event) {
		String id  = getId(event);

		if (this.acl != null) {
			this.acl.canRead(getUser(event), event, allowed -> {
				if (allowed) {
					find(event, id);
				} else {
					User user =  getUser(event);
					error("find", "User " + user + " tried to  read " + id);
					this.getACLList(user, userApps -> {
						error("find", "User " + user + " can read: " + userApps);
						Mail.error(event, "CrudRest.find() > User "+ getUser(event)+ " tried to read without permission. ACL: " + userApps);
					});
					returnError(event, 401);
				}
			});
		} else {
			this.find(event, id);
		}
	}
	
	
	protected abstract void find(RoutingContext event, String id);
	

	/********************************************************************************************
	 * Find By
	 ********************************************************************************************/

	public Handler<RoutingContext> findBy() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				findByAcl(event);
			}
		};
	}

	private void findByAcl(RoutingContext event) {
		if (this.acl != null) {
			this.acl.canRead(getUser(event), event, allowed -> {
				if (allowed) {
					findBy(event);
				} else {
					User user = getUser(event);
					error("findByAcl", "User " + user + " tried to  read " +event.request().path());
					this.getACLList(user, userApps -> {
						error("findByAcl", "User " + user + " can read: " + userApps);
						Mail.error(event, "CrudRest.findByAcl() > User "+ getUser(event)+ " tried to read without permission. ACL: " + userApps);
					});
					returnError(event, 401);
				}
			});
		} else {
			this.findBy(event);
		}
	}
	
	
	protected abstract void findBy(RoutingContext event);
	
	
	/********************************************************************************************
	 * Count By
	 ********************************************************************************************/

	public Handler<RoutingContext> countBy() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				countByAcl(event);
			}
		};
	}

	private void countByAcl(RoutingContext event) {
		if (this.acl != null) {
			this.acl.canRead(getUser(event), event, allowed -> {
				if (allowed) {
					countBy(event);
				} else {
					User user = getUser(event);
					error("countByAcl", "User " + user + " tried to  read " +event.request().path());
					this.getACLList(user, userApps -> {
						error("countByAcl", "User " + user + " can read: " + userApps);
						Mail.error(event, "CrudRest.countByAcl() > User "+ getUser(event)+ " tried to read without permission. ACL: " + userApps);;
					});

					returnError(event, 401);
				}
			});
		} else {
			this.findBy(event);
		}
	}
	
	
	protected abstract void countBy(RoutingContext event);
	

	/********************************************************************************************
	 * Delete
	 ********************************************************************************************/

	public Handler<RoutingContext> delete() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				delete(event);
			}
		};
	}

	public void delete(RoutingContext event) {
		String id  = getId(event);
		if (this.acl != null) {
			this.acl.canDelete(getUser(event), event, allowed -> {
				if (allowed) {
					delete(event, id);
				} else {
					error("delete", "User " + getUser(event) + " tried to  delete " + id);
					returnError(event, 401);
				}
			});
		} else {
			this.delete(event, id);
		}
	}
	
	protected abstract void delete(RoutingContext event, String id);
	
	
	/********************************************************************************************
	 * Helper
	 ********************************************************************************************/
	
	public Handler<RoutingContext> deleteBy() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				deleteBy(event);
			}
		};
	}

	public void deleteBy(RoutingContext event) {
		String id  = getId(event);
		if (this.acl != null) {
			this.acl.canDelete(getUser(event), event, allowed -> {
				if (allowed) {
					deleteBy(event, id);
				} else {
					error("delete", "User " + getUser(event) + " tried to  delete " + id);
					returnError(event, 401);
				}
			});
		} else {
			this.delete(event, id);
		}
	}
	
	protected abstract void deleteBy(RoutingContext event, String id);
	
	
	/********************************************************************************************
	 * Helper
	 ********************************************************************************************/

	protected JsonObject getPathQuery(RoutingContext event) {
		JsonObject query = new JsonObject();
		MultiMap map = event.request().params();
		List<Entry<String, String>> list = map.entries();
		for(Entry<String,String> entry : list){
			if(entry.getValue() != null){
				query.put(entry.getKey(),entry.getValue());
			}
		}
		return query;
	}
	
	protected JsonObject getPathQuery(RoutingContext event, Set<String> excluded) {
		JsonObject query = new JsonObject();
		
		MultiMap map = event.request().params();
		List<Entry<String, String>> list = map.entries();

		for(Entry<String,String> entry : list){
			if(!excluded.contains(entry.getKey())){
				if(entry.getValue() != null){
					query.put(entry.getKey(),entry.getValue());
				}
			}			
		}

		return query;
	}

	
	
}
