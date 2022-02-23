package com.qux.auth;

import com.auth0.jwk.InvalidPublicKeyException;
import com.auth0.jwk.Jwk;
import com.auth0.jwt.interfaces.RSAKeyProvider;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

public class JWKKeyProvider implements RSAKeyProvider {

    private  final Map<String, Jwk> keys;

    public JWKKeyProvider (Map<String, Jwk> keys) {
        this.keys = keys;
    }

    @Override
    public RSAPublicKey getPublicKeyById(String kid) {
        if (keys.containsKey(kid)) {
            try {
                RSAPublicKey publicKey = (RSAPublicKey)  keys.get(kid).getPublicKey();
                return publicKey;
            } catch (InvalidPublicKeyException e) {
                throw new IllegalStateException("");
            }
        } else {
            throw new IllegalStateException("");
        }
    }

    @Override
    public RSAPrivateKey getPrivateKey() {
        return null;
    }

    @Override
    public String getPrivateKeyId() {
        return null;
    }
}

