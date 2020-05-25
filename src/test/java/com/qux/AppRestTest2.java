package com.qux;

import java.util.List;

import com.qux.acl.Acl;
import com.qux.model.App;
import com.qux.model.Team;
import com.qux.model.User;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class AppRestTest2 extends MatcTestCase {
	

	@Test
	public void test_MutliUser(TestContext context){
		log("test_MutliUser", "enter");
		
		cleanUp();
		
		deploy(new MATC(), context);
		
		/**
		 * create user
		 */
		User klaus = postUser("klaus", context);
		User bernd = postUser("bernd", context);
		User dennis = postUser("dennis", context);
		
		/**
		 * create klaus apps
		 */
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		App klaus_app_public = postApp("klaus_app_public", true, context);
		App klaus_app_private1 = postApp("klaus_app_private1", false, context);
		App klaus_app_private2 = postApp("klaus_app_private2", false, context);
		assertUserList(3, context);
		
		/**
		 * create bernd apps
		 */
		assertLogin(context, "bernd@quant-ux.de", "123456789");
		App bernd_app_public = postApp("bernd_app_public", true, context);
		App bernd_app_private = postApp("bernd_app_private", false, context);
		assertUserList(2, context);
		
		/**
		 * dennis does not have apps
		 */
		assertLogin(context, "dennis@quant-ux.de", "123456789");
		assertUserList(0, context);
		assertPublicList(2, context);
		
		
		
		/**
		 * KLAUS: add acl for apps. 
		 * 
		 * - Bernd can read app1
		 * - bernd can write app2
		 * - bernd *CANNOT* set by as the owner
		 */
		
		assertLogin(context, "klaus@quant-ux.de", "123456789");
	
		createPermission(bernd, klaus_app_private1, Acl.READ, context);
		createPermission(bernd, klaus_app_private2, Acl.WRITE, context);
		createPermissionError(bernd, klaus_app_private2, Acl.OWNER, context);
		
		List<JsonObject> berndACL = client.find(team_db, Team.findByUser(bernd));
		context.assertEquals(4, berndACL.size(), "Bernd wrong # of acls");
		
		/**
		 * BERND: add acl for apps. 
		 * 
		 * - Klaus can read app1
		 * - Klaus can write app2
		 */
		assertLogin(context, "bernd@quant-ux.de", "123456789");
		createPermission(klaus, bernd_app_private, Acl.READ, context);
		createPermission(klaus, bernd_app_public, Acl.WRITE, context);
		
		List<JsonObject> klaus_acl = client.find(team_db, Team.findByUser(klaus));
		context.assertEquals(5, klaus_acl.size(), "Klaus wrong # of acls");
		
		/**
		 * now check if bernd can read and write klaus apps
		 */			
		getApp(klaus_app_private1, context);
		getApp(klaus_app_private2, context);
		getApp(klaus_app_public, context);
		updateAppError(klaus_app_private1, "Bernd should not write this", context);
		updateAppError(klaus_app_public, "Bernd should not write this", context);
		updateApp(klaus_app_private2, "Bernd was here", context);
		log("test_MultiUser", "get() >" + getApp(klaus_app_private2, context).getString("name"));
		
		/**
		 * no check that klaus can write bernd and his stuff
		 */
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		getApp(klaus_app_private1, context);
		getApp(klaus_app_private2, context);
		getApp(bernd_app_private, context);
		getApp(bernd_app_public, context);
		updateAppError(bernd_app_private, "KLaus should not write this", context);
		updateApp(bernd_app_public, "Klaus was here", context);
		updateApp(klaus_app_private1, "Klaus was here 2", context);
		updateApp(klaus_app_private2, "Klaus was here 3", context);
		log("test_MultiUser", "get() >" + getApp(bernd_app_public, context).getString("name"));
		
		
		/**
		 * now check dennis
		 */
		assertLogin(context, "dennis@quant-ux.de", "123456789");
		getApp(bernd_app_public, context);
		getApp(klaus_app_public, context);
		getAppError(klaus_app_private1, context);
		getAppError(klaus_app_private2, context);
		getAppError(bernd_app_private, context);
		
		
		
		/**
		 * Check team for klaus
		 */
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		JsonArray suggestion = getList("/rest/apps/" + klaus_app_private1.getId() + "/suggestions/team.json");
		log("test_MutliUser", "suggestion > "+ suggestion);
		context.assertEquals(2, suggestion.size());
		
		
		JsonArray team = getList("/rest/apps/" + klaus_app_private1.getId() + "/team.json");
		log("test_MutliUser", "team > "+ team);
		context.assertEquals(2, team.size());
	
		team = getList("/rest/apps/" + klaus_app_private2.getId() + "/team.json");
		log("test_MutliUser", "team > "+ team);
		context.assertEquals(2, team.size());
		
		
		/**
		 * logout and check team does not come!
		 */
		logout();
		
		suggestion = getList("/rest/apps/" + klaus_app_private1.getId() + "/suggestions/team.json");
		log("test_MutliUser", "suggestion(error) > "+ suggestion);
		context.assertEquals(404, suggestion.getInteger(0));
		
		team = getList("/rest/apps/" + klaus_app_private1.getId() + "/team.json");
		log("test_MutliUser", "team(error) > "+ team);
		context.assertEquals(404, suggestion.getInteger(0));
		
		
		/**
		 * Login as klaus, and *UPDATE* bernds permissions
		 */
		assertLogin(context, "klaus@quant-ux.de", "123456789");
		updatePermission(bernd, klaus_app_private2, Acl.READ, context);
		deletePermission(bernd, klaus_app_private1, context);
		
		/**
		 * check that the acl update were correct
		 */
		assertLogin(context, "bernd@quant-ux.de", "123456789");
		
		getApp(klaus_app_public, context);
		getApp(klaus_app_private2, context);
		getAppError(klaus_app_private1, context);
		updateAppError(klaus_app_private1, "Bernd should not write this", context);
		updateAppError(klaus_app_public, "Bernd should not write this", context);
		updateAppError(klaus_app_private2, "Bernd was here", context);
		
		updatePermissionError(bernd, klaus_app_private1, Acl.READ, context);
		updatePermissionError(bernd, klaus_app_private2, Acl.WRITE, context);
		updatePermissionError(dennis, klaus_app_private2, Acl.READ, context);
		
		printRestPerformance();
		
		log("test_MutliUser", "exit");
	}
	
	
	
	
	
	
}
