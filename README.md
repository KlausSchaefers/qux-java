[![Maven Package](https://github.com/KlausSchaefers/qux-java/actions/workflows/maven-publish.yml/badge.svg)](https://github.com/KlausSchaefers/qux-java/actions/workflows/maven-publish.yml)

# Quant-UX

This is the Quant-UX server backend. To run this you need a MongoDB server and a folder where
images are stored.

You can try out a running version here: https://quant-ux.com

## Config

Edit the config file (e.g. matc.conf). Do not forget to update the *jwt.password* property. For a clustered setup, you need to keep
the passwords the same. If you keep the password blank, a random password is generated at startup

```javascript
{
  "debug" : false,
  "http.port" : 8080, // The server port
  "http.host": "your.server.com" // The domain name of your server. This is important for the mail that will be send. Otherwise links will not work
  "image.folder.user" : "test/user", // folder where user images will be stored
  "image.folder.apps" : "test/apps", // folder where app images will be stored
  "image.size" : 50000000, // max image size for uploads
  "mongo.db_name": "MATC", // mongo DB to use
  "mongo.connection_string": "mongodb://localhost:27017" // connection string, might include password and username
  "mail.user": "", // POP user name for mail sending
  "mail.password" : "", // password or token
  "mail.host": "", // URL of mail server
  "admin": "admin@quant-ux.com", // Internal mails will be send to this persons
  "jwt.password": "test" // JWT password
}
```


You can also provide the configuration through ENV variables. The following variables are supported, and map 
to the JSON definitions.

```

    QUX_HTTP_HOST

    QUX_HTTP_PORT
    
    QUX_MONGO_DB_NAME

    QUX_MONGO_TABLE_PREFIX

    QUX_MONGO_CONNECTION_STRING

    QUX_MAIL_USER

    QUX_MAIL_PASSWORD

    QUX_MAIL_HOST

    QUX_JWT_PASSWORD

    QUX_IMAGE_FOLDER_USER

    QUX_IMAGE_FOLDER_APPS
    
```

Please note that we have replaced the old config of nested objects with a straight dot notation. 


## Deveoptment

You might need a mongo server. The simplest way is to use Docker.

```
docker run -p 27017:27017 --name quxmongo2 -d mongo:4.4   

```

## Start server

java -jar server-3.20.0-fat.jar -conf matc.conf -instances 4

## Dev Setup

In InteliJ create a new runner with the following parameters:

- *Main Class*: io.vertx.core.Starter

- *Program Arguments*: run com.qux.MATC -conf matc.conf
