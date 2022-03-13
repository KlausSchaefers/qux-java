package com.qux;

import com.qux.model.Model;
import com.qux.model.User;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ExternalUserTest  extends MatcTestCase {

    @Test
    public void testExternal(TestContext context){
        log("testExternal", "enter");

        cleanUp();

        deploy(new MATC(), context);

        long userCount = client.count(this.user_db, Model.all());

        JsonObject user = new JsonObject()
                .put("id", "b099e979-55a0-4e6b-bd1d-e36ca8e54192")
                .put("email", "klaus@external.com")
                .put("name", "Klaus");

        // post once, user gets created
        JsonObject response = post("/rest/user/external", user);
        context.assertNotNull(response);
        context.assertEquals(User.USER, response.getString("role"));
        context.assertEquals(user.getString("id"), response.getString("id"));

        // assert one user created
        context.assertEquals(userCount + 1,client.count(this.user_db, Model.all()));

        // post the user again
        response = post("/rest/user/external", user);
        context.assertNotNull(response);
        context.assertEquals(User.USER, response.getString("role"));
        context.assertEquals(user.getString("id"), response.getString("id"));

        // no additional user created
        context.assertEquals(userCount + 1,client.count(this.user_db, Model.all()));
        context.assertEquals(1l, client.count(this.user_db, User.findById("b099e979-55a0-4e6b-bd1d-e36ca8e54192")));

        log("testExternal", "exit");

    }
}
