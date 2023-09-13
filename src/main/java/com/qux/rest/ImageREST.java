 package com.qux.rest;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.qux.MATC;
import com.qux.acl.InvitationACL;
import com.qux.auth.ITokenService;
import com.qux.blob.IBlobService;
import com.qux.model.Image;
import com.qux.model.Model;
import com.qux.model.User;
import com.qux.util.ImageFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.util.DB;
import com.qux.util.rest.MongoREST;
import com.qux.util.Util;
import io.vertx.core.Handler;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

public class ImageREST extends MongoREST {

	private long imageSize;

	private final int maxUploads = 20;
	
	private final String image_db;
	
	private final Logger logger = LoggerFactory.getLogger(ImageREST.class);
	
	private final InvitationACL invACL;
	
	private final Set<String> supportedTypes;

	private final IBlobService blobService;
	
		
	public ImageREST(
			ITokenService tokenService,
			IBlobService blobService,
			MongoClient mongo,
			long maxImageSize
	) {
		super(tokenService, mongo, Image.class);
		this.blobService = blobService;
		this.imageSize = maxImageSize;
		this.invACL = new InvitationACL(mongo);
		this.image_db = DB.getTable(Image.class);
		this.supportedTypes = new HashSet<>();
		this.supportedTypes.add("jpg");
		this.supportedTypes.add("png");
		this.supportedTypes.add("jpeg");
		this.supportedTypes.add("gif");
	}
	

	/********************************************************************************************
	 * Set Image
	 ********************************************************************************************/

	
	
