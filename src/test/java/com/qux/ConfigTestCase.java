package com.qux;

import com.qux.util.Config;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ConfigTestCase extends MatcTestCase {


    @Test
    public void testMergeInEnv(TestContext context){
        log("testMergeInEnv", "enter");

        JsonObject config = new JsonObject().put("http.host", "XXX");

        Map<String, String> env = new HashMap<>();
        env.put(Config.ENV_HTTP_HOST, "https://other.com");
        env.put(Config.ENV_HTTP_PORT, "8080");
        env.put(Config.ENV_USER_ALLOW_SIGNUP, "false");
        env.put(Config.ENV_USER_ALLOWED_DOMAINS, "my-server.com");
        env.put(Config.ENV_DEBUG, "true");

        JsonObject mergedConfig = Config.mergeEnvIntoConfig(config, env);
        context.assertEquals("https://other.com", mergedConfig.getString(Config.HTTP_HOST) );
        context.assertEquals(8080, mergedConfig.getInteger(Config.HTTP_PORT));
        context.assertEquals(true, mergedConfig.getBoolean(Config.DEBUG));


        context.assertEquals("my-server.com", mergedConfig.getString(Config.USER_ALLOWED_DOMAINS));
        context.assertEquals(false, mergedConfig.getBoolean(Config.USER_ALLOW_SIGNUP));
        context.assertEquals("my-server.com", Config.getUserAllowedDomains(mergedConfig));
        context.assertEquals(false, Config.getUserSignUpAllowed(mergedConfig));



        Map<String, String> env2 = new HashMap<>();
        env2.put(Config.ENV_USER_ALLOW_SIGNUP, "true");
        JsonObject mergedConfig2 = Config.mergeEnvIntoConfig(config, env2);
        context.assertEquals(true, Config.getUserSignUpAllowed(mergedConfig2));


        Map<String, String> env3 = new HashMap<>();
        env3.put(Config.ENV_USER_ALLOW_SIGNUP, "asdasd");
        JsonObject mergedConfig3 = Config.mergeEnvIntoConfig(config, env3);
        context.assertEquals(true, Config.getUserSignUpAllowed(mergedConfig3));


        log("testMergeInEnv", "exit");
    }



    @Test
    public void testSetDefaults(TestContext context){
        log("testSetDefaults", "enter");

        JsonObject config = new JsonObject();
        JsonObject defaultConfig = Config.setDefaults(config);
        context.assertEquals("https://quant-ux.com", defaultConfig.getString(Config.HTTP_HOST) );

        context.assertEquals("*", defaultConfig.getString(Config.USER_ALLOWED_DOMAINS));
        context.assertEquals(true, defaultConfig.getBoolean(Config.USER_ALLOW_SIGNUP));
        context.assertEquals("*", Config.getUserAllowedDomains(defaultConfig));
        context.assertEquals(true, Config.getUserSignUpAllowed(defaultConfig));

        log("testSetDefaults", "exit");
    }




}
