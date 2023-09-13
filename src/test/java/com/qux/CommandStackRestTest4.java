package com.qux;


import com.qux.model.App;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class CommandStackRestTest4 extends MatcTestCase{

    @Test
    public void test_shift(TestContext context){
        log("test_shift", "enter");

        cleanUp();

        deploy(new MATC(), context);

        postUser("klaus", context);

        assertLogin(context, "klaus@quant-ux.de", "123456789");
        App app = postApp("klaus_app_public", true, context);


        log("test_Command", "Created App > "+ app.getId());
        getStack(app, context);


        for(int i=1; i< 300; i++){
            JsonObject command = new JsonObject().put("type", "AddWidget").put("new", new JsonObject().put("x",10)).put("id", i);
            postCommand(app, command, i, i, context);
        }

        JsonObject stack = getStack(app, context);
        context.assertEquals(299, stack.getInteger("pos"));
        context.assertEquals(299, stack.getJsonArray("stack").size());
        //assertStackOrder(stack,0, 1, context);

        shiftStack(app, 100, 199, 199, context);

        stack = getStack(app, context);
        context.assertEquals(199, stack.getInteger("pos"));
        context.assertEquals(199, stack.getJsonArray("stack").size());
        assertStackOrder(stack,100,1, context);


        log("test_shift", "exit");
    }


    @Test
    public void test_shift2(TestContext context){
        log("test_shift2", "enter");

        cleanUp();

        deploy(new MATC(), context);

        postUser("klaus", context);

        assertLogin(context, "klaus@quant-ux.de", "123456789");
        App app = postApp("klaus_app_public", true, context);


        log("test_Command", "Created App > "+ app.getId());
        getStack(app, context);


        for(int i=1; i< 300; i++){
            JsonObject command = new JsonObject().put("type", "AddWidget").put("new", new JsonObject().put("x",10)).put("id", i);
            postCommand(app, command, i, i, context);
        }

        postUndo(app, 298, 299, context);
        postUndo(app, 297, 299, context);
        postUndo(app, 296, 299, context);
        postUndo(app, 295, 299, context);
        postUndo(app, 294, 299, context);


        JsonObject stack = getStack(app, context);
        context.assertEquals(294, stack.getInteger("pos"));
        context.assertEquals(299, stack.getJsonArray("stack").size());
        assertStackOrder(stack, 0, 1, context);

        shiftStack(app, 100, 194, 199, context);

        stack = getStack(app, context);
        context.assertEquals(199, stack.getJsonArray("stack").size());
        context.assertEquals(194, stack.getInteger("pos"));
        assertStackOrder(stack, 100, 1, context);


        log("test_shift2", "exit");
    }


    public JsonObject getStack(App app, TestContext context){
        JsonObject stack = get("/rest/commands/" + app.getId() + ".json");
        log("assertStack", "get(stack) : " + stack);
        context.assertTrue(!stack.containsKey("error"));
        context.assertTrue(!stack.containsKey("errors"));
        context.assertEquals(stack.getString("appID"), app.getId());
        return stack;
    }

    public void postUndo(App app, int expectedPos, int extpectedLength, TestContext context){
        log("postUndo", "enter");
        JsonObject result = post("/rest/commands/" +app.getId()+"/undo", new JsonObject().put("as","as"));
        context.assertTrue(!result.containsKey("errors"));
        context.assertEquals(expectedPos, result.getInteger("pos"));

        JsonObject stack = get("/rest/commands/" + app.getId() + ".json");
        log("postRedo", "stack > "+  stack.encode());
        context.assertEquals(expectedPos, stack.getInteger("pos"));
        context.assertEquals(extpectedLength, stack.getJsonArray("stack").size());

    }

    public JsonObject shiftStack(App app, int count, int expectedPos, int extpectedLength, TestContext context){


        JsonObject result = delete("/rest/commands/" +app.getId()+"/shift/" + count);

        log("removeCommand", "result > " + result.encode());
        context.assertTrue(!result.containsKey("errors"));
        context.assertTrue(result.containsKey("stack"));
        context.assertEquals(expectedPos, result.getInteger("pos"));
        context.assertEquals(extpectedLength, result.getJsonArray("stack").size());


        JsonObject stack = get("/rest/commands/" + app.getId() + ".json");
        log("removeCommand", "stack > "+  stack.encodePrettily());
        context.assertEquals(expectedPos, stack.getInteger("pos"));
        context.assertEquals(extpectedLength, stack.getJsonArray("stack").size(), "Expectd stack length");


        return result;
    }
}
