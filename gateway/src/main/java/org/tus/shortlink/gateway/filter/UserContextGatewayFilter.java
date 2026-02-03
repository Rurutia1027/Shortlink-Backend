package org.tus.shortlink.gateway.filter;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import org.tus.common.domain.redis.CacheService;
import org.tus.shortlink.base.biz.UserInfoDTO;
import org.tus.shortlink.base.common.constant.RedisCacheConstant;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global Gateway Filter for user context resolution.
 * 
 * <p>This filter extracts user information from token and adds it to request headers
 * for downstream services. This centralizes user authentication at the Gateway level.
 * 
 * <p>Token extraction priority:
 * <ol>
 *     <li>Authorization header: "Bearer &lt;token&gt;"</li>
 *     <li>Cookie: "token" cookie</li>
 *     <li>Query parameter: "token" (for backward compatibility)</li>
 * </ol>
 * 
 * <p>User information is added to request headers:
 * <ul>
 *     <li>X-User-Id: User ID</li>
 *     <li>X-Username: Username</li>
 *     <li>X-Real-Name: User real name</li>
 * </ul>
 * 
 * <p>Downstream services can use UserTransmitFilter (from base module) to read
 * these headers and set UserContext.
 */
@Slf4j
@Component
public class UserContextGatewayFilter implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer";
    private static final String TOKEN_COOKIE_NAME = "token";
    private static final String TOKEN_PARAMETER_NAME = "token";
    
    // Headers to pass user info to downstream services
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USERNAME_HEADER = "X-Username";
    private static final String REAL_NAME_HEADER = "X-Real-Name";

    private final CacheService cacheService;

    public UserContextGatewayFilter(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Extract token from request
        String token = extractTokenFromRequest(request);
        
        if (StrUtil.isNotBlank(token)) {
            log.debug("Token extracted from request: {} (length: {})", 
                token.substring(0, Math.min(10, token.length())) + "...", token.length());
            
            // Resolve user information from token
            UserInfoDTO userInfo = resolveUserInfo(token);
            
            if (userInfo != null) {
                log.debug("User context resolved: {} (userId: {})", 
                    userInfo.getUsername(), userInfo.getUserId());
                
                // Add user information to request headers for downstream services
                ServerHttpRequest modifiedRequest = request.mutate()
                    .header(USER_ID_HEADER, userInfo.getUserId() != null ? userInfo.getUserId() : "")
                    .header(USERNAME_HEADER, userInfo.getUsername() != null ? userInfo.getUsername() : "")
                    .header(REAL_NAME_HEADER, userInfo.getRealName() != null ? userInfo.getRealName() : "")
                    .build();
                
                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            } else {
                log.debug("No user information found for token. Token may be invalid or expired.");
            }
        } else {
            log.debug("No token found in request. Request URI: {}, Method: {}", 
                request.getURI().getPath(), request.getMethod());
        }
        
        // Continue filter chain even if no token/user found
        // Downstream services can handle unauthenticated requests as needed
        return chain.filter(exchange);
    }

    /**
     * Extract token from request
     * Priority: Authorization header > Cookie > Query parameter
     */
    private String extractTokenFromRequest(ServerHttpRequest request) {
        // 1. Try Authorization header
        HttpHeaders headers = request.getHeaders();
        String authHeader = headers.getFirst(AUTHORIZATION_HEADER);
        if (StrUtil.isNotBlank(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }

        // 2. Try Cookie
        List<String> cookies = headers.get(HttpHeaders.COOKIE);
        if (cookies != null && !cookies.isEmpty()) {
            for (String cookieHeader : cookies) {
                String[] cookiePairs = cookieHeader.split(";");
                for (String cookiePair : cookiePairs) {
                    String[] parts = cookiePair.trim().split("=", 2);
                    if (parts.length == 2 && TOKEN_COOKIE_NAME.equals(parts[0].trim())) {
                        String token = parts[1].trim();
                        if (StrUtil.isNotBlank(token)) {
                            return token;
                        }
                    }
                }
            }
        }

        // 3. Try Query parameter (for backward compatibility)
        String tokenParam = request.getQueryParams().getFirst(TOKEN_PARAMETER_NAME);
        if (StrUtil.isNotBlank(tokenParam)) {
            log.warn("Token extracted from query parameter, consider using Authorization header or cookie");
            return tokenParam;
        }

        return null;
    }

    /**
     * Resolve user information from token using Redis session lookup
     */
    private UserInfoDTO resolveUserInfo(String token) {
        if (token == null || token.isBlank()) {
            log.debug("Token is null or blank!");
            return null;
        }

        try {
            // Step 1: Get username from reverse mapping (token -> username)
            String tokenToUsernameKey = RedisCacheConstant.TOKEN_TO_USERNAME_KEY + token;
            log.debug("Looking up username for token key: {}", tokenToUsernameKey);
            String username = cacheService.get(tokenToUsernameKey, String.class);

            if (username == null || username.isBlank()) {
                log.debug("No username found for token (token may be invalid or expired). Token key: {}", 
                    tokenToUsernameKey);
                return null;
            }
            
            log.debug("Found username: {} for token", username);
            
            // Step 2: Get user info from session hash (username -> {token: user_json})
            return getFromRedisSession(username, token);
        } catch (Exception e) {
            log.error("Error resolving user info from token: {}", token, e);
            return null;
        }
    }

    /**
     * Helper method to extract user info from Redis session hash
     */
    private UserInfoDTO getFromRedisSession(String username, String token) {
        try {
            String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
            log.debug("Looking up user session in Redis. Key: {}, Field: {}", loginKey, token);
            String userJson = cacheService.hget(loginKey, token, String.class);

            if (userJson != null && !userJson.isBlank()) {
                User user = JSON.parseObject(userJson, User.class);
                if (user != null) {
                    log.debug("Successfully retrieved user from Redis session: {}", user.getUsername());
                    return UserInfoDTO.builder()
                            .userId(user.getId() != null ? user.getId().toString() : null)
                            .username(user.getUsername())
                            .realName(user.getRealName())
                            .build();
                } else {
                    log.warn("Failed to parse user JSON from Redis session. UserJson: {}", userJson);
                }
            } else {
                log.debug("No user session found in Redis for username: {}, token: {}", username, token);
            }
        } catch (Exception e) {
            log.error("Failed to parse user info from Redis session for username: {}, token: {}", 
                username, token, e);
        }
        return null;
    }

    /**
     * Set filter order to run early in the filter chain
     * Lower order values have higher priority
     */
    @Override
    public int getOrder() {
        // Run before rate limiting and other filters, but after request logging
        return -100;
    }
}
