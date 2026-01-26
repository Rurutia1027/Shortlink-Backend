package org.tus.shortlink.admin.filter;

import com.alibaba.fastjson2.JSON;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tus.common.domain.redis.CacheService;
import org.tus.shortlink.admin.entity.User;
import org.tus.shortlink.base.biz.UserInfoDTO;
import org.tus.shortlink.base.biz.filter.UserInfoResolver;
import org.tus.shortlink.base.common.constant.RedisCacheConstant;

/**
 * Admin module implementation of UserInfoResolver.
 *
 * <p>Resolves user information from token using the following priority:</p>
 * <ol>
 *     <li>SecurityContext (if Spring Security Filter already processed the request)</li>
 *     <li>JWT Token parsing (if JWT support is enabled) </li>
 *     <li>Redis session lookup (when Redis module is ready)</li>
 *     <li>Database query (fallback)</li>
 *     <li>Redis session lookup (implemented)</li>
 *     <li>Database query (fallback - not implemented)</li>
 * </ol>
 * <p>Current implementation</p>
 * - Redis session lookup: Looks up user session stored in Redis with
 * > Key: USER_LOGIN_KEY + username,
 * > Field: token,
 * > Value: JSON(User)
 */
@Slf4j
@RequiredArgsConstructor
public class AdminUserInfoResolver implements UserInfoResolver {
    private final CacheService cacheService;


    // TODO: Integrates Spring Security (when Spring Security is added)
    // Check SecurityContextHolder.getContext().getAuthentication() first

    // TODO: Integrate JWT Token Provider (when JWT support is added)
    // private final JwtTokenProvider jwtTokenProvider;

    // TODO: Integrate User Service (for fallback database query)
    // private final UserService userService;

    @Override
    public UserInfoDTO resolveUserInfo(String token) {
        if (token == null || token.isBlank()) {
            log.debug("Token is null or blank!");
            return null;
        }

        // 1. Priority: Check SecurityContext (if Spring Security Filter already processed)
        // TODO: Implement when Spring Security is added
        // Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
        //     CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        //     return UserInfoDTO.builder()
        //             .userId(userDetails.getUserId())
        //             .username(userDetails.getUsername())
        //             .realName(userDetails.getRealName())
        //             .build();
        // }

        // 2. Fallback: JWT token parsing (if JWT support is enabled)
        // TODO: Implement when JWT support is added
        // if (jwtTokenProvider != null && jwtTokenProvider.isJwtToken(token)) {
        //     if (jwtTokenProvider.validateToken(token)) {
        //         return extractFromJwtClaims(token);
        //     }
        // }

        // 3. Redis session lookup
        // Step 1: Get username from reverse mapping (token -> username)
        // Step 2: Get user info from session hash (username -> {token: use_token})
        try {
            String tokenToUsenameKey = RedisCacheConstant.TOKEN_TO_USERNAME_KEY + token;
            String username = cacheService.get(tokenToUsenameKey, String.class);

            if (username == null || username.isBlank()) {
                log.debug("No username found for token (token may be invalid for expired)");
                return null;
            }
            // Now we have username, get user info from session hash
            return getFromRedisSession(username, token);
        } catch (Exception e) {
            log.error("Error resolving user info from token", e);
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
            String userJson = cacheService.hget(loginKey, token, String.class);

            if (userJson != null && !userJson.isBlank()) {
                User user = JSON.parseObject(userJson, User.class);
                if (user != null) {
                    return UserInfoDTO.builder()
                            .userId(user.getId() != null ? user.getId().toString() : null)
                            .username(user.getUsername())
                            .realName(user.getRealName())
                            .build();
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse user info from Redis session for username: {}, token:" +
                    " {}", username, token, e);
        }
        return null;
    }

    // TODO: Helper methods to be implemented
    // private UserInfoDTO extractFromSecurityContext(Authentication auth) { ... }
    // private UserInfoDTO extractFromJwtClaims(String token) { ... }
    // private UserInfoDTO getFromRedisSession(String token) { ... }
    // private UserInfoDTO queryFromDatabase(String token) { ... }
}
