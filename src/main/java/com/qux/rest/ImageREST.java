 package com.qux.rest;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import com.qux.MATC;
import com.qux.acl.InvitationACL;
import com.qux.model.Image;
import com.qux.model.Model;
import com.qux.model.User;
import com.qux.util.Mail;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.util.DB;
import com.qux.util.MongoREST;
import com.qux.util.Util;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

public class ImageREST extends MongoREST {
	
	private final String imageFolder;
	
	private long imageSize = 1024* 1024 * 10;
	
	private int maxUploads = 20;
	
	private final String image_db;
	
	private Logger logger = LoggerFactory.getLogger(ImageREST.class);
	
	private final InvitationACL invACL;
	
	private final Set<String> supportedTypes;
	
		
	public ImageREST(MongoClient mongo, String folder, JsonObject conf) {
		super(mongo, Image.class);
		this.imageSize = conf.getLong("image.size");
		this.imageFolder = folder;
		this.invACL = new InvitationACL(mongo);
		this.image_db = DB.getTable(Image.class);
		this.supportedTypes = new HashSet<>();
		this.supportedTypes.add("jpg");
		this.supportedTypes.add("png");
		this.supportedTypes.add("jpeg");
		this.supportedTypes.add("psd");
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
						Mail.error(event, "ImageRest.setImage() > User "+ getUser(event)+ " tried to CREATE without permission. User Apps: " + userApps);
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
		
		ArrayList<FileUpload> toCopy = new ArrayList<FileUpload>();
		ArrayList<FileUpload> toDelete = new ArrayList<FileUpload>();
		
		ArrayList<Image> uploads = new ArrayList<Image>();
		ArrayList<String> errors = new ArrayList<String>();
		
		if(files.size() <=  this.maxUploads){
			for(FileUpload file : files){
				
				
				if(checkImage(file)){
					toCopy.add(file);			
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
		
		logger.debug("setImage() > " + toCopy.size() + " > " + toDelete.size()); 
		
		
		/**
		 * make folder if not exists
		 */
		FileSystem fs = event.vertx().fileSystem();
		String folder = imageFolder +"/" + appID ;
		fs.mkdirs(folder, res -> {
			/**
			 * now start moving and deleting files
			 */
			copyFiles(event, appID, folder, toCopy, toDelete, errors, uploads);
		});
	}
	
	
	private void copyFiles(RoutingContext event, String id, String folder, ArrayList<FileUpload> copies, ArrayList<FileUpload> delete,
			List<String> errors, List<Image> uploads){
		
		logger.debug("copyFiles() > " + copies.size());
		
		//System.out.println("copyFiles " + id + " " + copies.size());
		
		FileSystem fs = event.vertx().fileSystem();

		
		if(copies.size() > 0){
			/**
			 * If there are more files to copy
			 * run another recurson. Do this until list
			 * is empty
			 */
			
			FileUpload file = copies.remove(0);
					
			String type = Util.getFileType(file.fileName());
			String imageID = Util.getRandomString();
			String image = imageID + "." + type;
			String dest = folder +"/" +  image;
			
			logger.debug("copyFiles() > move " + file.fileName());

			fs.move(file.uploadedFileName(),dest , moveResult->{
				/**
				 * We move, this we do not have to delete the files!
				 */
				if(moveResult.succeeded()){
					
					/**
					 * save an image in the db.
					 */
					Image img = new Image();
					img.setName(file.fileName());
					img.setUrl(id + "/" + image);
					img.setAppID(id);
					
					uploads.add(img);
				} else {
					errors.add("image.copy.error" + file.uploadedFileName());
				}
				
				/**
				 * call recursive
				 */
				copyFiles(event, id, folder, copies, delete, errors, uploads);
	
			});
			
		} else {
			/**
			 * No more files to copy, we start deleting
			 */
			deleteFiles(event, id, delete, errors, uploads);
			
		}
		
		
	}

	private void deleteFiles(RoutingContext event, String id, ArrayList<FileUpload> delete, List<String> errors, List<Image> uploads){
	
		/**
		 * Delete other uploads
		 */
		FileSystem fs = event.vertx().fileSystem();
		Util.cleanUpUploads(delete, fs);
		
		/**
		 * now pass to template method that can be overwritten.
		 */
		saveImages(event, uploads, new ArrayList<Image>(), errors, id );
	}
	
	
	protected void saveImages(RoutingContext event, List<Image> uploads, List<Image> saved, List<String> errors, String appID){
		logger.debug("saveImages() > " + uploads.size());
		
		if(uploads.size() > 0){
			Image img = uploads.remove(0);
		
			mongo.save(image_db, mapper.toVertx(img), res->{
				if(res.succeeded()){
					img.setId(res.result());
					saved.add(img);
				} else {
					logger.error("saveImages() > Could not save in mongo " + img.getUrl()); 
				}
				
				saveImages(event, uploads, saved, errors, appID);
			});
		} else {
			this.onImageUploadDone(event, saved, errors, appID);
		}
	}
	
	protected void onImageUploadDone(RoutingContext event, List<Image> uploads, List<String> errors, String appID){
		
		logger.info("onImageUploadDone() > uploads: " + uploads.size() + " >  errors: " + errors.size());
		
		
		if(uploads.size() >0){
			/**
			 * pass the image processing on the bus!
			 */
			User user = getUser(event);
			JsonArray array = new JsonArray();
			for(Image img : uploads){
				img.setUserID(user.getId());
				array.add(mapper.toVertx(img));
			}
			
			JsonObject busMessage = new JsonObject()
					.put("images", array)
					.put("appID", appID);
			

			event.vertx().eventBus().send(MATC.BUS_IMAGES_UPLOADED, busMessage, res->{
				
				if(res.succeeded()){
					Message<Object> message = res.result();
					
					Object obj = message.body();
					if(obj instanceof JsonArray){
						JsonArray imgages = (JsonArray)obj;
						
						/**
						 * assemble final response;
						 */
						JsonObject result = new JsonObject()
							.put("uploads", imgages);
						
						if(errors.size() > 0){
							result.put("errors", new JsonArray(errors));
						}
						
						
						returnJson(event, result);
					}else {
						logger.error("onImageUploadDone > Bus returned wrong type");
						returnError(event, "image.processing.error");
					}
					
				} else {
					logger.error("onImageUploadDone > Could not process images in bus");
					returnError(event, "image.processing.error");
				}
			});
		} else {
			
			logger.error("onImageUploadDone() > no correct files: " + uploads.size() + " >  errors: " + errors.size());
			
			
			/**
			 * assemble final response;
			 */
			JsonObject result = new JsonObject()
				.put("uploads", new JsonArray());
			
			if(errors.size() > 0){
				result.put("errors", new JsonArray(errors));
			}
			
			returnJson(event, result);
		}
		
	
	}

	
	private boolean checkImage(FileUpload file) {
		String type = Util.getFileType(file.fileName());
		if(supportedTypes.contains(type)){
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
					this.getACLList(user, userApps -> {
						error("getImage", "User " + user + " can read: " + userApps);
						Mail.error(event, "ImageRest.getImage() > User "+ getUser(event)+ " tried to READ without permission. User Apps: " + userApps);
					});

					returnError(event, 404);
				}
			});
		} else {
			getImage(event, id, image);
		}
	}
	
	
	
