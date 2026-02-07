package org.tus.shortlink.admin.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tus.shortlink.base.biz.UserInfoDTO;
import org.tus.shortlink.base.biz.filter.UserInfoResolver;
import org.tus.shortlink.identity.client.IdentityClient;

/**
 * Admin module implementation of UserInfoResolver.
 *
 * <p>This resolver delegates to Identity Service (via IdentityClient) to resolve
 * user information from token. This centralizes identity logic in the Identity module.
 *
 * <p>Migration from direct Redis access:
 * <ul>
 *     <li>Before: Direct Redis access in AdminUserInfoResolver</li>
 *     <li>After: Delegates to Identity Service via IdentityClient</li>
 * </ul>
 *
 * <p>Future enhancements (handled by Identity Service):
 * <ol>
 *     <li>SecurityContext (if Spring Security Filter already processed the request)</li>
 *     <li>JWT Token parsing (if JWT support is enabled)</li>
 *     <li>UUID Token validation</li>
 *     <li>Redis session lookup (current implementation)</li>
 *     <li>Database query (fallback)</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
public class AdminUserInfoResolver implements UserInfoResolver {
    private final IdentityClient identityClient;


    @Override
    public UserInfoDTO resolveUserInfo(String token) {
        if (token == null || token.isBlank()) {
            log.debug("Token is null or blank!");
            return null;
        }

        // Delegate to Identity Service via IdentityClient
        // Identity Service handles all token validation logic:
        // - Redis session lookup (current)
        // - JWT token parsing (future)
        // - UUID token validation (future)
        // - Database fallback (future)
        log.debug("Delegating token validation to Identity Service");
        return identityClient.validateToken(token);
    }
}
