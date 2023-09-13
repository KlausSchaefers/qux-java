package com.qux.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.qux.model.User;
import com.qux.util.KeyCloakConfig;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class KeyCloakTokenService implements ITokenService{

    private static Logger logger = LoggerFactory.getLogger(KeyCloakTokenService.class);

    private Map<String, Jwk> keys;

    private JWKKeyProvider keyProvider;

    private final KeyCloakConfig config;

    public KeyCloakTokenService (KeyCloakConfig config) {
        this.config = config;
        this.loadKeys(this.config.getServer(), this.config.getRealm());
    }


    public void loadKeys(String keycloakServer, String keycloakRealm) {
        logger.info("getKeys() > enter()");
        try {
            String url = String.format("%s/realms/%s/protocol/openid-connect/certs", keycloakServer, keycloakRealm);
            UrlJwkProvider provider = new UrlJwkProvider(new URL(url), 5000, 5000);
            logger.info("Load keys from " + url);
            List<Jwk> all = provider.getAll();
            this.setKeys(all);
        } catch (Exception e) {
            logger.error("loadKeys() > Cannot get keys ()", e);
            throw new RuntimeException();
        }
    }

    public List<Jwk> getKeys () {
        return new ArrayList<>(this.keys.values());
    }

    public JWKKeyProvider getKeyProvider () {
        return this.keyProvider;
    }


    public void setKeys (List<Jwk> all){
        this.keys = new HashMap<>();
        for (Jwk key : all) {
            this.keys.put(key.getId(), key);
        }
        this.keyProvider = new JWKKeyProvider(this.keys);
        logger.info("setKeys() > exit");
    }

    @Override
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


    public User getUser(String token) {

        if (this.keyProvider == null) {
            logger.error("getUser() > No secret");
            throw new IllegalStateException("No key provider");
        }

        try {

            Algorithm algorithm = Algorithm.RSA256(keyProvider);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(this.config.getIssuer())
                    .build();
            DecodedJWT jwt = verifier.verify(token);

            String id = jwt.getSubject();
            String email = this.config.getClaimEmail() != "" ?  jwt.getClaim(this.config.getClaimEmail()).asString() : jwt.getSubject();
            String role = User.USER;
            String name = this.config.getClaimName() != "" ?  jwt.getClaim(this.config.getClaimName()).asString() : "KeyCloakUser";
            String lastname = this.config.getClaimLastName() != "" ?  jwt.getClaim(this.config.getClaimLastName()).asString() : "KeyCloakUser";

            User user = new User(id, name, lastname, email, role);
            return user;

        } catch (JWTVerificationException e){
            logger.error("getToken() > Some  while parsing the token: " + token);
        }


        return null;
    }

    @Override
    public String getToken(JsonObject user) {
        return null;
    }
}
