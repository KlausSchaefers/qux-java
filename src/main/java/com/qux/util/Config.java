package com.qux.util;

import io.vertx.core.json.JsonObject;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {

    enum MAIl_SSL_CONFIG {
        Optional,
        Disabled,
        Required
    }

    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    public static final String ENV_DEBUG = "QUX_DEBUG";

    public static final String ENV_HTTP_HOST = "QUX_HTTP_HOST";

    public static final String ENV_HTTP_PORT = "QUX_HTTP_PORT";

    public static final String ENV_MONGO_DB_NAME = "QUX_MONGO_DB_NAME";

    public static final String ENV_MONGO_TABLE_PREFIX = "QUX_MONGO_TABLE_PREFIX";

    public static final String ENV_MONGO_CONNECTION_STRING = "QUX_MONGO_CONNECTION_STRING";

    public static final String ENV_MAIL_USER = "QUX_MAIL_USER";

    public static final String ENV_MAIL_PASSWORD = "QUX_MAIL_PASSWORD";

    public static final String ENV_MAIL_HOST = "QUX_MAIL_HOST";

    public static final String ENV_MAIL_PORT = "QUX_MAIL_PORT";

    public static final String ENV_MAIL_SSL = "QUX_MAIL_SSL";

    public static final String ENV_JWT_PASSWORD = "QUX_JWT_PASSWORD";

    public static final String ENV_IMAGE_FOLDER_USER = "QUX_IMAGE_FOLDER_USER";

    public static final String ENV_IMAGE_FOLDER_APPS = "QUX_IMAGE_FOLDER_APPS";

    public static final String ENV_AUTH_SERVICE = "QUX_AUTH_SERVICE";

    public static final String ENV_KEY_CLOAK_REALM = "QUX_KEYCLOAK_REALM";

    public static final String ENV_KEY_CLOAK_SERVER = "QUX_KEYCLOAK_SERVER";

    public static final String ENV_KEY_CLOAK_CLAIM_ROLE = "QUX_KEY_CLOAK_CLAIM_ROLE";

    public static final String ENV_KEY_CLOAK_ISSUER = "QUX_KEY_CLOAK_ISSUER";

    public static final String ENV_KEY_CLOAK_CLAIM_ID = "QUX_KEY_CLOAK_CLAIM_ID";

    public static final String ENV_KEY_CLOAK_CLAIM_EMAIL = "QUX_KEY_CLOAK_CLAIM_EMAIL";

    public static final String ENV_KEY_CLOAK_CLAIM_NAME = "QUX_KEY_CLOAK_CLAIM_NAME";

    public static final String ENV_KEY_CLOAK_CLAIM_LASTNAME = "QUX_KEY_CLOAK_CLAIM_LASTNAME";

    public static final String ENV_USER_ALLOW_SIGNUP = "QUX_USER_ALLOW_SIGNUP";

    public static final String ENV_USER_ALLOWED_DOMAINS = "QUX_USER_ALLOWED_DOMAINS";

    public static final String DEBUG = "debug";

    public static final String HTTP_HOST = "http.host";

    public static final String HTTP_PORT = "http.port";

    public static final String MONGO_DB_NAME = "mongo.db_name";

    public static final String MONGO_TABLE_PREFIX = "mongo.table_prefix";

    public static final String MONGO_CONNECTION_STRING = "mongo.connection_string";

    public static final String MAIL_USER = "mail.user";

    public static final String MAIL_PASSWORD = "mail.password";

    public static final String MAIL_HOST = "mail.host";

    public static final String MAIL_PORT = "mail.port";

    public static final String MAIL_DEBUG = "mail.debug";

    public static final String MAIL_SSL = "mail.ssl";


    public static final String JWT_PASSWORD = "jwt.password";

    public static final String IMAGE_FOLDER_USER = "image.folder.user";

    public static final String IMAGE_FOLDER_APPS = "image.folder.apps";

    public static final String AUTH_SERVICE = "auth.service";

    public static final String KEY_CLOAK_CLIENT_SERVER = "auth.keycloak.server";

    public static final String KEY_CLOAK_CLIENT_REALM = "auth.keycloak.realm";

    public static final String KEY_CLOAK_CLIENT_CLAIM_LASTNAME = "auth.keycloak.claim.lastname";

    public static final String KEY_CLOAK_CLIENT_CLAIM_ROLE = "auth.keycloak.claim.role";

    public static final String KEY_CLOAK_CLIENT_ISSUER = "auth.keycloak.issuer";

    public static final String KEY_CLOAK_CLIENT_CLAIM_ID = "auth.keycloak.claim.id";

    public static final String KEY_CLOAK_CLIENT_CLAIM_EMAIL = "auth.keycloak.claim.email";

    public static final String KEY_CLOAK_CLIENT_CLAIM_NAME = "auth.keycloak.claim.name";

    public static final String USER_ALLOW_SIGNUP = "user.allowSignUp";

    public static final String USER_ALLOWED_DOMAINS = "user.allowedDomains";



    public static boolean isKeyCloak (JsonObject config) {
        return "keycloak".equals(config.getString(AUTH_SERVICE));
    }

    public static boolean isFileSystem(JsonObject config) {
        return true;
    }

    public static JsonObject getMail(JsonObject config) {
        JsonObject mailConfig = config.getJsonObject("mail");
        if (mailConfig == null) {
            mailConfig = new JsonObject()
                    .put("user", config.getString(MAIL_USER))
                    .put("password", config.getString(MAIL_PASSWORD))
                    .put("host", config.getString(MAIL_HOST))
                    .put("debug", config.containsKey(MAIL_DEBUG));

            if (config.containsKey(MAIL_PORT)) {
                mailConfig.put("port", config.getInteger(MAIL_PORT));
            }

            if (config.containsKey(MAIL_SSL)) {
                mailConfig.put("ssl", config.getString(MAIL_SSL));
            }
        }
        return mailConfig;
    }


    public static KeyCloakConfig getKeyCloak(JsonObject config) {
        KeyCloakConfig result = new KeyCloakConfig();
        result.setServer(config.getString(KEY_CLOAK_CLIENT_SERVER));
        result.setRealm(config.getString(KEY_CLOAK_CLIENT_REALM));
        result.setIssuer(config.getString(KEY_CLOAK_CLIENT_ISSUER));

        result.setClaimId(config.getString(KEY_CLOAK_CLIENT_CLAIM_ID));
        result.setClaimEmail(config.getString(KEY_CLOAK_CLIENT_CLAIM_EMAIL));
        result.setClaimName(config.getString(KEY_CLOAK_CLIENT_CLAIM_NAME));
        result.setClaimLastName(config.getString(KEY_CLOAK_CLIENT_CLAIM_LASTNAME));
        result.setClaimLastName(config.getString(KEY_CLOAK_CLIENT_CLAIM_LASTNAME));
        return result;
    }

    public static String getHttpHost(JsonObject config) {
        return config.getString(HTTP_HOST);
    }

    public static String getUserAllowedDomains(JsonObject config) {
        return config.getString(USER_ALLOWED_DOMAINS);
    }

    public static boolean getUserSignUpAllowed(JsonObject config) {
        return config.getBoolean(USER_ALLOW_SIGNUP);
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
        if(!result.containsKey(USER_ALLOW_SIGNUP)) {
            result.put(USER_ALLOW_SIGNUP, true);
        }
        if(!result.containsKey(USER_ALLOWED_DOMAINS)) {
            result.put(USER_ALLOWED_DOMAINS, "*");
        }
        return result;
    }

    public static JsonObject mergeEnvIntoConfig(JsonObject config){
        return mergeEnvIntoConfig(config, System.getenv());
    }



    public static JsonObject mergeEnvIntoConfig(JsonObject config, Map<String, String> env) {
        JsonObject result = config.copy();
        mergeDebug(env, result);
        mergeAuth(env, result);
        mergeKeyCloak(env, result);
        mergeMail(env, result);
        mergeHTTP(env, result);
        mergeMongo(env, result);
        mergeImage(env, result);
        mergeUser(env, result);
        return result;

    }
    private static void mergeDebug(Map<String, String> env, JsonObject result) {
        if (env.containsKey(ENV_DEBUG)) {
            logger.error("mergeDebug() > " + ENV_DEBUG + " > " + env.get(ENV_DEBUG));
            result.put(DEBUG, "true".equals(env.get(ENV_DEBUG)));
        }
    }

    private static void mergeUser(Map<String, String> env, JsonObject result) {
        if (env.containsKey(ENV_USER_ALLOWED_DOMAINS)) {
            logger.warn("mergeUser() > " + ENV_USER_ALLOWED_DOMAINS);
            result.put(USER_ALLOWED_DOMAINS, env.get(ENV_USER_ALLOWED_DOMAINS));
        }
        if (env.containsKey(ENV_USER_ALLOW_SIGNUP)) {
            logger.error("mergeUser() > " + ENV_USER_ALLOW_SIGNUP + " > " + env.get(ENV_USER_ALLOW_SIGNUP));
            result.put(USER_ALLOW_SIGNUP, !"false".equals(env.get(ENV_USER_ALLOW_SIGNUP)));
        }
    }


    private static void mergeImage(Map<String, String> env, JsonObject result) {
        if (env.containsKey(ENV_IMAGE_FOLDER_USER)) {
            logger.warn("mergeImage() > " + ENV_IMAGE_FOLDER_USER);
            result.put(IMAGE_FOLDER_USER, env.get(ENV_IMAGE_FOLDER_USER));
        }
        if (env.containsKey(ENV_IMAGE_FOLDER_APPS)) {
            logger.warn("mergeImage() > " + ENV_IMAGE_FOLDER_APPS);
            result.put(IMAGE_FOLDER_APPS, env.get(ENV_IMAGE_FOLDER_APPS));
        }
    }

    private static void mergeAuth(Map<String, String> env, JsonObject result) {
        if (env.containsKey(ENV_AUTH_SERVICE)) {
            logger.warn("mergeAuth() > " + ENV_AUTH_SERVICE);
            result.put(AUTH_SERVICE, env.get(ENV_AUTH_SERVICE));
        }

        if (env.containsKey(ENV_JWT_PASSWORD)) {
            logger.warn("mergeAuth() > " + ENV_JWT_PASSWORD);
            result.put(JWT_PASSWORD, env.get(ENV_JWT_PASSWORD));
        }
    }

    private static void mergeMongo(Map<String, String> env, JsonObject result) {
        if (env.containsKey(ENV_MONGO_CONNECTION_STRING)) {
            logger.warn("mergeMongo() > " + ENV_MONGO_CONNECTION_STRING);
            result.put(MONGO_CONNECTION_STRING, env.get(ENV_MONGO_CONNECTION_STRING));
        }
        if (env.containsKey(ENV_MONGO_DB_NAME)) {
            logger.warn("mergeMongo() > " + ENV_MONGO_DB_NAME);
            result.put(MONGO_DB_NAME, env.get(ENV_MONGO_DB_NAME));
        }
        if (env.containsKey(ENV_MONGO_TABLE_PREFIX)) {
            logger.warn("mergeMongo() > " + ENV_MONGO_TABLE_PREFIX);
            result.put(MONGO_TABLE_PREFIX, env.get(ENV_MONGO_TABLE_PREFIX));
        }
    }

    private static void mergeHTTP(Map<String, String> env, JsonObject result) {
        if (env.containsKey(ENV_HTTP_HOST)) {
            logger.warn("mergeHTTP() > " + ENV_HTTP_HOST);
            result.put(HTTP_HOST, env.get(ENV_HTTP_HOST));
        }
        if (env.containsKey(ENV_HTTP_PORT)) {
            logger.warn("mergeHTTP() > " + ENV_HTTP_PORT);
            try {
                String port = env.get(ENV_HTTP_PORT);
                result.put(HTTP_PORT,Integer.parseInt(port));
            } catch (Exception e) {
                logger.error("Config.mergeHTTP() > Could not merge http.port from env: " + ENV_HTTP_PORT);
            }

        }
    }

    private static void mergeMail(Map<String, String> env, JsonObject result) {
        if (env.containsKey(ENV_MAIL_HOST)) {
            logger.warn("mergeMail() > " + ENV_MAIL_HOST);
            result.put(MAIL_HOST, env.get(ENV_MAIL_HOST));
        }
        if (env.containsKey(ENV_MAIL_USER)) {
            logger.warn("mergeMail() > " + ENV_MAIL_USER);
            result.put(MAIL_USER, env.get(ENV_MAIL_USER));
        }
        if (env.containsKey(ENV_MAIL_PASSWORD)) {
            logger.warn("mergeMail() > " + ENV_MAIL_PASSWORD);
            result.put(MAIL_PASSWORD, env.get(ENV_MAIL_PASSWORD));
        }
        if (env.containsKey(ENV_MAIL_PORT)) {
            logger.warn("mergeMail() > " + ENV_MAIL_PORT);
            try {
                int port = Integer.parseInt(env.get(ENV_MAIL_PORT));
                result.put(MAIL_PORT, port);
            } catch (Exception e) {
                logger.warn("mergeMail() > Could not parse port: " + env.get(ENV_MAIL_PORT));
            }
        }
        if (env.containsKey(ENV_MAIL_SSL)) {
            logger.warn("mergeMail() > " + ENV_MAIL_SSL);
            String value = env.get(ENV_MAIL_SSL);
            if (!"required".equalsIgnoreCase(value)) {
                if ("optional".equalsIgnoreCase(value)) {
                    result.put(MAIL_SSL, MAIl_SSL_CONFIG.Optional.name());
                } else if ("disabled".equalsIgnoreCase(value)) {
                    result.put(MAIL_SSL, MAIl_SSL_CONFIG.Disabled.name());
                } else {
                    logger.error("mergeMail() > Use 'required','optional' or 'disabled' " + ENV_MAIL_SSL);
                }
            } else {
                result.put(MAIL_SSL, MAIl_SSL_CONFIG.Required.name());
            }
        }
    }


    private static void mergeKeyCloak(Map<String, String> env, JsonObject result) {

        if (env.containsKey(ENV_KEY_CLOAK_CLAIM_LASTNAME)) {
            logger.warn("mergeKeyCloak() > " + ENV_KEY_CLOAK_CLAIM_LASTNAME);
            result.put(KEY_CLOAK_CLIENT_CLAIM_LASTNAME, env.get(ENV_KEY_CLOAK_CLAIM_LASTNAME));
        }

        if (env.containsKey(ENV_KEY_CLOAK_CLAIM_NAME)) {
            logger.warn("mergeKeyCloak() > " + ENV_KEY_CLOAK_CLAIM_NAME);
            result.put(KEY_CLOAK_CLIENT_CLAIM_NAME, env.get(ENV_KEY_CLOAK_CLAIM_NAME));
        }

        if (env.containsKey(ENV_KEY_CLOAK_CLAIM_EMAIL)) {
            logger.warn("mergeKeyCloak() > " + ENV_KEY_CLOAK_CLAIM_EMAIL);
            result.put(KEY_CLOAK_CLIENT_CLAIM_EMAIL, env.get(ENV_KEY_CLOAK_CLAIM_EMAIL));
        }

        if (env.containsKey(ENV_KEY_CLOAK_CLAIM_ID)) {
            logger.warn("mergeKeyCloak() > " + ENV_KEY_CLOAK_CLAIM_ID);
            result.put(KEY_CLOAK_CLIENT_CLAIM_ID, env.get(ENV_KEY_CLOAK_CLAIM_ID));
        }

        if (env.containsKey(ENV_KEY_CLOAK_ISSUER)) {
            logger.warn("v() > " + ENV_KEY_CLOAK_ISSUER);
            result.put(KEY_CLOAK_CLIENT_ISSUER, env.get(ENV_KEY_CLOAK_ISSUER));
        }

        if (env.containsKey(ENV_KEY_CLOAK_CLAIM_ROLE)) {
            logger.warn("mergeKeyCloak() > " + ENV_KEY_CLOAK_CLAIM_ROLE);
            result.put(KEY_CLOAK_CLIENT_CLAIM_ROLE, env.get(ENV_KEY_CLOAK_CLAIM_ROLE));
        }

        if (env.containsKey(ENV_KEY_CLOAK_SERVER)) {
            logger.warn("mergeKeyCloak() > " + ENV_KEY_CLOAK_SERVER);
            result.put(KEY_CLOAK_CLIENT_SERVER, env.get(ENV_KEY_CLOAK_SERVER));
        }

        if (env.containsKey(ENV_KEY_CLOAK_REALM)) {
            logger.warn("mergeKeyCloak() > " + ENV_KEY_CLOAK_REALM);
            result.put(KEY_CLOAK_CLIENT_REALM, env.get(ENV_KEY_CLOAK_REALM));
        }
    }


    public static boolean isMailSSLOptional(JsonObject config) {
        if (config.containsKey("ssl")) {
            return MAIl_SSL_CONFIG.Optional.name().equalsIgnoreCase(config.getString("ssl"));
        }
        return false;
    }

    public static boolean isMailSSLDisabled(JsonObject config) {
        if (config.containsKey("ssl")) {
            return MAIl_SSL_CONFIG.Disabled.name().equalsIgnoreCase(config.getString("ssl"));
        }
        return false;
    }

    public static boolean isMailSSLRequired(JsonObject config) {
        if (config.containsKey("ssl")) {
            return MAIl_SSL_CONFIG.Required.name().equalsIgnoreCase(config.getString("ssl"));
        }
        return false;
    }

}

