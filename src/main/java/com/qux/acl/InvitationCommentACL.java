package com.qux.acl;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import com.qux.model.AppPart;
import com.qux.model.Comment;
import com.qux.model.Invitation;
import com.qux.model.User;
import com.qux.util.DB;

public class InvitationCommentACL extends MongoAcl implements Acl{
	
	private final String inv_db;
	
	private final String comment_db;	
	
	public InvitationCommentACL(MongoClient client){
		super(client);
		comment_db = DB.getTable(Comment.class);
		inv_db = DB.getTable(Invitation.class);
	}

	
	@Override
	public void canCreate(User user, RoutingContext event, Handler<Boolean> handler) {
		canRead(user, event, handler);
	}


	@Override
	public void canRead(User user, RoutingContext event, Handler<Boolean> handler) {
		String appID = event.request().params().get("appID");
		String hash = event.request().params().get("hash");

		client.findOne(inv_db, Invitation.canTest(appID, hash), new JsonObject().put("_id", 1), res->{
						
			if(res.succeeded()){
				JsonObject result = res.result();
				if(result!=null){
					handler.handle(true);
				} else {
					handler.handle(false);
				}				
			} else {
				handler.handle(false);
			}
		});
	}

	
	


	@Override
	public void canWrite(User user, RoutingContext event, Handler<Boolean> handler) {
		String appID = event.request().params().get("appID");
		String hash = event.request().params().get("hash");
		String commentID = event.request().params().get("commentID");
		
		client.count(inv_db, Invitation.canTest(appID, hash), res->{
			if(res.succeeded()){
				/**
				 * now check if the user is the author
				 */
				client.count(comment_db, AppPart.isAuthor(user.getId(), commentID), res2->{
					assertOne(res2, handler, event);
				});
				
			} else {
				handler.handle(false);
			}
		});
		
		
	}



	@Override
	public void canDelete(User user, RoutingContext event, Handler<Boolean> handler) {
		canWrite(user, event, handler);
	}
	


}
