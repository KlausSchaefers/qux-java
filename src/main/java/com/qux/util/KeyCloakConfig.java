package com.qux.util;

public class KeyCloakConfig {

    private String server, realm, issuer, claimId, claimEmail, claimName, claimLastName, claimRole;

    public void setServer(String server) {
        this.server = server;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public void setClaimId(String claimId) {
        this.claimId = claimId;
    }

    public String getClaimEmail() {
        return claimEmail;
    }

    public void setClaimEmail(String claimEmail) {
        this.claimEmail = claimEmail;
    }

    public void setClaimName(String claimName) {
        this.claimName = claimName;
    }

    public void setClaimLastName(String claimLastName) {
        this.claimLastName = claimLastName;
    }

    public void setClaimRole(String claimRole) {
        this.claimRole = claimRole;
    }

    public KeyCloakConfig() {
    }

    public String getServer() {
        return server;
    }

    public String getRealm() {
        return realm;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getClaimId() {
        return claimId;
    }

    public String getClaimName() {
        return claimName;
    }

    public String getClaimLastName() {
        return claimLastName;
    }

    public String getClaimRole() {
        return claimRole;
    }
}
