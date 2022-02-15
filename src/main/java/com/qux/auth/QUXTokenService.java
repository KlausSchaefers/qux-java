package com.qux.auth;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import com.qux.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class QUXTokenService implements ITokenService{
	
	private static Logger logger = LoggerFactory.getLogger(QUXTokenService.class);

	private static final String ISSUER = "MATC";

	private static final String CLAIM_ID = "id";

	private static final String CLAIM_EMAIL = "email";

	private static final String CLAIM_NAME = "name";

	private static final String CLAIM_LASTNAME = "lastname";

	private static final String CLAIM_ROLE = "role";

	private String secret = null;
	
	private int daysToExpire = 7;
	

			
	public void setSecret(String secret) {
		this.secret = secret;
	}
	
	public String getToken (JsonObject user) {
		return getToken(user, daysToExpire);
	}
	
	public String getToken (JsonObject user, int days) {
		
		if (secret == null) {
			logger.error("getToken() > No secret");
			throw new IllegalStateException("TokenService needs secret!");
		}
		
		try {
			Date ttl = getTTL(days);
			
		    Algorithm algorithm = Algorithm.HMAC256(this.secret);
		    String token = JWT.create()
		        .withIssuer(ISSUER)
		        .withClaim(CLAIM_ID, user.getString("_id"))
		        .withClaim(CLAIM_EMAIL, user.getString("email"))
		        .withClaim(CLAIM_NAME, user.getString("name"))
		        .withClaim(CLAIM_LASTNAME, user.getString("lastname"))
		        .withClaim(CLAIM_ROLE, user.getString("role"))
		        .withExpiresAt(ttl)
		        .sign(algorithm);
		    return token;
		} catch (JWTCreationException e){
			logger.error("getToken() > Some exception", e);
		}
		return "SomeError";
	}

	public Date getTTL(int days) {
		LocalDateTime plus7Days = LocalDateTime.now().plusDays(days);
		Date ttl = Date.from(plus7Days.atZone(ZoneId.systemDefault()).toInstant());
		return ttl;
	}

	public String getExpiresAt(RoutingContext event) {
		String token = event.request().getHeader("Authorization");
		if (token != null && token.length() > 10) {
			token = token.substring(7);
			return getExpiresAt(token);
		}
		String queryToken = event.request().getParam("token");
		if (queryToken != null && !queryToken.isEmpty()) {
			return getExpiresAt(queryToken);
		}
		return "-";
	}


	public String getExpiresAt (String token) {
		if (token != null) {
			try {
				DecodedJWT jwt = JWT.decode(token);
				return jwt.getExpiresAt().toString();
			} catch (Exception e) {
				logger.error("getExpiresAt() > Some  while parsing the token: " + token);
			}
		}
		return "-";
	}

	public User getUser(String token) {

		if (secret == null) {
			logger.error("getUser() > No secret");
			throw new IllegalStateException("TokenService needs secret!");
		}
		
		try {
		    Algorithm algorithm = Algorithm.HMAC256(secret);
		    JWTVerifier verifier = JWT.require(algorithm)
		        .withIssuer(ISSUER)
		        .build(); //Reusable verifier instance
		    DecodedJWT jwt = verifier.verify(token);
		    
		    String email = jwt.getClaim(CLAIM_EMAIL).asString();
		    String id = jwt.getClaim(CLAIM_ID).asString();
		    String role = jwt.getClaim(CLAIM_ROLE).asString();
		    String name = jwt.getClaim(CLAIM_NAME).asString();
		    String lastname = jwt.getClaim(CLAIM_LASTNAME).asString();
		    
		    User user = new User(id, name, lastname, email, role);
		    
		    return user;
		    
		} catch (JWTVerificationException e){
			logger.error("getToken() > Some  while parsing the token: " + token);
		}
		
		return null;
	}

	public User getUser(RoutingContext event) {
		String token = event.request().getHeader("Authorization");
		if (token != null && token.length() > 10) {
			token = token.substring(7);
			return getUser(token);
		}
		String queryToken = event.request().getParam("token");
		if (queryToken != null && !queryToken.isEmpty()) {
			return getUser(queryToken);
		}
		return null;
	}

}
