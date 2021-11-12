package com.qux;

import com.qux.model.App;
import com.qux.model.Comment;
import com.qux.model.Invitation;
import com.qux.util.Config;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.HashMap;
import java.util.List;
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

        JsonObject mergedConfig = Config.mergeEncIntoConfig(config, env);
        context.assertEquals("https://other.com", mergedConfig.getString(Config.HTTP_HOST) );


        log("testMergeInEnv", "exit");
    }



    @Test
    public void testSetDefaults(TestContext context){
        log("testSetDefaults", "enter");

        JsonObject config = new JsonObject();
        JsonObject defaultConfig = Config.setDefaults(config);
        context.assertEquals("https://quant-ux.com", defaultConfig.getString(Config.HTTP_HOST) );

        log("testSetDefaults", "exit");
    }




}
