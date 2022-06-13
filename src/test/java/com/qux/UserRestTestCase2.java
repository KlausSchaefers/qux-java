package com.qux;

import com.qux.model.User;
import com.qux.util.Config;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class UserRestTestCase2 extends MatcTestCase {


    @Test
    public void testNoSignUp(TestContext context){
        log("testNoSignUp", "enter");

        cleanUp();
        conf.put(Config.USER_ALLOW_SIGNUP, false);
        deploy(new MATC(), context);


        User klaus = createUser("Klaus");
        klaus.setPassword("123456789");

        JsonObject result = post("/rest/user", klaus);
        log("test", "post > " + result);
        context.assertTrue(result.containsKey("errors"));
        context.assertTrue(result.getJsonArray("errors").contains("user.create.nosignup"));

        log("testNoSignUp", "exit");
    }

    @Test
    public void testAllowedDomains(TestContext context){
        log("testAllowedDomains", "enter");

        cleanUp();
        conf.put(Config.USER_ALLOWED_DOMAINS, "my-server.com");
        deploy(new MATC(), context);

        testKlaus(context);
        testMounir(context);
        testOzan(context);

        log("testAllowedDomains", "exit");
    }

    private void testKlaus(TestContext context) {
        User klaus = createUser("Klaus");
        klaus.setPassword("123456789");
        JsonObject result = post("/rest/user", klaus);
        log("test", "post > " + result);
        context.assertTrue(result.containsKey("errors"));
        context.assertTrue(result.getJsonArray("errors").contains("user.create.domain"));
    }

    private void testOzan(TestContext context) {
        User ozan = createUser("Ozan");
        ozan.setPassword("123456789");
        ozan.setEmail("ozan@external.my-server.com");
        JsonObject result3 = post("/rest/user", ozan);
        log("test", "post > " + result3);
        context.assertTrue(!result3.containsKey("errors"));
        context.assertTrue(result3.containsKey("_id"));
        context.assertEquals("ozan@external.my-server.com", result3.getString("email"));
    }

    private void testMounir(TestContext context) {
        User mounir = createUser("Mounir");
        mounir.setPassword("123456789");
        mounir.setEmail("mounir@my-server.com");
        JsonObject result2 = post("/rest/user", mounir);
        log("test", "post > " + result2);
        context.assertTrue(!result2.containsKey("errors"));
        context.assertTrue(result2.containsKey("_id"));
        context.assertEquals("mounir@my-server.com", result2.getString("email"));
    }
}
