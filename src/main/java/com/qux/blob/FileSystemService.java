package com.qux.blob;

import io.vertx.core.Handler;
import io.vertx.core.file.FileSystem;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemService implements IBlobService{


    private Logger logger = LoggerFactory.getLogger(FileSystemService.class);


    private final String imageFolder;

    public FileSystemService(String imageFolder) {
        this.imageFolder = imageFolder;
    }

    public void setBlob(RoutingContext event, String source, String target, Handler<Boolean> handler) {
        logger.info("setBlob() > enter");
        FileSystem fs = event.vertx().fileSystem();
        fs.move(source, target , moveResult-> {
            if (moveResult.succeeded()) {
                handler.handle(true);
            } else {
                handler.handle(false);
            }
        });
    }

    public void getBlob(RoutingContext event, String id, String image) {
        logger.info("getBlob() > enter");
        String file = imageFolder +"/" + id + "/" + image ;
        FileSystem fs = event.vertx().fileSystem();
        fs.exists(file, exists-> {
            if(exists.succeeded() && exists.result()){
                event.response().putHeader("Cache-Control", "no-transform,public,max-age=86400,s-maxage=86401");
                event.response().putHeader("ETag", id);
                event.response().sendFile(file);
            } else {
                event.response().setStatusCode(404);
                event.response().end();
            }
        });
    }

    public String createFolder(RoutingContext event, String appID) {
        /**
         * make folder if not exists
         */
        FileSystem fs = event.vertx().fileSystem();
        String folder = imageFolder +"/" + appID;
        fs.mkdirsBlocking(folder);
        return folder;
    }


    public void deleteFile(RoutingContext event, String id, String fileName) {
        String file = imageFolder +"/" + id + "/" + fileName;
        FileSystem fs = event.vertx().fileSystem();
        fs.delete(file, fres->{
            if(!fres.succeeded()){
                logger.error("delete() > Could not delete from file system !" + file);
            }
        });
    }
}
