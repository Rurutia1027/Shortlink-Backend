package org.tus.shortlink.identity.client;

import org.tus.shortlink.base.biz.UserInfoDTO;

/**
 * Identity Client Interface
 *
 * <p>Client interface for accessing Identity Service.
 * This interface allows Gateway and other modules to call Identity Service without
 * directly depending on Identity Service implementation.
 *
 * <p>Current implementation: In-process call (same JVM)
 * Future implementation: HTTP/gRPC client for service-to-service communication
 *
 * <p>Usage
 * <ul>
 *     <li>Gateway: Call validateToken() to resolve user form token</li>
 *     <li>Admin/Shortlink modules: Can use this client for token validation</li>
 * </ul>
 */
public interface IdentityClient {
    /**
     * Validate token and resolve user information.
     *
     * <p>This method delegates to IdentityService.validateToken().
     * In the future, this cna be replaced with HTTP/gRPC client calls when Identity Service
     * is deployed as a separate service.
     *
     * @param token Authentication token
     * @return userInfoDTO if token is valid, null otherwise
     */
    UserInfoDTO validateToken(String token);
}
