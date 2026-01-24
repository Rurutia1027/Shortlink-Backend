package org.tus.shortlink.base.biz.filter;

import org.tus.shortlink.base.biz.UserInfoDTO;

/**
 * Strategy interface for resolving user information from token.
 * Each module (admin, shortlink) can provide its own implementation
 * based on its specific Redis/DB/Spring Security configuration.
 *
 * <p>Implementation priority:
 * <ol>
 *     <li>SecurityContext (if Spring Security enabled)</li>
 *     <li>JWT token parsing</li>
 *     <li>Redis session lookup</li>
 *     <li>Database query (fallback)</li>
 * </ol>
 */
public interface UserInfoResolver {
    /**
     * Resolver user information from token.
     * <p>The implementation should check multiple sources in priority order:
     * <ul>
     *     <li>If Spring Security is enabled, check SecurityContext first</li>
     *     <li>If JWT support is enabled, parse JWT token</li>
     *     <li>If Redis is available, lookup session from Redis</li>
     *     <li>Fallback to database query if needed</li>
     * </ul>
     *
     * @param token authentication token (JWT, UUID, or session ID)
     * @return UserInfoDTO if found, null otherwise
     */
    UserInfoDTO resolveUserInfo(String token);
}
