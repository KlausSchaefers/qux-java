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

    private boolean isInsideBase(String path) {
        try {
            String baseCanonical = new java.io.File(imageFolder).getCanonicalPath();
            String fileCanonical = new java.io.File(path).getCanonicalPath();
            return fileCanonical.startsWith(baseCanonical + java.io.File.separator);
        } catch (java.io.IOException e) {
            logger.error("isInsideBase() > Could not resolve canonical path !" + path, e);
            return false;
        }
    }

    public void setBlob(RoutingContext event, String source, String target, Handler<Boolean> handler) {
        logger.info("setBlob() > enter");
        if (!isInsideBase(target)) {
            logger.error("setBlob() > Path traversal attempt detected !" + target);
            handler.handle(false);
            return;
        }
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
        if (!isInsideBase(sourceFile) || !isInsideBase(targetFile)) {
            logger.error("copyBlob() > Path traversal attempt detected !" + sourceFile + " " + targetFile);
            handler.handle(false);
            return;
        }
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
        if (!isInsideBase(file)) {
            logger.error("getBlob() > Path traversal attempt detected !" + file);
            event.response().setStatusCode(404);
            event.response().end();
            return;
        }
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
        if (!isInsideBase(folder)) {
            logger.error("createFolder() > Path traversal attempt detected !" + folder);
            throw new IllegalArgumentException("Invalid folder name");
        }
        fs.mkdirsBlocking(folder);
        return folder;
    }


    public void deleteFile(RoutingContext event, String folder, String fileName, Handler<Boolean> handler) {
        String file = imageFolder +"/" + folder + "/" + fileName;
        if (!isInsideBase(file)) {
            logger.error("delete() > Path traversal attempt detected !" + file);
            if (handler != null) {
                handler.handle(false);
            }
            return;
        }
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
