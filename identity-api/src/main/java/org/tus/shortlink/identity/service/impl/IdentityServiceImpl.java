package org.tus.shortlink.identity.service.impl;

import com.alibaba.fastjson2.JSON;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tus.common.domain.dao.HqlQueryBuilder;
import org.tus.common.domain.persistence.QueryService;
import org.tus.common.domain.redis.CacheService;
import org.tus.shortlink.base.biz.UserInfoDTO;
import org.tus.shortlink.base.common.constant.RedisCacheConstant;
import org.tus.shortlink.identity.entity.Token;
import org.tus.shortlink.identity.enums.TokenType;
import org.tus.shortlink.identity.service.IdentityService;
import org.tus.shortlink.identity.util.JwtUtil;
import org.tus.shortlink.identity.util.TokenTypeDetector;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Identity Service Implementation
 *
 * <p>Centralized service for user identity resolution and token validation.
 * Supports both JWT and UUID token types with database-backed storage.
 *
 * <p>Token Validation Flow:
 * <ol>
 *     <li>Detect token type (JWT or UUID) using TokenTypeDetector</li>
 *     <li>For JWT: Validate signature, expiration, check revocation in database</li>
 *     <li>For UUID: Lookup in database, check revocation and expiration</li>
 *     <li>Fallback to Redis lookup for backward compatibility</li>
 * </ol>
 *
 * <p>Token Generation:
 * <ul>
 *     <li>JWT: Self-contained token with claims, stored in database for revocation</li>
 *     <li>UUID: Random UUID string, stored in database</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityServiceImpl implements IdentityService {
    private final CacheService cacheService;
    private final QueryService queryService;

    /**
     * JWT secret key (configured via properties)
     */
    @Value("${identity.jwt.secret:your-256-bit-secret-key-change-this-in-production-minimum-32-characters}")
    private String jwtSecret;

    /**
     * Default token expiration: 24 hours (in milliseconds)
     */
    @Value("${identity.token.expiration-ms:86400000}")
    private Long defaultExpirationMs;

    @Override
    public UserInfoDTO validateToken(String token) {
        if (token == null || token.isBlank()) {
            log.debug("Token is null or blank!");
            return null;
        }

        // Remove Bearer prefix if present
        String cleanToken = token.trim();
        if (cleanToken.startsWith("Bearer ")) {
            cleanToken = cleanToken.substring(7).trim();
        }

        // Step 1: Detect token type
        TokenType tokenType = TokenTypeDetector.detect(cleanToken);

        if (tokenType == null) {
            log.debug("Unable to detect token type, falling back to Redis lookup");
            return validateToken(cleanToken);
        }

        // Step 2: Validate based on token type
        switch (tokenType) {
            case JWT:
                return validateJwtToken(cleanToken);
            case UUID:
                return validateUuidToken(cleanToken);
            default:
                log.warn("Unsupported token type: {}", tokenType);
                return validateTokenFromRedis(cleanToken);
        }
    }


    @Override
    @Transactional
    public String generateToken(UserInfoDTO userInfo, TokenType tokenType, Long expirationMs) {
        if (userInfo == null || userInfo.getUserId() == null) {
            throw new IllegalArgumentException("UserInfoDTO and userId must not be null");
        }
        if (tokenType == null) {
            throw new IllegalArgumentException("TokenType must not be null");
        }

        Date now = new Date();
        Date expiryDate = expirationMs != null ? new Date(now.getTime() + expirationMs) :
                null;
        String tokenValue;
        Token token = Token.builder()
                .tokenType(tokenType.name())
                .userId(userInfo.getUserId())
                .username(userInfo.getUsername())
                .issuedAt(now)
                .expiresAt(expiryDate)
                .build();

        switch (tokenType) {
            case JWT:
                // Generate JWT token
                tokenValue = JwtUtil.generateToken(userInfo, jwtSecret, expirationMs);

                // Extract JWT claims for database storage
                Claims claims = JwtUtil.validateAndParseToken(tokenValue, jwtSecret);
                if (claims != null) {
                    token.setJwtId(claims.getId());
                    token.setJwtSubject(claims.getSubject());
                    token.setJwtIssuer(claims.getIssuer());
                    token.setJwtAudience(claims.getAudience() != null && !claims.getAudience().isEmpty()
                            ? claims.getAudience().iterator().next()
                            : null);
                }
                break;
            case UUID:
                // Generate UUID token
                tokenValue = UUID.randomUUID().toString();
                break;

            default:
                throw new IllegalArgumentException("Unsupported token type:" + tokenType);
        }

        // Set token value and save to database
        token.setTokenValue(tokenValue);
        queryService.save(token);

        log.info("Generated {} token for user: {} (userId: {})",
                tokenType, userInfo.getUsername(), userInfo.getUserId());
        return tokenValue;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            // Remove Bearer prefix if present
            String cleanToken = token.trim();
            if (cleanToken.startsWith("Bearer ")) {
                cleanToken = cleanToken.substring(7).trim();
            }

            // Try to find token in database
            Token dbToken = findTokenByTokenValue(cleanToken);

            // If not found by value, try JWT ID for JWT tokens
            if (dbToken == null && TokenTypeDetector.isJWT(cleanToken)) {
                String jwtId = JwtUtil.extractJwtId(cleanToken, jwtSecret);
                if (jwtId != null) {
                    dbToken = findTokenByJwtId(jwtId);
                }
            }

            if (dbToken != null) {
                if (dbToken.isRevoked()) {
                    log.debug("Token already revoked: {}", cleanToken.substring(0, Math.min(20, cleanToken.length())));
                    return false;
                }
                dbToken.revoke();
                queryService.save(dbToken);
                log.info("Token revoked successfully: {}", cleanToken.substring(0, Math.min(20, cleanToken.length())));
                return true;
            } else {
                log.debug("Token not found in database: {}", cleanToken.substring(0, Math.min(20, cleanToken.length())));
                return false;
            }
        } catch (Exception e) {
            log.error("Error revoking token", e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int revokeAllUserTokens(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }

        try {
            List<Token> tokens = findValidTokensByUserId(userId);
            Date now = new Date();
            int revokedCount = 0;
            for (Token token : tokens) {
                token.setRevokedAt(now);
                queryService.save(token);
                revokedCount++;
            }

            log.info("Revoked {} tokens from user: {}", revokedCount, userId);
            return revokedCount;
        } catch (Exception e) {
            log.error("Error revoking all tokens for user: {}", userId, e);
            return 0;
        }
    }

    // -- private methods --


    /**
     * Validate JWT token
     */
    private UserInfoDTO validateJwtToken(String token) {
        try {
            // Step 1: Validate JWT signature and expiration
            Claims claims = JwtUtil.validateAndParseToken(token, jwtSecret);
            if (claims == null) {
                log.debug("JWT token validation failed (invalid signature or expired)");
                return null;
            }

            // Step 2: Check revocation in database
            String jwtId = claims.getId();
            Token dbToken = findTokenByJwtId(jwtId);

            if (dbToken != null) {
                // Token exists in database, check revocation
                if (dbToken.isRevoked()) {
                    log.debug("JWT token revoked: jti={}", jwtId);
                    return null;
                }
                if (dbToken.isExpired()) {
                    log.debug("JWT token expired in database, jti={}", jwtId);
                    return null;
                }
            } else {
                // Token does not exist in database (might be old token), still validate JWT
                // itself
                log.debug("JWT token not found in database (might be legacy token): jti={}",
                        jwtId);
            }

            // Step 3: Extract user info from claims
            UserInfoDTO userInfo = JwtUtil.extractUserInfo(claims);
            if (userInfo != null) {
                log.debug("JWT token validated successfully for user: {}", userInfo.getUsername());
            }
            return userInfo;
        } catch (Exception e) {
            log.error("Error validating JWT token", e);
            return null;
        }
    }

    /**
     * Validate UUID token
     */
    private UserInfoDTO validateUuidToken(String token) {
        try {
            // Step 1: Lookup token in database
            Token dbToken = findTokenByTokenValue(token);

            if (dbToken != null) {
                // Token found in database
                if (dbToken.isRevoked()) {
                    log.debug("UUID token revoked: token={}", token.substring(0, Math.min(20, token.length())));
                    return null;
                }

                if (dbToken.isExpired()) {
                    log.debug("UUID token expired: token={}", token.substring(0,
                            Math.min(20, token.length())));
                    return null;
                }

                // Build UserInfoDTO from database token
                UserInfoDTO userInfo = UserInfoDTO.builder()
                        .userId(dbToken.getUserId())
                        .username(dbToken.getUsername())
                        // Real name not stored in token table, would need user lookup
                        .realName(null)
                        .build();
                log.debug("UUID token validated successfully for user: {}", userInfo.getUsername());
                return userInfo;
            }

            // Step 2: Fallback to Redis lookup for backward compatibility
            log.debug("UUID token not found in database, falling back to Redis lookup");
            return validateTokenFromRedis(token);
        } catch (Exception e) {
            log.error("Error validating UUID token", e);
            return validateTokenFromRedis(token);
        }
    }

    /**
     * Find token by token value using HQLQueryBuilder
     */
    private Token findTokenByTokenValue(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return null;
        }

        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(Token.class, "t")
                .select("t")
                .eq("t.tokenValue", tokenValue)
                .isNull("t.deleted")
                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        @SuppressWarnings("unchecked")
        List<Token> results = queryService.query(hql, params);

        if (results == null || results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    /**
     * Validate Token from Redis (backward compatibility)
     */
    private UserInfoDTO validateTokenFromRedis(String token) {
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

            // Step 2: Get user info from session hash (username -> {token: user_json}
            return getFromRedisSession(username, token);
        } catch (Exception e) {
            log.error("Error resolving user info from token in Redis: {}", token, e);
            return null;
        }
    }


    /**
     * Find token by JWT ID using HQLQueryBuilder
     */
    private Token findTokenByJwtId(String jwtId) {
        if (jwtId == null || jwtId.isBlank()) {
            return null;
        }

        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(Token.class, "t")
                .select("t")
                .eq("t.jwtId", jwtId)
                .isNull("t.deleted")
                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        @SuppressWarnings("unchecked")
        List<Token> results = queryService.query(hql, params);

        return (results == null || results.isEmpty()) ? null : results.get(0);
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
     * Find valid tokens by user ID using HqlQueryBuilder
     */
    private List<Token> findValidTokensByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }

        Date now = new Date();
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(Token.class, "t")
                .select("t")
                .eq("t.userId", userId)
                .isNull("t.deleted")
                .isNotNull("t.revokedAt")
                .open()
                .isNull("t.expiresAt")
                .or()
                .gt("t.expiresAt", now)
                .close()
                .orderBy("t.issuedAt", false)
                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();


        @SuppressWarnings("unchecked")
        List<Token> results = queryService.query(hql, params);

        return results == null ? List.of() : results;
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
