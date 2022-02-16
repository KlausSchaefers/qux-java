package com.qux.blob;

import com.qux.model.Image;
import io.vertx.core.Handler;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

public interface IBlobService {

    void setBlob(RoutingContext event, String source, String target, Handler<Boolean> handler);

    void getBlob(RoutingContext event, String appID, String file);

    String createFolder(RoutingContext event, String appID);

    void deleteFile(RoutingContext event, String id, String fileName);

}
