package com.qux;

import com.qux.model.App;
import com.qux.model.Team;
import com.qux.model.User;
import com.qux.util.DebugMailClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ACLErrorLoggingTest extends MatcTestCase {


    @Test
    public void test_error_mail(TestContext context){
        log("test_error_mail", "enter");

        cleanUp();
        DebugMailClient.getMails().clear();

        deploy(new MATC(), context);


        /**
         * create user
         */
        User klaus = postUser("klaus", context);
        log("test_SingleUser", klaus.toString());
        assertLogin(context, "klaus@quant-ux.de", "123456789");

        /**
         * create an app now
         */
        App klaus_private_app = postApp("klaus_private_app", false, context);
        log("test_error_mail", "klaus_private_app > "+ klaus_private_app);

        /**
         * Make sure one ACL is there
         */
        context.assertEquals(1, client.find(team_db, Team.findByUser(klaus)).size());


        /**
         * Load app
         */
        JsonObject loadedApp = getApp(klaus_private_app, context);
        context.assertEquals("klaus_private_app", loadedApp.getString("name"));

        /**
         * Load some wrong URl
         */
        JsonObject error = get("/rest/apps/wrongid.json");
        context.assertTrue(error.containsKey("error"));


        logout();



        JsonObject appError = getAppError(klaus_private_app, context);
        context.assertNotNull(appError);
        log(0, "test_error_mail", "Error: " + appError.toString() );

        /**
         * Assert 3 mails was send. One for signup, two for error. Wait also for the bus to send them
         */
        sleep(1000);
        log("test_error_mail", "# Mails:" + DebugMailClient.getMails().size());
        context.assertEquals(3, DebugMailClient.getMails().size());

        /**
         * Second mail should have ACL printed
         */
        MailMessage mailMessage2 = DebugMailClient.getMails().get(1);
        String textMail2 = mailMessage2.getText();
        context.assertTrue(textMail2.contains(klaus_private_app.getId()+ ":3"));


        /**
         * Third mail should have ACL printed
         */
        MailMessage mailMessage3 = DebugMailClient.getMails().get(2);
        String textMail3 = mailMessage3.getText();
        context.assertTrue(textMail3.contains("[] - User is guest"));


        log("test_error_mail", "exit");
    }


    // @Test
    public void test_image_error(TestContext context) {
        log("test_image_error", "enter");

        context.fail("Implement The test");

        cleanUp();

        deploy(new MATC(), context);

        log("test_image_error", "exit");
    }
}
