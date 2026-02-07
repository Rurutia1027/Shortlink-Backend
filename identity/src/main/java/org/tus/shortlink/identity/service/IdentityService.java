package org.tus.shortlink.identity.service;

import org.tus.shortlink.base.biz.UserInfoDTO;

/**
 * Identity Service Interface
 *
 * <p>Centralized service for user identity resolution and token validation.
 * This service handles authentication logic that was previously scattered across Gateway
 * and admin modules.
 *
 * <p>This service will be extended in the future to support:
 * <ul>
 *     <li>JWT token generation and validation</li>
 *     <li>UUID token generation and validation</li>
 *     <li>OAuth2/OIDC integration</li>
 *     <li>Token revocation</li>
 *     <li>Session management</li>
 * </ul>
 */
public interface IdentityService {
    /**
     * Validate token and resolve user information.
     * <p>Current implementation:
     * <ol>
     *     <li>lookup username from token-to-username mapping in Redis</li>
     *     <li>lookup user info from user session hash in Redis</li>
     * </ol>
     * <p>Future implementation will support:
     * <ul>
     *     <li>JWT token parsing and validation</li>
     *     <li>UUID token validation</li>
     *     <li>Token type detection (automatic)</li>
     *     <li>Token revocation check</li>
     * </ul>
     *
     * @param token Authentication token (UUID, JWT, or session ID)
     * @return userInfoDTO if token is valid and user found, null otherwise
     */
    UserInfoDTO validateToken(String token);
}
