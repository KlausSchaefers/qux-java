package com.qux.rest;

import com.qux.auth.ITokenService;
import com.qux.model.AppEvent;
import com.qux.model.User;
import com.qux.util.rest.REST;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenAIProxyRest extends REST {

    private final Logger logger = LoggerFactory.getLogger(OpenAIProxyRest.class);

    private final String targetURL;

    private final int targetPort;

    public OpenAIProxyRest(ITokenService tokenService, String url) {
        super(tokenService);
        logger.info("forward() > enter > URL : " + url);
        this.targetURL = url;
        this.targetPort = 443;
    }



    public void forward(RoutingContext event) {
        logger.info("forward() > enter");

        User user = getUser(event);
        if (!user.hasRole(User.USER)) {
            logger.error("forward() > exit > Error! User " + user.getEmail() + " tried to proxy");
            returnError(event, 401);
            return;
        }

        JsonObject request = event.getBodyAsJson();
        if (!isValidRequest(request)) {
            logger.error("forward() > exit > Error! Wrong data ");
            returnError(event, 400);
            return;
        }

        AppEvent.send(event, user.getEmail(), AppEvent.TYPE_OPEN_AI);


        String openAIModel = request.getString("openAIModel");
        String openAIToken = request.getString("openAIToken");
        JsonObject payload = request.getJsonObject("openAIPayload");

        WebClientOptions options = new WebClientOptions()
                .setTrustAll(true)
                .setConnectTimeout(20000)
                .setVerifyHost(false);

        WebClient client = WebClient.create(event.vertx(), options);
        // make sure the port is 443 for SSL... How came up with this?
        client.post(this.targetPort, this.targetURL, openAIModel)
                .ssl(true)
                .putHeader("Content-Type", "application/json")
                .putHeader("Authorization", "Bearer " + openAIToken)
                .sendJsonObject(payload, res -> {

            if (res.succeeded()) {
                try {

                    JsonObject result = res.result().bodyAsJsonObject();
                    logger.info("forward() > exit > Success!");

                    returnJson(event, result);
                } catch (Exception err) {
                    logger.error("forward() > exit > Success!", err);
                    returnError(event, 400);
                }

            } else {
                logger.error("forward() > exit > Error! Something wrong with open ai", res.cause());

                returnError(event, 401);
            }
        });

    }

    private boolean isValidRequest(JsonObject request) {
        return request.containsKey("openAIToken") &&
                request.containsKey("openAIPayload") &&
                request.containsKey("openAIModel");
    }

}