	public Handler<RoutingContext> setImage() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				setImage(event);
			}
		};
	}

	public void setImage(RoutingContext event) {
		String id  = getId(event);
		if (this.acl != null) {
			this.acl.canWrite(getUser(event), event, allowed -> {
				if (allowed) {
					setImage(event, id);
				} else {
					User user = getUser(event);
					error("setImage", "User "+ getUser(event) + " tried to upload image to "+ event.request().path());
					this.getACLList(user, userApps -> {
						error("setImage", "User " + user + " can read: " + userApps);
					});
					returnError(event, 405);
				}
			});
		} else {
			this.setImage(event, id);
		}
	}
	
	public void setImage(RoutingContext event, String appID) {
		logger.debug("setImage() > " + appID);


		Set<FileUpload> files = event.fileUploads();
		
		ArrayList<ImageFileUpload> toUpload = new ArrayList<>();
		ArrayList<FileUpload> toDelete = new ArrayList<>();
		ArrayList<String> errors = new ArrayList<>();
		
		if (files.size() <=  this.maxUploads){
			for (FileUpload file : files){
				if(checkImage(file)){
					toUpload.add(new ImageFileUpload(file));
				} else {
					toDelete.add(file);
					error("setImage", "Shitty file!");
					errors.add(file.fileName());
				}
			}
		}  else {
			error("setImage", "Too many uploads!");
			toDelete.addAll(files);
			logger.error("setImage() > Too many uploads");
		}
		
		logger.debug("setImage() > " + toUpload.size() + " > " + toDelete.size());

		/**
		 * delete too big temp files
		 */
		FileSystem fs = event.vertx().fileSystem();
		Util.cleanUpUploads(toDelete, fs);

		/**
		 * make folder if not exists
		 */
		String folder = this.blobService.createFolder(event, appID);

		/**
		 * read meta data async to have with and height
		 */
		this.addImageMetaData (event, toUpload, metaResult -> {
			/**
			 * now start moving and deleting files
			 */
			copyFiles(event, appID, folder, metaResult, errors, new ArrayList<>());
		});
	}


	private void addImageMetaData (RoutingContext event, List<ImageFileUpload> uploads, Handler<List<ImageFileUpload>> handler) {
		logger.error("addImageMetaData > enter");
		event.vertx().executeBlocking(promise -> {
			try {
				for (ImageFileUpload file : uploads) {
					addWidthAndHeight(file);
				}
			} catch (Exception err) {
				promise.fail(err);
				return;
			}
			promise.complete();
		}, blockingResult -> {
			if (blockingResult.succeeded()) {

				handler.handle(uploads);
			} else {
				logger.error("addImageMetaData > Could not process images in bus");
				returnError(event, "image.processing.error");
			}
		} );
	}


	private void addWidthAndHeight (ImageFileUpload image) {
		String filename = image.uploadedFileName();
		BufferedImage bufferedImage = Util.getImage(filename);
		if (bufferedImage != null) {
			image.setWidth(bufferedImage.getWidth());
			image.setHeight(bufferedImage.getHeight());
		} else {
			logger.error("addWidthAndHeight() Cannot get size for " + image.uploadedFileName());
		}
	}


	private void copyFiles(
			RoutingContext event,
			String appID,
			String folder,
			List<ImageFileUpload> uploads,
			List<String> errors,
			List<Image> result
	){
		
		logger.debug("copyFiles() > " + uploads.size());
		User user = getUser(event);
		
		if (uploads.size() > 0){
			/**
			 * If there are more files to copy
			 * run another recurson. Do this until list
			 * is empty
			 */
			
			ImageFileUpload file = uploads.remove(0);
					
			String type = Util.getFileType(file.fileName());
			String imageID = Util.getRandomString();
			String image = imageID + "." + type;
			String dest = folder +"/" +  image;
			
			logger.debug("copyFiles() > move " + file.fileName());

			this.blobService.setBlob(event, file.uploadedFileName(), dest, blobResult -> {
				if (blobResult.booleanValue() == true) {
					/**
					 * save an image in the db.
					 */
					Image img = new Image();
					img.setName(file.fileName());
					img.setUrl(appID + "/" + image);
					img.setAppID(appID);
					img.setUserID(user.getId());
					img.setHeight(file.getHeight());
					img.setWidth(file.getWidth());

					result.add(img);
				} else {
					errors.add("image.copy.error" + file.uploadedFileName());
				}
				/**
				 * call recursive
				 */
				copyFiles(event, appID, folder, uploads, errors, result);
			});

		} else {
			/**
			 * No more files to copy, we start deleting the temp files
			 * and save the results in mongo
			 */
			saveImages(event, result, new ArrayList<Image>(), errors, appID );
		}
	}


	
	protected void saveImages(RoutingContext event, List<Image> uploads, List<Image> saved, List<String> errors, String appID){
		logger.debug("saveImages() > " + uploads.size());
		if (uploads.size() > 0){
			Image img = uploads.remove(0);
			mongo.save(image_db, mapper.toVertx(img), res->{
				if(res.succeeded()){
					img.setId(res.result());
					saved.add(img);
				} else {
					logger.error("saveImages() > Could not save in mongo " + img.getUrl()); 
				}
				// call recursive  until list is empty
				saveImages(event, uploads, saved, errors, appID);
			});
		} else {
			this.onImageUploadDone(event, saved, errors, appID);
		}
	}
	
	protected void onImageUploadDone(RoutingContext event, List<Image> uploads, List<String> errors, String appID){
		logger.info("onImageUploadDone() > uploads: " + uploads.size() + " >  errors: " + errors.size());

		JsonArray images = new JsonArray();
		for (Image img : uploads){
			JsonObject i = mapper.toVertx(img);
			i.put("id", img.getId());
			images.add(i);
		}
		sendUploadResponse(event, errors, images);
	}

	private void sendUploadResponse(RoutingContext event, List<String> errors, JsonArray images) {
		/**
		 * assemble final response;
		 */
		JsonObject result = new JsonObject()
			.put("uploads", images);

		if (errors.size() > 0){
			result.put("errors", new JsonArray(errors));
		}
		returnJson(event, result);
	}

	private boolean checkImage(FileUpload file) {
		String type = Util.getFileType(file.fileName());
		if (supportedTypes.contains(type)){
			if(file.size() > this.imageSize){
				logger.error("checkImage() > Toooo biggg " + file.size());
			}
			return file.size() < this.imageSize;
		}
		logger.error("checkImage() > Not supported type " + type);
		return false;
	}

	/********************************************************************************************
	 * Get Image
	 ********************************************************************************************/

	public Handler<RoutingContext> getInvitationImage() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				getInvitationImage(event);
			}
		};
	}
	

	public void getInvitationImage(RoutingContext event) {
		this.invACL.canTest(getUser(event), event, res ->{
			if(res){
				String appID  = event.request().getParam("appID");
				String image = event.request().getParam("image");
				getImage(event, appID, image);
			} else {
				logger.error("getInvitationImage() > " + getUser(event) + " tried to read the image " +event.request().path() );
				returnError(event, 404);
			}
		});
	}
	
	
	/********************************************************************************************
	 * Get Image
	 ********************************************************************************************/


	public Handler<RoutingContext> getImage() {
		return new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext event) {
				getImage(event);
			}
		};
	}
	

	public void getImage(RoutingContext event) {
		String id  = getId(event);
		String image = event.request().getParam("image");
		if (this.acl != null) {
			User user = getUser(event);
			this.acl.canRead(user, event, allowed -> {
				if (allowed) {
					getImage(event, id, image);
				} else {
					logger.error("getImage() > " + user + " tried to read the image " +event.request().path() );
					returnError(event, 404);
				}
			});
		} else {
			getImage(event, id, image);
		}
	}
	

	public void getImage(RoutingContext event, String appID, String image) {
		this.blobService.getBlob(event, appID, image);
	}


	/********************************************************************************************
	 * Delete Image
	 ********************************************************************************************/
	public void delete(RoutingContext event, String id) {
		logger.info("delete() > enter " +  id);
		String imageID = event.request().getParam("imageID");
		String fileName = event.request().getParam("file");
		mongo.removeDocuments(table, Model.findById(imageID), res ->{
			if(res.succeeded()){
				returnOk(event, "image.deleted");
				this.blobService.deleteFile(event, id, fileName, deleteResult -> {
					logger.error("delete() > exit: " + deleteResult);
				});
			} else {
				logger.error("delete() > Could not delete from mongo!");
				returnError(event, 405);
			}
		});
	}





}

