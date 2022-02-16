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

    @Override
    public void copyBlob(RoutingContext event, String source, String target, Handler<Boolean> handler) {
        logger.info("copyBlob() > enter " + source + " to " + target);
        FileSystem fs = event.vertx().fileSystem();
        String sourceFile = imageFolder + "/" + source;
        String targetFile = imageFolder + "/" + target;
        fs.copy(sourceFile, targetFile, fileResult ->{
            if(!fileResult.succeeded()){
               handler.handle(true);
            } else {
                logger.error("copyBlob() > error ", fileResult.cause());
                handler.handle(false);
            }
        });
    }

    public void getBlob(RoutingContext event, String folder, String image) {
        logger.info("getBlob() > enter");
        String file = imageFolder +"/" + folder + "/" + image ;
        FileSystem fs = event.vertx().fileSystem();
        fs.exists(file, exists-> {
            if(exists.succeeded() && exists.result()){
                logger.info("getBlob() > stream > " + file);
                event.response().putHeader("Cache-Control", "no-transform,public,max-age=86400,s-maxage=86401");
                event.response().putHeader("ETag", folder + image);
                event.response().sendFile(file);
            } else {
                logger.info("getBlob() > not found > " + file);
                event.response().setStatusCode(404);
                event.response().end();
            }
        });
    }

    public String createFolder(RoutingContext event, String folderName) {
        logger.info("createFolder() > enter > " + folderName);
        FileSystem fs = event.vertx().fileSystem();
        String folder = imageFolder +"/" + folderName;
        fs.mkdirsBlocking(folder);
        return folder;
    }


    public void deleteFile(RoutingContext event, String folder, String fileName, Handler<Boolean> handler) {
        String file = imageFolder +"/" + folder + "/" + fileName;
        FileSystem fs = event.vertx().fileSystem();
        fs.delete(file, deleteResult->{
            if(!deleteResult.succeeded()){
                logger.error("delete() > Could not delete from file system !" + file);
            }
            if (handler != null) {
                handler.handle(deleteResult.succeeded());
            }
        });
    }
}