	public void getImage(RoutingContext event, String id, String image) {
		

		String file = imageFolder +"/" + id + "/" + image ;
		FileSystem fs = event.vertx().fileSystem();
			
	
		fs.exists(file, exists-> {
			if(exists.succeeded() && exists.result()){
				int w = getWidth(event);
				event.response().putHeader("Cache-Control", "no-transform,public,max-age=86400,s-maxage=86401");
				event.response().putHeader("ETag", id);
		
				if(w >0){					
					getScalledImage(event, image, file, w);					
				} else {
					event.response().sendFile(file);
				}
				//logger.info("getImage() > serve : {0} ", file);
	
			} else {
				error("getImage", "Not file with name "+ file);
				returnError(event,404);
			}
		});
	
	}


	private void getScalledImage(RoutingContext event, String image, String file, int w) {
		event.vertx().executeBlocking(future ->{
			try{			
				long start = System.currentTimeMillis();
				
				String type = image.substring(image.lastIndexOf(".")+1, image.length());
				BufferedImage originalImage = ImageIO.read(new File(file));
				int h = (int)(originalImage.getHeight() * ((double)w / (double)originalImage.getWidth()));
				BufferedImage thumbnail = Scalr.resize(originalImage, Scalr.Method.QUALITY, Scalr.Mode.FIT_TO_WIDTH,  w, h, Scalr.OP_ANTIALIAS);

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageIO.write(thumbnail, type, out);
				
				event.response().putHeader("content-type", "image/"+type);
				event.response().end(Buffer.buffer(out.toByteArray()));
				
				long stop = System.currentTimeMillis();
				logger.info("getImage() > Resizing took "+ (stop - start) + "ms for " + type);
			} catch(Exception e){
				error("getImage", "Cannot read file "+ file);
				event.response().sendFile(file);
			}
			future.complete();
		},  res -> {
		    /**
		     * Do nothing in here
		     */
		});
	}
	
	public int getWidth(RoutingContext event){
		String w = event.request().getParam("w");
		if(w!=null){
			try{
				return Integer.parseInt(w);
			}catch (Exception e){
				logger.error("getWidth() > " + getUser(event) + " tried w paramter " + w);
			}
		}
		return 0;
	}		


	

	/********************************************************************************************
	 * Set Image
	 ********************************************************************************************/


	public void delete(RoutingContext event, String id) {
		logger.info("delete() > enter " +  id);
		
		String imageID = event.request().getParam("imageID");
		String fileName = event.request().getParam("file");
		mongo.removeDocuments(table, Model.findById(imageID), res ->{
			
			if(res.succeeded()){
				returnOk(event, "image.deleted");
				
				String file = imageFolder +"/" + id + "/" + fileName ;	
				FileSystem fs = event.vertx().fileSystem();
				fs.delete(file, fres->{
					if(!fres.succeeded()){
						logger.error("delete() > Could not delete from file system !" + file);
					}
				});
				
			
				
			} else {
				logger.error("delete() > Could not delete from mongo!");
				returnError(event, 405);
			}
			
		});
		

		
	}
}
