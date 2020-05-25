package com.qux;

import com.qux.acl.Acl;
import com.qux.model.App;
import com.qux.model.Comment;
import com.qux.model.User;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)

public class CommentRestTest extends MatcTestCase {


	@Test
	public void test(TestContext context){
		log("test", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
	
		postUser("klaus", context);
		User dennis = postUser("dennis", context);
		
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		App klaus_app_public = postApp("klaus_app_public", true, context);
		App klaus_app_private1 = postApp("klaus_app_private1", false, context);
		
		
		Comment comment_1 = postComment(klaus_app_public, "overview", "klaus 1", null, context);
		postComment(klaus_app_public, "overview", "klaus 2", null, context);
		postComment(klaus_app_public, "test", "klaus 3", "a", context);
		postComment(klaus_app_public, "test", "klaus 4", "b", context);
		
		postComment(klaus_app_private1, "overview", "4", null, context);
		postComment(klaus_app_private1, "overview", "5", null, context);
		
		/**
		 * now dennis want to comment, but cannot on klaus_private
		 */
		assertLogin(context, "dennis@quant-ux.de", "123456789");
		postComment(klaus_app_public, "overview", "dennis 1", null, context);
		postComment(klaus_app_public, "overview", "dennis 2", null, context);
		postCommentError(klaus_app_private1, "overview", "dennis 3 should not write here", null, context);
		
		
		/**
		 * now check find methods
		 */
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		JsonArray comments = getList("/rest/comments/apps/" + klaus_app_public.getId() + ".json"); 
		context.assertEquals(6, comments.size());
		
		comments = getList("/rest/comments/apps/" + klaus_app_public.getId() + "/overview.json"); 
		context.assertEquals(4, comments.size());
		
		// make sure the body handler does not allow overwriting! We set here the appID which is also
		// defined in the route.
		//comments = getList("/rest/comments/apps/" + klaus_app_public.getId() + "/overview.json?appID=asdasd"); 
		//context.assertEquals(4, comments.size(), "App ID");
		
		comments = getList("/rest/comments/apps/" + klaus_app_public.getId() + "/test.json"); 
		context.assertEquals(2, comments.size());
	
		comments = getList("/rest/comments/apps/" + klaus_app_public.getId() + "/a/test.json"); 
		context.assertEquals(1, comments.size());
		
		comments = getList("/rest/comments/apps/" + klaus_app_private1.getId() + ".json"); 
		context.assertEquals(2, comments.size());
		
		/**
		 * dennis cannot read klaus_priavet comments
		 */
		assertLogin(context, "dennis@quant-ux.de", "123456789");
		
		comments = getList("/rest/comments/apps/" + klaus_app_public.getId() + "/a/test.json"); 
		context.assertEquals(1, comments.size());
		
		comments = getList("/rest/comments/apps/" + klaus_app_private1.getId() + ".json"); 
		context.assertEquals(401, comments.getInteger(0));
		
		updateCommentError(comment_1, "dennis hsould not do this", context);
		deleteCommentError(comment_1, context);
		
		assertCommentsError("/rest/comments/apps/" + klaus_app_private1.getId() + "/a/test.json", context);
		
		
		super.loglevel = 3;
		
		/**
		 *  klaus gives dennis now read access
		 */
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		createPermission(dennis, klaus_app_private1, Acl.READ, context);
	
		/**
		 * now dennis can comment
		 */
		assertLogin(context, "dennis@quant-ux.de", "123456789");
		Comment comment = postComment(klaus_app_private1, "overview", "dennis 3", null, context);
		log("test() > 'dennis 3' comment > ", comment.getId() + " " + comment.getAppID());
		
		comments = getList("/rest/comments/apps/" + klaus_app_private1.getId() + ".json"); 
		context.assertEquals(3, comments.size());
		
		/**
		 * now check update!
		 */
		updateComment(comment,"dennis 3(updated)", context);
		deleteComment(comment, context);
		
		
		comments = getList("/rest/comments/apps/" + klaus_app_private1.getId() + ".json"); 
		context.assertEquals(2, comments.size());
		
		
		/**
		 * logout. test guest. they cannot creatr, write or delete. just read.
		 */
		logout();
		
		postCommentError(klaus_app_private1, "overview", "private comment from guest", null, context);
		postCommentError(klaus_app_public, "overview", "public comment", null, context);
		updateCommentError(comment, "public comment", context);
		deleteCommentError(comment, context);
		
		/**
		 * but we can read public apps
		 */
		assertComments("/rest/comments/apps/" + klaus_app_public.getId() + "/a/test.json", 1, context); 
		assertCommentsError("/rest/comments/apps/" + klaus_app_private1.getId() + "/a/test.json", context);
		
		
		
		log("test", "exit");
	}
	
	
}
