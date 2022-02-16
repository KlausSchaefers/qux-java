package com.qux.blob;


import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;


public class S3BlobService implements IBlobService {

    @Override
    public void setBlob(RoutingContext event, String tempSource, String target, Handler<Boolean> handler) {
        // we might need to delete all temp files!!
    }

    @Override
    public void copyBlob(RoutingContext event, String source, String target, Handler<Boolean> handler) {

    }

    @Override
    public void getBlob(RoutingContext event, String appID, String file) {

    }

    @Override
    public String createFolder(RoutingContext event, String appID) {
        return null;
    }

    @Override
    public void deleteFile(RoutingContext event, String id, String fileName, Handler<Boolean> handler) {

    }
}
