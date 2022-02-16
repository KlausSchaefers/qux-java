package com.qux.util;

import io.vertx.core.json.JsonObject;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {

    private static final Logger logger = LoggerFactory.getLogger(Config.class);


    public static final String ENV_HTTP_HOST = "QUX_HTTP_HOST";

    public static final String ENV_HTTP_PORT = "QUX_HTTP_PORT";

    public static final String ENV_MONGO_DB_NAME = "QUX_MONGO_DB_NAME";

    public static final String ENV_MONGO_TABLE_PREFIX = "QUX_MONGO_TABLE_PREFIX";

    public static final String ENV_MONGO_CONNECTION_STRING = "QUX_MONGO_CONNECTION_STRING";

    public static final String ENV_MAIl_USER = "QUX_MAIL_USER";

    public static final String ENV_MAIl_PASSWORD = "QUX_MAIL_PASSWORD";

    public static final String ENV_MAIL_HOST = "QUX_MAIL_HOST";

    public static final String ENV_JWT_PASSWORD = "QUX_JWT_PASSWORD";

    public static final String ENV_IMAGE_FOLDER_USER = "QUX_IMAGE_FOLDER_USER";

    public static final String ENV_IMAGE_FOLDER_APPS = "QUX_IMAGE_FOLDER_APPS";

    public static final String ENV_AUTH_SERVICE = "QUX_AUTH_SERVICE";

    public static final String ENV_KEY_CLOAK_REALM = "QUX_KEYCLOAK_REALM";

    public static final String ENV_KEY_CLOAK_CLIENT_ID = "QUX_KEY_CLOAK_CLIENT_ID";

    public static final String ENV_KEY_CLOAK_CLIENT_SECRET = "QUX_KEYCLOAK_CLIENT_SECRET";

    public static final String ENV_KEY_CLOAK_SERVER = "QUX_KEYCLOAK_SERVER";




    public static final String HTTP_HOST = "http.host";

    public static final String HTTP_PORT = "http.port";

    public static final String MONGO_DB_NAME = "mongo.db_name";

    public static final String MONGO_TABLE_PREFIX = "mongo.table_prefix";

    public static final String MONGO_CONNECTION_STRING = "mongo.connection_string";

    public static final String MAIl_USER = "mail.user";

    public static final String MAIl_PASSWORD = "mail.password";

    public static final String MAIL_HOST = "mail.host";

    public static final String JWT_PASSWORD = "jwt.password";

    public static final String IMAGE_FOLDER_USER = "image.folder.user";

    public static final String IMAGE_FOLDER_APPS = "image.folder.apps";

    public static final String KEY_CLOAK_CLIENT_ID = "auth.keycloak.client_id";

    public static final String KEY_CLOAK_CLIENT_SECRET = "auth.keycloak.client_secret";

    public static final String KEY_CLOAK_CLIENT_SERVER = "auth.keycloak.server";

    public static final String KEY_CLOAK_CLIENT_REALM = "auth.keycloak.realm";

    public static final String AUTH_SERVICE = "auth.service";


    public static boolean isKeyCloak (JsonObject config) {
        return false;
    }

    public static boolean isFileSystem(JsonObject config) {
        return true;
    }

    public static JsonObject getMail(JsonObject config) {
        JsonObject mailConfig = config.getJsonObject("mail");
        if (mailConfig == null) {
            mailConfig = new JsonObject()
                    .put("user", config.getString(MAIl_USER))
                    .put("password", config.getString(MAIl_PASSWORD))
                    .put("host", config.getString(MAIL_HOST));
        }
        return mailConfig;
    }

    public static String getHttpHost(JsonObject config) {
        return config.getString(HTTP_HOST);
    }

    public static JsonObject getMongo(JsonObject config) {
        JsonObject mongoConfig = config.getJsonObject("mongo");
        if (mongoConfig == null) {
            mongoConfig = new JsonObject()
                    .put("connection_string", config.getString(Config.MONGO_CONNECTION_STRING))
                    .put("db_name", config.getString(Config.MONGO_DB_NAME));
        }

        return mongoConfig;
    }

    public static JsonObject setDefaults(JsonObject config){
        JsonObject result = config.copy();
        if (!result.containsKey(HTTP_HOST)) {
            result.put(HTTP_HOST, "https://quant-ux.com");
        }
        return result;
    }

    public static JsonObject mergeEnvIntoConfig(JsonObject config){
        return mergeEnvIntoConfig(config, System.getenv());
    }



    public static JsonObject mergeEnvIntoConfig(JsonObject config, Map<String, String> env) {
        JsonObject result = config.copy();
        mergeAuth(env, result);
        mergeKeyCloak(env, result);
        mergeMail(env, result);
        mergeHTTP(env, result);
        mergeMongo(env, result);
        mergeImage(env, result);
        return result;
    }

    private static void mergeImage(Map<String, String> env, JsonObject result) {
        if (env.containsKey(ENV_IMAGE_FOLDER_USER)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_IMAGE_FOLDER_USER);
            result.put(IMAGE_FOLDER_USER, env.get(ENV_IMAGE_FOLDER_USER));
        }
        if (env.containsKey(ENV_IMAGE_FOLDER_APPS)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_IMAGE_FOLDER_APPS);
            result.put(IMAGE_FOLDER_APPS, env.get(ENV_IMAGE_FOLDER_APPS));
        }
    }

    private static void mergeAuth(Map<String, String> env, JsonObject result) {
        if (env.containsKey(ENV_AUTH_SERVICE)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_AUTH_SERVICE);
            result.put(AUTH_SERVICE, env.get(ENV_AUTH_SERVICE));
        }

        if (env.containsKey(ENV_JWT_PASSWORD)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_JWT_PASSWORD);
            result.put(JWT_PASSWORD, env.get(ENV_JWT_PASSWORD));
        }
    }

    private static void mergeMongo(Map<String, String> env, JsonObject result) {
        if (env.containsKey(ENV_MONGO_CONNECTION_STRING)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_MONGO_CONNECTION_STRING);
            result.put(MONGO_CONNECTION_STRING, env.get(ENV_MONGO_CONNECTION_STRING));
        }
        if (env.containsKey(ENV_MONGO_DB_NAME)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_MONGO_DB_NAME);
            result.put(MONGO_DB_NAME, env.get(ENV_MONGO_DB_NAME));
        }
        if (env.containsKey(ENV_MONGO_TABLE_PREFIX)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_MONGO_TABLE_PREFIX);
            result.put(MONGO_TABLE_PREFIX, env.get(ENV_MONGO_TABLE_PREFIX));
        }
    }

    private static void mergeHTTP(Map<String, String> env, JsonObject result) {
        if (env.containsKey(ENV_HTTP_HOST)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_HTTP_HOST);
            result.put(HTTP_HOST, env.get(ENV_HTTP_HOST));
        }
        if (env.containsKey(ENV_HTTP_PORT)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_HTTP_PORT);
            result.put(HTTP_PORT, env.get(ENV_HTTP_PORT));
        }
    }

    private static void mergeMail(Map<String, String> env, JsonObject result) {
        if (env.containsKey(ENV_MAIL_HOST)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_MAIL_HOST);
            result.put(MAIL_HOST, env.get(ENV_MAIL_HOST));
        }
        if (env.containsKey(ENV_MAIl_USER)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_MAIl_USER);
            result.put(MAIl_USER, env.get(ENV_MAIl_USER));
        }
        if (env.containsKey(ENV_MAIl_PASSWORD)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_MAIl_PASSWORD);
            result.put(MAIl_PASSWORD, env.get(ENV_MAIl_PASSWORD));
        }
    }

    private static void mergeKeyCloak(Map<String, String> env, JsonObject result) {
        if (env.containsKey(ENV_KEY_CLOAK_CLIENT_ID)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_KEY_CLOAK_CLIENT_ID);
            result.put(KEY_CLOAK_CLIENT_ID, env.get(ENV_KEY_CLOAK_CLIENT_ID));
        }

        if (env.containsKey(ENV_KEY_CLOAK_CLIENT_SECRET)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_KEY_CLOAK_CLIENT_SECRET);
            result.put(KEY_CLOAK_CLIENT_SECRET, env.get(ENV_KEY_CLOAK_CLIENT_SECRET));
        }

        if (env.containsKey(ENV_KEY_CLOAK_SERVER)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_KEY_CLOAK_SERVER);
            result.put(KEY_CLOAK_CLIENT_SERVER, env.get(ENV_KEY_CLOAK_SERVER));
        }

        if (env.containsKey(ENV_KEY_CLOAK_REALM)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_KEY_CLOAK_REALM);
            result.put(KEY_CLOAK_CLIENT_REALM, env.get(ENV_KEY_CLOAK_REALM));
        }
    }


}
