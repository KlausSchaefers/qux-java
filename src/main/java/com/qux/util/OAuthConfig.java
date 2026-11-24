package com.qux.util;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Where the authorization server lives, for the OAuth metadata and the
 * redirect to the consent page.
 *
 * oauth.issuer (QUX_OAUTH_ISSUER): the public URL of quant-ux, e.g.
 * https://app.quant-ux.com. The frontend server proxies /oauth, /.well-known
 * and /rest to this backend, so the issuer is the URL of the web app and of
 * its consent page. Without it, the URL is taken from the request
 * (X-Forwarded-* of the proxy, else Host).
 */
public class OAuthConfig {

	public static final String ISSUER = Config.OAUTH_ISSUER;

	public static final String ENV_ISSUER = Config.ENV_OAUTH_ISSUER;

	private static String issuer;

	public static void init(JsonObject config) {
		issuer = trim(config.getString(ISSUER, ""));
	}

	public static String getIssuer(RoutingContext event) {
		if (issuer != null && !issuer.isEmpty()) {
			return issuer;
		}
		String proto = event.request().getHeader("X-Forwarded-Proto");
		String host = event.request().getHeader("X-Forwarded-Host");
		if (host == null || host.isEmpty()) {
			host = event.request().host();
		}
		if (proto == null || proto.isEmpty()) {
			proto = event.request().isSSL() ? "https" : "http";
		}
		// a proxy may send a list, the first one is the client facing one
		return proto.split(",")[0].trim() + "://" + host.split(",")[0].trim();
	}

	private static String trim(String url) {
		if (url == null) {
			return "";
		}
		url = url.trim();
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}
}
