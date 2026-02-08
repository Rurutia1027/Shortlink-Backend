package org.tus.shortlink.identity.service;

import org.tus.shortlink.base.biz.UserInfoDTO;
import org.tus.shortlink.identity.enums.TokenType;

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


    /**
     * Generate a token for a user
     *
     * <p>Generates either a JWT or UUID token based on the specified type.
     * The token is stored in the database for revocation tracking.
     *
     * @param userInfo     User information to include in token
     * @param tokenType    Type of token to generate (JWT or UUID)
     * @param expirationMs Expiration time in milliseconds (null for no expiration)
     * @return Generate token string
     */
    String generateToken(UserInfoDTO userInfo, TokenType tokenType, Long expirationMs);

    /**
     * Generate a token with default expiration
     *
     * @param userInfo  User information
     * @param tokenType Type of token to generate
     * @return Generated token string
     */
    default String generateToken(UserInfoDTO userInfo, TokenType tokenType) {
        return generateToken(userInfo, tokenType, null);
    }

    /**
     * Revoke a token
     *
     * @param token Token string to revoke
     * @return true if token was revoked, false if token not found or already revoked
     */
    boolean revokeToken(String token);

    /**
     * Revoke all tokens for a user
     *
     * @param userId User ID
     * @return Number of tokens revoked
     */
    int revokeAllUserTokens(String userId);
}
