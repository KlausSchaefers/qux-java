package com.qux;

import com.qux.auth.JWKKeyProvider;
import com.qux.auth.KeyCloakTokenService;
import com.qux.auth.QUXTokenService;
import com.qux.model.User;
import com.qux.util.KeyCloakConfig;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.CountDownLatch;

@RunWith(VertxUnitRunner.class)
public class KeyCloakServiceTest extends MatcTestCase{

    @Test
    public void testGetKeys(TestContext context) {
        System.out.println("KeyCloakServiceTest.testGetKeys() > enter");

        cleanUp();
        KeyCloakConfig conf = new KeyCloakConfig();
        conf.setServer("http://localhost:8080");
        conf.setRealm("qux");

        KeyCloakTokenService ks = new KeyCloakTokenService(conf);
        context.assertEquals(2, ks.getKeys().size());

        JWKKeyProvider keyProvider = ks.getKeyProvider();
        RSAPublicKey publicKey = keyProvider.getPublicKeyById(ks.getKeys().get(0).getId());
        context.assertNotNull(publicKey);

        System.out.println("KeyCloakServiceTest.testGetKeys() > exit");
    }

    @Test
    public void testKeyProvider(TestContext context) throws InterruptedException {
        System.out.println("KeyCloakServiceTest.testKeyProvider() > enter");
        cleanUp();


        System.out.println("KeyCloakServiceTest.testKeyProvider() > exit");
    }



}
