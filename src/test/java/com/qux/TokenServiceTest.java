package com.qux;

import com.qux.model.User;
import com.qux.util.TokenService;
import org.junit.Assert;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class TokenServiceTest {
	
	@Test
	public void test(){
		System.out.println("TokenServiceTest.test() > enter");
		
		TokenService.setSecret("123");
		
		JsonObject json = new JsonObject().put("_id", "id1").put("email", "klaus@quant-ux.de");
		String token = TokenService.getToken(json);
		
		Assert.assertNotNull(token);

		
		User user = TokenService.getUser(token);
		Assert.assertNotNull(user);
		Assert.assertEquals("id1", user.getId());
		Assert.assertEquals("klaus@quant-ux.de", user.getEmail());
		Assert.assertEquals(User.GUEST, user.getRole());
		Assert.assertNull(user.getName());
	}
	
	@Test
	public void test_user(){
		System.out.println("TokenServiceTest.test_user() > enter");
		
		TokenService.setSecret("123");
		
		JsonObject json = new JsonObject()
				.put("_id", "id1")
				.put("email", "klaus@quant-ux.de")
				.put("role", User.USER);
		String token = TokenService.getToken(json);
		
		Assert.assertNotNull(token);

		User user = TokenService.getUser(token);
		Assert.assertNotNull(user);
		Assert.assertEquals("id1", user.getId());
		Assert.assertEquals("klaus@quant-ux.de", user.getEmail());
		Assert.assertEquals(User.USER, user.getRole());
	}
	
	
	@Test
	public void test_wrong_secret(){
		System.out.println("TokenServiceTest.test_wrong_secret() > enter");
		
		TokenService.setSecret("123");
		
		JsonObject json = new JsonObject()
				.put("_id", "id1")
				.put("email", "klaus@quant-ux.de")
				.put("role", User.USER);
		String token = TokenService.getToken(json);
		
		Assert.assertNotNull(token);
		
		TokenService.setSecret("abc");
		
		User user = TokenService.getUser(token);
		
		Assert.assertNull(user);
	
	}
	
	@Test
	public void test_expired(){
		System.out.println("TokenServiceTest.test_wrong_secret() > enter");
		
		TokenService.setSecret("123");
		
		JsonObject json = new JsonObject()
				.put("_id", "id1")
				.put("email", "klaus@quant-ux.de")
				.put("role", User.USER);
		String token = TokenService.getToken(json, -2);
		
		Assert.assertNotNull(token);
		
		User user = TokenService.getUser(token);
		
		Assert.assertNull(user);
	}

	@Test
	public void test_getExpiredAt(){
		System.out.println("TokenServiceTest.test_getExpiredAt() > enter");

		TokenService.setSecret("123");

		JsonObject json = new JsonObject()
				.put("_id", "id1")
				.put("email", "klaus@quant-ux.de")
				.put("role", User.USER);
		String token = TokenService.getToken(json);

		Assert.assertNotNull(token);

		// ste other secret
		TokenService.setSecret("");

		String expiresAt = TokenService.getExpiresAt(token);
		Assert.assertNotNull(expiresAt);

		expiresAt = TokenService.getExpiresAt("");
		Assert.assertEquals("-", expiresAt);

		expiresAt = TokenService.getExpiresAt("No token");
		Assert.assertEquals("-", expiresAt);

		System.out.println("TokenServiceTest.test_getExpiredAt() > exit "+ expiresAt);

	}

}
