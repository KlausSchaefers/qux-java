# Quant-UX

This is the Quant-UX server backend. To run this you need a MongoDB server and a fodler where
images etc are stored.

## Config

Edit the config file. Do not forget to update the jwt password property. For a clustered setup, you need to keep
the passwords the same. If you keep the password blank, a random password is generated at startup

```javascript
{
	"debug" : false,
	"http.port" : 8080,
	"http.server": "localhost",
	"image.folder.user" : "test/user",
	"image.folder.apps" : "test/apps",
	"image.folder.cms" : "test/cms",
	"backup.fs.folder": "test/backup",
	"image.size" : 50000000,
	"mongo": {
		"db_name" : "MATC",
		"connection_string" : "mongodb://localhost:27017"
	},
	"mail": {
		"user" : "",
		"password" : "",
		"host": ""
	},
	"admin": "admin@quant-ux.com",
	"jwt" : {
		"password": "Test"
	}
}
```


## Start server

java -jar server-3.0.4-fat.jar -conf matc.conf -instances 4

## Dev Setup

Main Class: io.vertx.core.Starter

Program Arguments: run com.qux.MATC -conf matc.conf
