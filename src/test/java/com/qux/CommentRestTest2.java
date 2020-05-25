package com.qux;

import com.qux.model.App;
import com.qux.model.Comment;
import com.qux.model.Invitation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)

public class CommentRestTest2 extends MatcTestCase {


	@Test
	public void test(TestContext context){
		log("test", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
	
		postUser("klaus", context);
		postUser("dennis", context);
		
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		App app = postApp("klaus_app_public", true, context);
		
		Map<Object, String> invitations = getInvitations(app);
		String hash = invitations.get(1);
		log(" -> hash:", hash);
		
		logout();

		
		Comment comment1 = postComment(app, hash, "ScreenComment", "Comment 1", "s1", context);
		postComment(app, hash, "ScreenComment", "Comment 2", "s2", context);
		postComment(app, hash, "ScreenComment", "Comment 3", "s1", context);

		
		JsonArray comments = getList("/rest/comments/hash/" +hash+"/" + app.getId() + ".json"); 
		context.assertEquals(3, comments.size());
		
		comments = getList("/rest/comments/hash/" +hash+"/" + app.getId() + "/ScreenComment.json"); 
		context.assertEquals(3, comments.size());
				
		comments = getList("/rest/comments/hash/" +hash+"/" + app.getId() + "/s1/ScreenComment.json"); 
		context.assertEquals(2, comments.size());
		
		comments = getList("/rest/comments/hash/" +hash+"/" + app.getId() + "/s2/ScreenComment.json"); 
		context.assertEquals(1, comments.size());
		
		
		
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		postComment(app, hash, "ScreenComment", "Comment 4", "s2", context);
		Comment comment5 = postComment(app, hash, "ScreenComment", "Comment 5", "s2", context);
		
		
		comments = getList("/rest/comments/hash/" +hash+"/" + app.getId() + "/s2/ScreenComment.json"); 
		context.assertEquals(3, comments.size());
		
		updateComment(hash, comment5, "Comment 5a", context);
		updateCommentError(hash, comment1, "Comment 1a", context);

		
		logout();
		
		updateCommentError(hash, comment5, "Comment 5b", context);
		
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		updateComment(hash, comment5, "Comment 5c", context);
		
		
		deleteComment(hash, comment5, context);
		deleteCommentError(hash, comment1, context);
				
		
		log("test", "exit");
	}
	

	private Map<Object, String> getInvitations(	App app ){
		
		Map<Object, String> result = new HashMap<Object, String>();
		
		List<JsonObject> list = client.find(inv_db, Invitation.findByApp(app.getId()));
		JsonObject invitations = Invitation.getInvitationFromList(list);	
		
		invitations.forEach(c->{
			result.put(c.getValue(), c.getKey());
		});
		
		return result;
	}
	
	
	public void assertComments(String url, int x,  TestContext context ){
		JsonArray comments = getList(url); 
		context.assertEquals(x, comments.size());
		log("assertComments", "" + comments);
		
		for(int i=0; i< comments.size(); i++){
			JsonObject c = comments.getJsonObject(0);
			context.assertTrue(c.containsKey("user"));
		}
	}
	
	public void assertCommentsError(String url, TestContext context ){
		JsonArray comments = getList(url); 
		context.assertEquals(404, comments.getInteger(0));
		log("assertCommentsError", "" + comments);
	}
	
	public Comment deleteComment(String hash, Comment comment, TestContext context ){

		JsonObject result = delete("/rest/comments/hash/" + hash +"/"+ comment.getAppID() + "/" + comment.getId() + ".json");
		log("deleteComment", "" + result);
		
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(!result.containsKey("errors"));

		return comment;
	}
	
	
	
	public void deleteCommentError(String hash, Comment comment, TestContext context ){

		JsonObject result = delete("/rest/comments/hash/" + hash +"/" + comment.getAppID() + "/" + comment.getId() + ".json");
		log("deleteCommentError", "" + result);
		
		context.assertTrue(result.containsKey("error") || result.containsKey("errors"));

	}
	

	public Comment updateComment(String hash, Comment comment,  String message, TestContext context ){

		comment.setMessage(message);
		
		log("updateComment", "" + comment);
		JsonObject result = post("/rest/comments/hash/" + hash +"/" + comment.getAppID() + "/" + comment.getId() + ".json", mapper.toVertx(comment));
		log("updateComment", "" + result);
		
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(!result.containsKey("errors"));
		context.assertTrue(result.containsKey("userID"));
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(result.containsKey("lastUpdate"));
		context.assertEquals(result.getString("message"), message);
		
		return comment;
	}
	
	public Comment updateCommentError(String hash, Comment comment,  String message, TestContext context ){

		comment.setMessage(message);
		
		JsonObject result = post("/rest/comments/hash/" + hash +"/" + comment.getAppID() + "/" + comment.getId() + ".json", mapper.toVertx(comment));
		log("updateCommentError", "" + result);
		
		context.assertTrue(result.containsKey("error") || result.containsKey("errors"));

		
		return comment;
	}
	
	public Comment postComment(App app, String hash, String type, String msg, String ref, TestContext context ){

		Comment comment = new Comment();
		comment.setAppID("Evil");
		comment.setMessage(msg);
		comment.setType(type);
		if(ref!=null)
			comment.setReference(ref);
		
		JsonObject result = post("/rest/comments/hash/" + hash +"/" + app.getId(), mapper.toVertx(comment));
		log("postComment", "" + result);
		
		context.assertTrue(result.containsKey("_id"));
		context.assertTrue(!result.containsKey("error"));
		context.assertTrue(result.containsKey("userID"));
		context.assertTrue(result.containsKey("appID"));
		context.assertTrue(result.containsKey("created"));
		context.assertEquals(app.getId(), result.getString("appID"));
		
		comment.setId(result.getString("_id"));
		comment.setAppID(result.getString("appID"));
		
		return comment;
	}
	
	
	public void postCommentError(App app, String hash, String type, String msg, String ref, TestContext context ){

		Comment comment = new Comment();
		comment.setAppID(app.getId());
		comment.setMessage(msg);
		comment.setType(type);
		if(ref!=null)
			comment.setReference(ref);
		
		JsonObject result = post("/rest/comments/hash/" + hash +"/" + app.getId(), mapper.toVertx(comment));
		log("postCommentError", "" + result);
		
		context.assertTrue(!result.containsKey("_id"));
		context.assertTrue(result.containsKey("error") ||result.containsKey("errors") );
		
	}
	
	
}
