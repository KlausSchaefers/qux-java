package com.qux;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.qux.acl.Acl;
import com.qux.model.App;
import com.qux.model.Image;
import com.qux.model.User;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ImageRestTest extends MatcTestCase {
	
	
	@Test
	public void test(TestContext context) throws InterruptedException, IOException {
		log("test_MutliUser", "enter");

		cleanUp();

		deploy(new MATC(), context);

		/**
		 * create user
		 */
		User klaus = postUser("klaus", context);
		User bernd = postUser("bernd", context);
		User dennis = postUser("dennis", context);
		

		assertLogin(context, klaus, "123456789");
		
		App klaus_app_public = postApp("klaus_app_public", true, context);
		App klaus_app_private = postApp("klaus_app_private", false, context);
		createPermission(dennis, klaus_app_private, Acl.READ, context);
		createPermission(bernd, klaus_app_private, Acl.WRITE, context);
		assertList("/rest/images/" + klaus_app_private.getId() + ".json", 0, context);
		
		/**
		 * do an upload
		 */
		postImage(klaus_app_public, context, "test.png");
		assertList("/rest/images/" + klaus_app_public.getId() + ".json", 1, context);

		postImage(klaus_app_private, context, "test.png");
		postImage(klaus_app_private, context, "2000x4000_white.png");
		assertList("/rest/images/" + klaus_app_private.getId() + ".json", 2, context);

		/**
		 * Login as Dennis, he cannot write, but read
		 */
		assertLogin(context, dennis, "123456789");
		postImageError(klaus_app_private, context);
		postImageError(klaus_app_public, context);
		JsonArray images = assertList("/rest/images/" + klaus_app_private.getId() + ".json", 2, context);
		
		assertRaw(context, klaus_app_private, images);

		/**
		 * Logout. Private images cannot be seen, public onses can
		 */
		logout();
		assertRawError(context, klaus_app_private, images);

		/**
		 * Guest cannot post
		 */
		postImageError(klaus_app_private, context);
		postImageError(klaus_app_public, context);


		/**
		 * Examples can be loaded
		 */
		JsonArray publicImages = assertList("/rest/images/" + klaus_app_public.getId() + ".json", 1, context);
		assertRaw(context, klaus_app_public, publicImages);

		
	}

	private void assertRaw(TestContext context, App app, JsonArray images) throws IOException {
		for(int i=0; i< images.size();i++){
			JsonObject img = images.getJsonObject(i);
			context.assertTrue(img.getInteger("width") > 0);
			context.assertTrue(img.getInteger("height") > 0);
			
			
			InputStream is = getRaw("/rest/images/" + img.getString("url") + "?token=" + this.getJWT());
		
			
			context.assertTrue(is !=null);
			
			BufferedImage image = ImageIO.read(is);
		
			log("assertRaw", img.getString("url") + " > " +image.getWidth() );
			is.close();
		}
	}

	private void assertRawError(TestContext context, App app, JsonArray images) throws IOException {
		for(int i=0; i< images.size();i++){
			JsonObject img = images.getJsonObject(i);
			context.assertTrue(img.getInteger("width") > 0);
			context.assertTrue(img.getInteger("height") > 0);

			InputStream is = getRaw("/rest/images/" + img.getString("url") + "?token=" + this.getJWT());
			context.assertNull(is);

		}
	}



}
