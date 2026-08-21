package com.qux.rest;


import com.qux.auth.ITokenService;
import com.qux.model.User;
import com.qux.util.rest.MongoREST;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.net.URI;
import java.util.*;

/**
 * Proxy for AI requests. Unlike {@link ProxyREST}, which forwards on
 * behalf of an invitation hash and per-app secrets, this proxy is used
 * directly by logged in users and is authorized against a shared server
 * side token, with a per user usage quota.
 */
public class AIProxyREST extends MongoREST {

    public static final String HEADER_URL = "x-qux-url";

    public static final String HEADER_AUTHORIZATION = "Authorization";

    public static final String BEARER_PREFIX = "Bearer ";


    private static final Set<String> EXCLUDED_HEADERS = new HashSet<>(Arrays.asList(
            "host", "connection", "content-length", "transfer-encoding", "accept-encoding",
            "upgrade", "keep-alive", "origin", "referer", "cookie",
            HEADER_URL, "authorization"
    ));

    private static final Set<String> EXCLUDED_RESPONSE_HEADERS = new HashSet<>(Arrays.asList(
            "connection", "keep-alive", "transfer-encoding", "content-length"
    ));

    private final String token;

    private final List<String> allowedUrls;

    private final HttpClient client;

    public AIProxyREST(ITokenService tokenService, Vertx vertx, MongoClient db, String token, String allowedUrls) {
        super(tokenService, db, User.class);
        this.token = token;
        this.allowedUrls = parseAllowedUrls(allowedUrls);

        /*
         * No per request timeout, so streaming (e.g. SSE) responses are not cut off.
         * The idle timeout closes connections without any traffic.
         */
        HttpClientOptions options = new HttpClientOptions()
                .setConnectTimeout(20000)
                .setIdleTimeout(120);
        this.client = vertx.createHttpClient(options);
    }

    private static List<String> parseAllowedUrls(String csv) {
        List<String> result = new ArrayList<>();
        if (csv != null) {
            for (String entry : csv.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    /**
     * The target URL is allowed if it starts with one of the configured
     * allowed URLs, e.g. "https://api.openai.com" allows any path below it.
     */
    private boolean isUrlAllowed(URI target) {
        String url = target.toString();
        for (String allowed : allowedUrls) {
            if (url.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }

    public void proxy(RoutingContext event) {
        logger.info("proxy() > enter > " + event.request().method() + " " + event.request().path());

        User user = getUser(event);
        if (!user.hasRole(User.USER)) {
            error("proxy", "User " + user + " tried to use the AI proxy", event);
            returnError(event, 401);
            return;
        }

        URI target = getTargetURI(event);
        if (target == null) {
            error("proxy", "No valid " + HEADER_URL + " header", event);
            returnError(event, 400);
            return;
        }

        if (!isUrlAllowed(target)) {
            error("proxy", "User " + user + " tried to proxy to not allowed URL " + target, event);
            returnError(event, 403);
            return;
        }

        mongo.findOne(table, User.findById(user.getId()), null, res -> {
            if (res.succeeded() && res.result() != null) {
                checkUsageAndForward(event, res.result(), target);
            } else {
                error("proxy", "Could not load user " + user.getId(), event);
                returnError(event, 404);
            }
        });
    }

    private void checkUsageAndForward(RoutingContext event, JsonObject user, URI target) {

        int usage = user.getInteger(User.FIELD_AI_USAGE, 0);
        if (usage >= User.AI_USAGE_LIMIT) {
            error("checkUsageAndForward", "User " + user.getString("_id") + " has no AI usage left", event);
            returnNoTokenLeft(event);
            return;
        }

        JsonObject inc = new JsonObject()
                .put(User.FIELD_AI_USAGE, 1)
                .put(User.FIELD_AI_USAGE_TOTAL, 1);
        JsonObject update = new JsonObject().put("$inc", inc);

        mongo.updateCollection(table, User.findById(user.getString("_id")), update, res -> {
            if (!res.succeeded()) {
                logger.error("checkUsageAndForward() > Could not update AI usage", res.cause());
                returnError(event, 500);
                return;
            }
            forward(event, target);
        });


    }

    private void forward(RoutingContext event, URI target) {

        HttpClientRequest request = client.requestAbs(event.request().method(), target.toString());

        request.handler(response -> returnStream(event, response));

        request.exceptionHandler(err -> {
            logger.error("forward() > Could not forward to target", err);
            if (!event.response().headWritten() && !event.response().ended()) {
                returnError(event, 502);
            } else if (!event.response().ended()) {
                event.response().close();
            }
        });

        for (Map.Entry<String, String> header : event.request().headers().entries()) {
            if (!EXCLUDED_HEADERS.contains(header.getKey().toLowerCase())) {
                request.putHeader(header.getKey(), header.getValue());
            }
        }

        /*
         * Callers never provide their own credentials for the target. We
         * always inject the shared server side token instead.
         */
        request.putHeader(HEADER_AUTHORIZATION, BEARER_PREFIX + this.token);

        /*
         * If the caller disconnects, close the upstream connection, so
         * we do not keep streaming into the void.
         */
        event.response().closeHandler(v -> {
            HttpConnection connection = request.connection();
            if (connection != null) {
                connection.close();
            }
        });

        Buffer body = event.getBody();
        if (body != null && body.length() > 0) {
            request.end(body);
        } else {
            request.end();
        }
    }

    /**
     * Pump the target response directly into the caller response, so
     * chunked and SSE streams arrive incrementally.
     */
    private void returnStream(RoutingContext event, HttpClientResponse response) {

        HttpServerResponse out = event.response();
        out.setStatusCode(response.statusCode());

        for (Map.Entry<String, String> header : response.headers().entries()) {
            if (!EXCLUDED_RESPONSE_HEADERS.contains(header.getKey().toLowerCase())) {
                out.putHeader(header.getKey(), header.getValue());
            }
        }
        out.setChunked(true);

        Pump.pump(response, out).start();

        response.endHandler(v -> {
            if (!out.ended()) {
                out.end();
            }
        });
        response.exceptionHandler(err -> {
            logger.error("returnStream() > Stream from target failed", err);
            if (!out.ended()) {
                out.close();
            }
        });
    }

    private void returnNoTokenLeft(RoutingContext event) {
        JsonObject result = new JsonObject().put("type", "NoTokenLeft");
        event.response().setStatusCode(403);
        event.response().putHeader("content-type", "application/json");
        event.response().end(result.encodePrettily());
    }

    private URI getTargetURI(RoutingContext event) {
        String url = event.request().getHeader(HEADER_URL);
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null) {
                return null;
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

}
