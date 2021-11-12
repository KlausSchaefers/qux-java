package com.qux.util;

import com.qux.MATC;
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

    public static JsonObject mergeEncIntoConfig(JsonObject config){
        return mergeEncIntoConfig(config, System.getenv());
    }

    public static JsonObject mergeEncIntoConfig(JsonObject config, Map<String, String> env) {
        JsonObject result = config.copy();

        if (env.containsKey(ENV_HTTP_HOST)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_HTTP_HOST);
            result.put(HTTP_HOST, env.get(ENV_HTTP_HOST));
        }
        if (env.containsKey(ENV_HTTP_PORT)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_HTTP_PORT);
            result.put(HTTP_PORT, env.get(ENV_HTTP_PORT));
        }

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


        if (env.containsKey(ENV_JWT_PASSWORD)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_JWT_PASSWORD);
            result.put(JWT_PASSWORD, env.get(ENV_JWT_PASSWORD));
        }


        if (env.containsKey(ENV_IMAGE_FOLDER_USER)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_IMAGE_FOLDER_USER);
            result.put(IMAGE_FOLDER_USER, env.get(ENV_IMAGE_FOLDER_USER));
        }
        if (env.containsKey(ENV_IMAGE_FOLDER_APPS)) {
            logger.warn("mergeEncIntoConfig() > " + ENV_IMAGE_FOLDER_APPS);
            result.put(IMAGE_FOLDER_APPS, env.get(ENV_IMAGE_FOLDER_APPS));
        }

        return result;
    }
}
