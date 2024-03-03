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
        env.put(Config.ENV_MAIL_PORT, "123");
        env.put(Config.ENV_MAIL_SSL, "optional");

        JsonObject mergedConfig = Config.mergeEnvIntoConfig(config, env);
        context.assertEquals("https://other.com", mergedConfig.getString(Config.HTTP_HOST) );
        context.assertEquals(8080, mergedConfig.getInteger(Config.HTTP_PORT));
        context.assertEquals(true, mergedConfig.getBoolean(Config.DEBUG));

        JsonObject mailConfig = Config.getMail(mergedConfig);
        context.assertEquals(true, Config.isMailSSLOptional(mailConfig));
        context.assertEquals(false, Config.isMailSSLDisabled(mailConfig));

        context.assertEquals("my-server.com", mergedConfig.getString(Config.USER_ALLOWED_DOMAINS));
        context.assertEquals(false, mergedConfig.getBoolean(Config.USER_ALLOW_SIGNUP));
        context.assertEquals("my-server.com", Config.getUserAllowedDomains(mergedConfig));


        context.assertEquals(123, mergedConfig.getInteger("mail.port"));
        context.assertEquals(123, Config.getMail(mergedConfig).getInteger("port"));


        Map<String, String> env2 = new HashMap<>();
        env2.put(Config.ENV_USER_ALLOW_SIGNUP, "true");
        env2.put(Config.ENV_MAIL_SSL, "disabled");
        JsonObject mergedConfig2 = Config.mergeEnvIntoConfig(config, env2);
        context.assertEquals(true, Config.getUserSignUpAllowed(mergedConfig2));

        JsonObject mailConfig2 = Config.getMail(mergedConfig2);
        context.assertEquals(true, Config.isMailSSLDisabled(mailConfig2));
        context.assertEquals(false, Config.isMailSSLOptional(mailConfig2));

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

        JsonObject mailConfig = Config.getMail(defaultConfig);
        context.assertEquals(false, Config.isMailSSLOptional(mailConfig));
        context.assertEquals(false, Config.isMailSSLDisabled(mailConfig));

        log("testSetDefaults", "exit");
    }


    @Test
    public void testSSLBug(TestContext context){
        log("testSSLBug", "enter");

        JsonObject config = new JsonObject();

        Map<String, String> env = new HashMap<>();
        env.put(Config.ENV_MAIL_HOST, "mail.example.org");
        env.put(Config.ENV_MAIL_PORT, "587");
        env.put(Config.ENV_MAIL_PASSWORD, "abc");
        env.put(Config.ENV_MAIL_SSL, "required");
        env.put(Config.ENV_MAIL_USER, "mail@example.org");

        JsonObject mergedConfig = Config.mergeEnvIntoConfig(config, env);

        print(mergedConfig);
        JsonObject mailConfig = Config.getMail(mergedConfig);
        context.assertEquals(true, Config.isMailSSLRequired(mailConfig));
        context.assertEquals(false, Config.isMailSSLDisabled(mailConfig));
        context.assertEquals(false, Config.isMailSSLOptional(mailConfig));

        log("testSSLBug", "exit");
    }






}
