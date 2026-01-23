package org.tus.shortlink.admin.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tus.shortlink.base.biz.UserInfoDTO;
import org.tus.shortlink.base.biz.filter.UserInfoResolver;

/**
 * Admin module implementation of UserInfoResolver.
 *
 * <p>Resolves user information from token using the following priority:
 * <ol>
 *     <li>SecurityContext (if Spring Security Filter already processed the request)</li>
 *     <li>JWT token parsing (if JWT support is enabled)</li>
 *     <li>Redis session lookup (when Redis module is ready)</li>
 *     <li>Database query (fallback)</li>
 * </ol>
 *
 * <p>This implementation will be enhanced as we add:
 * - Redis module integration
 * - JWT token support
 * - Spring Security integration
 */
@Slf4j
@RequiredArgsConstructor
public class AdminUserInfoResolver implements UserInfoResolver {

    // TODO: Integrates Spring Security (when Spring Security is added)
    // Check SecurityContextHolder.getContext().getAuthentication() first

    // TODO: Integrate JWT Token Provider (when JWT support is added)
    // private final JwtTokenProvider jwtTokenProvider;

    //
    // TODO: Integrate Redis Cache Service (when Redis module is ready)
    // private final CacheService cacheService;

    // TODO: Integrate User Service (for fallback database query)
    // private final UserService userService;

    @Override
    public UserInfoDTO resolverUserInfo(String token) {
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

        // 3. Fallback: Redis session lookup (when Redis module is ready)
        // TODO: Implement when Redis module is ready
        // String sessionKey = RedisCacheConstant.USER_LOGIN_KEY + username;
        // Map<Object, Object> sessionData = cacheService.getHash(sessionKey, token);
        // if (sessionData != null && !sessionData.isEmpty()) {
        //     String userInfoJson = (String) sessionData.get(token);
        //     if (StrUtil.isNotBlank(userInfoJson)) {
        //         try {
        //             User user = JSON.parseObject(userInfoJson, User.class);
        //             return UserInfoDTO.builder()
        //                     .userId(user.getId())
        //                     .username(user.getUsername())
        //                     .realName(user.getRealName())
        //                     .build();
        //         } catch (Exception e) {
        //             log.error("Failed to parse user info from session: {}", userInfoJson, e);
        //         }
        //     }
        // }

        // 4. Fallback: Database query (if Redis cache miss)
        // TODO: Implement when needed
        // This requires extracting username from token first (if token contains username)
        // For UUID-based tokens, we need to query database to find user by token
        // For JWT tokens, we can extract username directly from token payload

        // For now, return null (will be implemented when Redis module is ready)
        log.debug("AdminUserInfoResolver.resolveUserInfo not implemented yet, waiting for Redis module");
        return null;
    }

    // TODO: Helper methods to be implemented
    // private UserInfoDTO extractFromSecurityContext(Authentication auth) { ... }
    // private UserInfoDTO extractFromJwtClaims(String token) { ... }
    // private UserInfoDTO getFromRedisSession(String token) { ... }
    // private UserInfoDTO queryFromDatabase(String token) { ... }
}
