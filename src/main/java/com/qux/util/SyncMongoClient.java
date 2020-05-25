package com.qux.util;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.qux.model.Model;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class SyncMongoClient {

	private final MongoClient client;
	
	public JSONMapper mapper = new JSONMapper();
	
	public SyncMongoClient(MongoClient c){
		this.client =c;
	}
	

	public String save(String name, Model model){
		String id =  save(name, mapper.toVertx(model));
		if(id!=null)
			model.setId(id);
		return id;
	}
	
	public String save(String name, JsonObject query){
		//System.out.println("SyncMongo.save() " + query);
		
		CountDownLatch latch = new CountDownLatch(1);
		String[] result = new String[1];
		client.save(name, query, new Handler<AsyncResult<String>>() {
			
			@Override
			public void handle(AsyncResult<String> event) {
				if(event.succeeded()){
					result[0] = event.result();
				} else {
					System.err.println("SyncMongo.save() " +  query);
					event.cause().printStackTrace();
				}
			
				latch.countDown();
			}
		});
		
		try {
			latch.await();
		} catch (InterruptedException e) {

		}
		
		
		return result[0];
		
	}
	
	
	public String update(String name, String id, JsonObject query){
		
		CountDownLatch latch = new CountDownLatch(1);
		String[] result = new String[1];
		client.updateCollection(name, new JsonObject().put("_id", id), query, event ->{
			if(event.succeeded()){
				result[0] = "ok";
			} else {
				System.err.println("SyncMongo.save() " +  query);
				event.cause().printStackTrace();
			}
		
			latch.countDown();
		});
		
		try {
			latch.await();
		} catch (InterruptedException e) {

		}
		
		
		return result[0];
		
	}
	
	
	public String insert(String name, Model model){		
		String id =  insert(name, mapper.toVertx(model));
		model.setId(id);
		return id;
	}
	
	public String insert(String name, JsonObject query){
	//	System.out.println("SyncMongo.insert() " + query);
		
		CountDownLatch latch = new CountDownLatch(1);
		String[] result = new String[1];
		client.insert(name, query, new Handler<AsyncResult<String>>() {
			
			@Override
			public void handle(AsyncResult<String> event) {
				if(event.succeeded()){
					result[0] = event.result();
				} else {
					System.err.println("SyncMongo.insert() " +  query);
					event.cause().printStackTrace();
				}
			
				latch.countDown();
			}
		});
		
		try {
			latch.await();
		} catch (InterruptedException e) {

		}
		
		
		return result[0];
		
	}
	
	@SuppressWarnings("unchecked")
	public List<JsonObject> find(String name, JsonObject query){
		
	
		CountDownLatch latch = new CountDownLatch(1);
		Object[] result = new Object[1];
		client.find(name, query, new Handler<AsyncResult<List<JsonObject>>>() {
			@Override
			public void handle(AsyncResult<List<JsonObject>> event) {
				if(event.succeeded()){
					result[0] = event.result();
				} else {
					event.cause().printStackTrace();
				}
				latch.countDown();
			}
		});
		
		try {
			latch.await();
		} catch (InterruptedException e) {

		}
		
		return (List<JsonObject>) result[0];
		
	}
	
	public JsonObject findOne(String name, JsonObject query){
		
	//	System.out.println("SyncMongo.findOne() " + query);
		CountDownLatch latch = new CountDownLatch(1);
		Object[] result = new Object[1];
		client.findOne(name, query, null, new Handler<AsyncResult<JsonObject>>() {
			@Override
			public void handle(AsyncResult<JsonObject> event) {
				if(event.succeeded()){
					result[0] = event.result();
				} else {
					event.cause().printStackTrace();
				}
				latch.countDown();
			}
		});
		
		try {
			latch.await();
		} catch (InterruptedException e) {

		}
		
		return (JsonObject) result[0];
		
	}
	
	public long count(String name, JsonObject query){

		CountDownLatch latch = new CountDownLatch(1);
		long[] result = new long[1];
		client.count(name, query, new Handler<AsyncResult<Long>>() {
			@Override
			public void handle(AsyncResult<Long> event) {
				if(event.succeeded()){
					result[0] = event.result();
				}
				latch.countDown();
			}
		});
		
		try {
			latch.await();
		} catch (InterruptedException e) {

		}
		System.out.println("SyncMongo.count() " + name + " " + query + " : " + result[0]);
		return  result[0];
		
	}
	
	
	public boolean remove(String name, JsonObject query){
		CountDownLatch latch = new CountDownLatch(1);
		boolean[] result = new boolean[1];
		client.removeDocuments(name, query, event -> {
			if(event.succeeded()){
				result[0] = true;
			} else {
				result[1]  =false;
			}
			latch.countDown();
		});
		
		try {
			latch.await();
		} catch (InterruptedException e) {

		}
		
		return  result[0];
		
	}
}
