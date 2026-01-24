package org.tus.shortlink.base.common.constant;

public class HttpAuthConstants {
    private HttpAuthConstants() {}

    // Core auth headers
    public static final String AUTHORIZATION = "Authorization";
    public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    public static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";

    // Authentication schemes
    public static final String BEARER = "Bearer";
    public static final String BASIC = "Basic";
    public static final String DIGEST = "Digest";
    public static final String NEGOTICATE = "Negotiate";
}
