package com.qux.acl;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.model.AppPart;
import com.qux.model.Comment;
import com.qux.model.Team;
import com.qux.model.User;
import com.qux.util.DB;

/**
 * The CommentAcl works like following:
 * 
 *  - Every user that can read, can also create new app parts
 *  
 *  - A user can only change parts if they userID property is set
 *    in the AppPart model, so they can only edit and delete there
 *    own post.
 *    
 * 
 * @author klaus_schaefers
 *
 */
public class CommentAcl extends AppAcl{
	
	private Logger logger = LoggerFactory.getLogger(CommentAcl.class);
	
	private final String team_db;
	
	private final String comment_db;
	
	public CommentAcl(MongoClient client){
		super(client);
		comment_db = DB.getTable(Comment.class);
		team_db = DB.getTable(Team.class);
	}

	
	@Override
	public void canCreate(User user, RoutingContext event, Handler<Boolean> handler) {
		canRead(user, event, handler);
	}
	


	@Override
	public void canWrite(User user, RoutingContext event, Handler<Boolean> handler) {
		

		String appID = event.request().params().get("appID");
		String commentID= event.request().params().get("commentID");
		
	
		String json = event.getBodyAsString();
		
		if(json!=null && !json.isEmpty()){
			/**
			 * Exception for the CanvasComments...
			 */
			JsonObject comment = event.getBodyAsJson();
			if(comment.containsKey("type") && Comment.TYPE_CANVAS.equals(comment.getString("type"))){
				logger.debug("canWrite() > Update 'ScreenComment'"); 
				client.count(team_db, Team.canWrite(user, appID), res->{
					if(res.succeeded()){
						assertOne(res, handler, event);
					} else {
						handler.handle(false);
					}
				});
			} else {
				//System.out.println("Can Write > " + appID + " " + commentID);
				checkAuthor(user, event, handler, appID, commentID);	
			}	
		} else {	
			//System.out.println("Can Write > " + appID + " " + commentID);
			checkAuthor(user, event, handler, appID, commentID);			
		}
	}


	private void checkAuthor(User user, RoutingContext event,Handler<Boolean> handler, String appID,String commentID) {
		
		/**
		 * First check ACL. Everybody that can read, can also comment...
		 */
		super.canRead(user, event, allowed->{
			
			if(allowed){	
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