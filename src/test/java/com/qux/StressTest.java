package com.qux;

import com.qux.model.App;
import com.qux.model.User;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(VertxUnitRunner.class)
public class StressTest extends MatcTestCase {

    /**
     * Test here that stupid % are correctly escaped, so
     * we have a fix for these kind of issues
     * @param context
     * @throws IOException
     */
    @Test
    public void test10k(TestContext context) throws IOException {
        log("test10k", "enter");
        cleanUp();

        deploy(new MATC(), context);

        User klaus = postUser("klaus", context);
        assertLogin(context, klaus, "123456789");

        App app = postApp("klaus_app_public", true, context);

        JsonObject rawApp = new JsonObject()
                .put("name", "Klaus")
                .put("description", "lalal")
                .put("screens", new JsonObject())
                .put("widgets", new JsonObject())
                .put("lastUUID", 0);
        updateApp(app, rawApp, context);
        assertUserList(1, context);
        for (int i = 0; i < 10000; i++) {
            this.stressStep(app, i, context);
        }
        log("test10k", "enter");
    }

    private void stressStep(App app ,int i,TestContext context) {
        JsonArray changes = new JsonArray();
        changes.add(createChange("update", "lastUUID", i));
        changes.add(createChange("add", "grid", new JsonObject().put("x", 10).put("y", 10)));
        changes.add(createChange("add", "s1", new JsonObject().put("x", 10).put("y", 10).put("id", "s1"), "screens"));
        changes.add(createChange("add", "w1", new JsonObject().put("x", i).put("y", 1).put("id","w1"), "widgets"));
        JsonObject updateApp = postChanges(app, changes, context);


        assertJsonPath(updateApp, "lastUUID", i, context);
        assertJsonPath(updateApp, "widgets.w1", context);
        assertJsonPath(updateApp, "widgets.w1.x",i, context);
    }
}
