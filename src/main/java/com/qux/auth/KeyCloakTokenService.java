package com.qux.auth;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.RSAKeyProvider;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

// get JWTKS files like
// https://vertx.io/blog/jwt-authorization-for-vert-x-with-keycloak/
// then use java-jwt to setup keysotre
// https://github.com/auth0/java-jwt
//https://github.com/auth0/jwks-rsa-java
public class KeyCloakTokenService {

    public void init() {
        final RSAPrivateKey privateKey = null;//Get the key instance
        final String privateKeyId = null; //Create an Id for the above key

        RSAKeyProvider keyProvider = new RSAKeyProvider() {
            @Override
            public RSAPublicKey getPublicKeyById(String kid) {
                //Received 'kid' value might be null if it wasn't defined in the Token's header
                RSAPublicKey publicKey = null;
                return (RSAPublicKey) publicKey;
            }

            @Override
            public RSAPrivateKey getPrivateKey() {
                return null;
            }

            @Override
            public String getPrivateKeyId() {
                return null;
            }
        };

        Algorithm algorithm = Algorithm.RSA256(keyProvider);

    }
}
