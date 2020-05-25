package com.qux.model;


public class Rating extends Model{
	

	
	private long userID;
	
	private long appID;
	
	private int value;
	
	private long created;

	
	public long getUserID() {
		return userID;
	}

	public void setUserID(long userID) {
		this.userID = userID;
	}

	public long getAppID() {
		return appID;
	}

	public void setAppID(long appID) {
		this.appID = appID;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}

	public long getCreated() {
		return created;
	}

	public void setCreated(long created) {
		this.created = created;
	}

	@Override
	public String toString() {
		return "Rating [userID=" + userID + ", appID=" + appID + ", value="
				+ value + "]";
	}
	

}
