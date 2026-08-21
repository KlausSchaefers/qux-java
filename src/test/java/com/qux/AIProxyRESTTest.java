package com.qux;

import com.google.common.io.CharStreams;

import com.qux.model.User;
import com.qux.rest.AIProxyREST;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@RunWith(VertxUnitRunner.class)
public class AIProxyRESTTest extends MatcTestCase {

    private static final int ECHO_PORT = 8899;

    private static final String AI_TOKEN = "test-ai-token";

    @Test
    public void test_AIProxy(TestContext context) throws Exception {
        log("test_AIProxy", "enter");

        cleanUp();

        deploy(new MATC(), context);

        String statusURL = getServerURL() + "/rest/status.json";

        /**
         * 1) Anonymous / not logged in callers must not be able to use the
         * AI proxy at all, even for an otherwise valid target URL.
         */
        AIProxyResponse guest = callAIProxy(statusURL);
        context.assertEquals(401, guest.status);

        /**
         * Now log in
         */
        User klaus = postUser("klaus", context);
        assertLogin(context, "klaus@quant-ux.de", "123456789");

        /**
         * 2) A missing x-qux-url header must give 400
         */
        AIProxyResponse noUrl = callAIProxy(null);
        context.assertEquals(400, noUrl.status);

        /**
         * A URL that is not on the allow list must give 403
         */
        AIProxyResponse notAllowed = callAIProxy("https://example.com/some/path");
        context.assertEquals(403, notAllowed.status);

        /**
         * 3) Valid request: the proxy must forward to /rest/status.json and
         * return it, and must bump the usage counters.
         */
        AIProxyResponse ok = callAIProxy(statusURL);
        context.assertEquals(200, ok.status);
        JsonObject status = new JsonObject(ok.body);
        context.assertEquals(MATC.VERSION, status.getString("version"));

        JsonObject mongoUser = client.findOne(user_db, User.findById(klaus.getId()));
        context.assertEquals(1, mongoUser.getInteger(User.FIELD_AI_USAGE));
        context.assertEquals(1, mongoUser.getInteger(User.FIELD_AI_USAGE_TOTAL));

        /**
         * 4) The server side AI token must be injected as Bearer token,
         * the caller's own Authorization (JWT) header must never be
         * forwarded to the target.
         */
        startEchoServer(context);
        String echoURL = "http://localhost:" + ECHO_PORT + "/echo";

        AIProxyResponse echo = callAIProxy(echoURL);
        context.assertEquals(200, echo.status);
        JsonObject headers = new JsonObject(echo.body);
        context.assertEquals("Bearer " + AI_TOKEN, headers.getString("authorization"));

        mongoUser = client.findOne(user_db, User.findById(klaus.getId()));
        context.assertEquals(2, mongoUser.getInteger(User.FIELD_AI_USAGE));
        context.assertEquals(2, mongoUser.getInteger(User.FIELD_AI_USAGE_TOTAL));


        log("test_AIProxy", "exit");
    }

    private void startEchoServer(TestContext context) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        vertx.createHttpServer().requestHandler(req -> {
            JsonObject headers = new JsonObject();
            for (Map.Entry<String, String> header : req.headers().entries()) {
                headers.put(header.getKey().toLowerCase(), header.getValue());
            }
            req.response()
                    .putHeader("Content-Type", "application/json")
                    .end(headers.encode());
        }).listen(ECHO_PORT, res -> {
            if (res.failed()) {
                context.fail("Could not start echo server");
            }
            latch.countDown();
        });
        latch.await();
    }

    private AIProxyResponse callAIProxy(String targetURL) throws IOException {
        HttpGet get = new HttpGet(getServerURL() + "/rest/ai-proxy");
        if (getJWT() != null) {
            get.addHeader("Authorization", "Bearer " + getJWT());
        }
        if (targetURL != null) {
            get.addHeader(AIProxyREST.HEADER_URL, targetURL);
        }

        CloseableHttpResponse resp = httpClient.execute(get);
        AIProxyResponse result = new AIProxyResponse();
        result.status = resp.getStatusLine().getStatusCode();
        if (resp.getEntity() != null) {
            result.body = CharStreams.toString(new InputStreamReader(resp.getEntity().getContent()));
        }
        resp.close();
        return result;
    }

    private String getServerURL() {
        return "http://localhost:" + conf.getInteger("http.port");
    }

    private static class AIProxyResponse {
        int status;
        String body;
    }
}
