package com.qux.util;

import com.qux.model.User;
import io.vertx.ext.web.FileUpload;

public class ImageFileUpload implements FileUpload {

    private final FileUpload upload;

    public ImageFileUpload (FileUpload upload) {
        this.upload = upload;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    private int width, height;



    @Override
    public String name() {
        return this.upload.name();
    }

    @Override
    public String uploadedFileName() {
        return this.upload.uploadedFileName();
    }

    @Override
    public String fileName() {
        return upload.fileName();
    }

    @Override
    public long size() {
        return this.upload.size();
    }

    @Override
    public String contentType() {
        return this.upload.contentType();
    }

    @Override
    public String contentTransferEncoding() {
        return this.upload.contentTransferEncoding();
    }

    @Override
    public String charSet() {
        return this.upload.charSet();
    }
}
