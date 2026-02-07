package org.tus.shortlink.identity.service.impl;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tus.common.domain.redis.CacheService;
import org.tus.shortlink.base.biz.UserInfoDTO;
import org.tus.shortlink.base.common.constant.RedisCacheConstant;
import org.tus.shortlink.identity.service.IdentityService;

/**
 * Identity Service Implementation
 * <p>Extract from Gateway's UserContextGatewayFilter and Admin's AdminUserInfoResolver.
 * This implementation centralizes token validation and user resolution logic.
 *
 * <p>Current implementation uses Redis session lookup:
 * <ol>
 *     <li>Token -> Username mapping: short-link:token-to-username:{token}</li>
 *     <li>Username -> User session: short-link:login:{username} (Hash with token as field)
 *     </li>
 * </ol>
 *
 * <p>TODO: Future enhancements:
 * <ul>
 *     <li>Add JWT token support</li>
 *     <li>Add UUID token support</li>
 *     <li>Add token type detection</li>
 *     <li>Add token revocation check</li>
 *     <li>Add database fallback</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityServiceImpl implements IdentityService {
    private final CacheService cacheService;

    @Override
    public UserInfoDTO validateToken(String token) {
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
                log.debug("No username found for token (token maybe invalid or expired). " +
                        "Token key: {}", tokenToUsernameKey);
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
     * Helper method to extract user info from Redis session hash.
     * This method requires the username to be known.
     */
    private UserInfoDTO getFromRedisSession(String username, String token) {
        try {
            String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
            log.debug("Looking up user session in Redis. Key: {}, Field: {}", loginKey, token);
            String userJson = cacheService.hget(loginKey, token, String.class);

            if (userJson != null && !userJson.isBlank()) {
                // Parse user JSON (only fields we need: id, username, realName)
                UserDTO user = JSON.parseObject(userJson, UserDTO.class);
                if (user != null && user.getUsername() != null) {
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
     * Simple DTO for deserializing User entity from Redis.
     * Only includes fields needed for UserInfoDTO.
     * This avoids Identity module depending on admin module.
     */
    @lombok.Data
    private static class UserDTO {
        @com.alibaba.fastjson2.annotation.JSONField(name = "id")
        private Long id;

        private String username;

        private String realName;
    }
}
