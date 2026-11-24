package com.qux.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;


public class User extends Model implements Serializable {

	@Serial
    private static final long serialVersionUID = -9119653686209363893L;

	public static final List<String> FIELD_TOS = List.of("acceptedAI", "acceptedTOS", "acceptedPrivacy", "acceptedGDPR");

	public static final String FIELD_AI_USAGE = "aiUsage";

	public static final String FIELD_AI_USAGE_TOTAL = "aiUsageTotal";

	public static final String FIELD_NUMBER_OF_HACKS = "hackCount";

	public static final String FIELD_FAIlED_LOGINS = "failedLoginAttempts";

	public static final String FIELD_LOGIN_COUNTS= "loginCount";

	public static final String FIELD_LAST_LOGIN = "lastlogin";

	public static final String FIELD_PASSWORD_RESET_COUNT = "passwordResetCount";

	public static final String FIELD_PASSWORD_RESET_KEY = "passwordRestKey";

	public static Set<String> HIDDEN_FIELDS = Set.of(
			FIELD_NUMBER_OF_HACKS,
			FIELD_LOGIN_COUNTS,
			FIELD_FAIlED_LOGINS,
			FIELD_PASSWORD_RESET_COUNT,
			FIELD_PASSWORD_RESET_KEY
	);




	public static final int AI_USAGE_LIMIT = 100000000;

	public static final int AI_USAGE_DEFAULT_BUDGET = 20;

	public static final int AI_USAGE_MAX_BUDGET = 1000;

	public static final String USER = "user";

	public static final String GUEST = "guest";

	public static final String STAFF = "staff";

	public static final String ADMIN = "admin";

	public static final String TOKEN = "token";

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

	private String plan = "Free";

	private boolean acceptedGDPR = false;

	private String domain = "";


	private String appID = "";

	private int appPermission = -1;

	/*
	 * Set when the user calls with an agent token (see AgentACL)
	 */
	private String agentTokenID;

	// null: all apps the user can access
	private List<String> agentAppIDs;

	private List<String> agentScopes;


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
	public boolean isAdmin() {
		return User.ADMIN.equals(getRole());
	}
	@JsonIgnore
	public boolean isUser() {
		return User.USER.equals(getRole());
	}

	public String getPlan() {
		return plan;
	}

	public void setPlan(String plan) {
		this.plan = plan;
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

	public boolean hasClaim() {
		return TOKEN.equals(this.role) && this.appID.length() > 0 && this.appPermission != -1;
	}

	public boolean hasRole(String role){

		/**
		 * Guest is the lowest role. users and guests have it
		 */
		if(role == User.GUEST){
			return User.GUEST.equals(this.role) || User.USER.equals(this.role) || User.ADMIN.equals(this.role) || User.STAFF.equals(this.role) || User.TOKEN.equals(this.role);
		}

		if(role == User.USER){
			return User.USER.equals(this.role) || User.ADMIN.equals(this.role) || User.STAFF.equals(this.role)  || User.TOKEN.equals(this.role);
		}

		if(role == User.STAFF){
			return User.ADMIN.equals(this.role) || User.STAFF.equals(this.role);
		}

		if(role == User.ADMIN){
			return User.ADMIN.equals(this.role);
		}

		return false;
	}

	public String getAppID() {
		return appID;
	}

	public void setAppID(String appID) {
		this.appID = appID;
	}

	public int getAppPermission() {
		return appPermission;
	}

	public void setAppPermission(int appPermission) {
		this.appPermission = appPermission;
	}

	/*********************************************************************
	 *  Agent tokens
	 *********************************************************************/

	public void setAgent(String tokenID, List<String> appIDs, List<String> scopes) {
		this.agentTokenID = tokenID;
		this.agentAppIDs = appIDs;
		this.agentScopes = scopes;
	}

	/**
	 * Whether the token has the scope (AgentToken.SCOPE_*). The web app can
	 * do everything its ACL allows.
	 */
	public boolean hasAgentScope(String scope) {
		return !isAgent() || (agentScopes != null && agentScopes.contains(scope));
	}

	@JsonIgnore
	public boolean isAgent() {
		return this.agentTokenID != null;
	}

	@JsonIgnore
	public String getAgentTokenID() {
		return agentTokenID;
	}

	@JsonIgnore
	public List<String> getAgentAppIDs() {
		return agentAppIDs;
	}

	public boolean isAppAllowed(String appID) {
		return !isAgent() || agentAppIDs == null || agentAppIDs.contains(appID);
	}


	/*********************************************************************
	 *  Queries
	 *********************************************************************/


	public static JsonObject findByEmail(String email){
		return new JsonObject()
				.put("email", email.toLowerCase());
	}


	public static JsonObject findExpired(){
		return new JsonObject()
				.put("paidUntil", new JsonObject().put("$lt", System.currentTimeMillis()))
				.put("plan",  new JsonObject().put("$ne", "Free"));
	}




}
