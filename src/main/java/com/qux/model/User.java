package com.qux.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;


public class User extends Model implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -9119653686209363893L;

	public static final String USER = "user";
	
	public static final String GUEST = "guest";

	public static final String STATUS_RETIRED = "retired";

	public static final User GUEST_USER = new User("-1", "Guest", "Guest", "guest@quant-ux.com", GUEST );
	

	private String name;
	
	private String lastname;
	
	private String email = "guest@guest.com";

	private String role = "guest";

	private String tel;

	private String image;
	
	private String about;
	
	private String homepage;

	private String facebook;
	
	private String status;
		
	private long lastlogin = 0;
	
	private long lastNotification = 0;
	
	private String password;
	
	private boolean tos;
	
	private String[] has;
	
	private long paidUntil;

	private boolean acceptedGDPR = false;
	
	private String domain = "";

	public User(){
		
	}
	
	public User(String id){
		this._id = id;
	}

	public User(String id, String name, String lastname, String email, String role){
		this._id = id;
		this.name = name;
		this.name = name;
		this.email = email;
		this.role = role;
	}

	public static User formSession(RoutingContext event){
		Session session = event.session();
		
		if(session!=null && session.get("session.user")!=null){
			return session.get("session.user");
		} 
		return User.GUEST_USER;
	}
	
	/*********************************************************************
	 *  Getters and Setters
	 *********************************************************************/
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLastname() {
		return lastname;
	}

	public void setLastname(String lastname) {
		this.lastname = lastname;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getRole() {
		if (role == null) {
			return GUEST;
		}
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}



	public String getTel() {
		return tel;
	}

	public void setTel(String tel) {
		this.tel = tel;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public String getAbout() {
		return about;
	}

	public void setAbout(String about) {
		this.about = about;
	}

	public String getHomepage() {
		return homepage;
	}

	public void setHomepage(String homepage) {
		this.homepage = homepage;
	}

	public String getFacebook() {
		return facebook;
	}

	public void setFacebook(String facebook) {
		this.facebook = facebook;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public long getCreated() {
		return created;
	}

	public void setCreated(long created) {
		this.created = created;
	}

	public long getLastlogin() {
		return lastlogin;
	}

	public void setLastlogin(long lastlogin) {
		this.lastlogin = lastlogin;
	}

	@Override
	public String toString() {
		return "User [id=" + getId() + ", email=" + email + ", role=" + role + "]";
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public boolean getTos() {
		return tos;
	}

	public void setTos(boolean tos) {
		this.tos = tos;
	}
	
	public String[] getHas() {
		return has;
	}

	public void setHas(String[] has) {
		this.has = has;
	}
	
	public long getLastNotification() {
		return lastNotification;
	}

	public void setLastNotification(long lastNotification) {
		this.lastNotification = lastNotification;
	}

	public long getPaidUntil() {
		return paidUntil;
	}

	public void setPaidUntil(long paidUntil) {
		this.paidUntil = paidUntil;
	}

	@JsonIgnore
	public boolean isGuest() {
		return User.GUEST.equals(getRole());
	}
	@JsonIgnore
	public boolean isUser() {
		return User.USER.equals(getRole());
	}

	public boolean isAcceptedGDPR() {
		return acceptedGDPR;
	}

	public void setAcceptedGDPR(boolean acceptedGDPR) {
		this.acceptedGDPR = acceptedGDPR;
	}
	
	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}


	
	
	
	/*********************************************************************
	 *  ACL stuff
	 *********************************************************************/


	public boolean hasRole(String role){
		
		/**
		 * Guest is the lowest role. users and guests have it
		 */
		if(role == User.GUEST){
			return User.GUEST.equals(this.role) || User.USER.equals(this.role);
		}
		
		if(role == User.USER){
			return User.USER.equals(this.role) ;
		}
		
		return false;
	}
	
	
	/*********************************************************************
	 *  Queries
	 *********************************************************************/

	
	public static JsonObject findByEmail(String email){
		 return new JsonObject()
	    	.put("email", email.toLowerCase());
	}


}
