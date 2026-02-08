package org.tus.shortlink.identity.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.tus.shortlink.base.biz.UserInfoDTO;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT Utility Class
 * <p>Provides methods to generate, validate, and parse JWT tokens.
 * Uses HMAC-SHA256 algorithm for signing.
 */

@Slf4j
public class JwtUtil {
    /**
     * Default JWT secrets key (should be configured via properties)
     * IN production, this should be a strong, randomly generated secret
     */
    private static final String DEFAULT_SECRET = "your-256-bit-secret-key-change-this-in-production-minimum-32-characters";

    /**
     * Default JWT expiration time: 24 hours (in milliseconds)
     */
    private static final long DEFAULT_EXPIRATION_MS = 24 * 60 * 60 * 1000L;

    /**
     * JWT issuer claim
     */
    private static final String ISSUER = "shortlink-platform";

    /**
     * JWT audience claim
     */
    private static final String AUDIENCE = "shortlink-client";

    /**
     * Generate a secret key from the secret string
     */
    private static SecretKey getSecretKey(String secret) {
        // Ensure secret is at least 32 characters for HS256
        String actualSecret = secret != null && secret.length() >= 32
                ? secret
                : DEFAULT_SECRET;
        return Keys.hmacShaKeyFor(actualSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate JWT token for a user
     *
     * @param userInfo     User information to include in token
     * @param secret       Secret key for signing (if null, uses default)
     * @param expirationMs Expiration time in milliseconds (if null, uses default)
     * @return JWT token string
     */
    public static String generateToken(UserInfoDTO userInfo, String secret,
                                       Long expirationMs) {
        if (userInfo == null || userInfo.getUserId() == null) {
            throw new IllegalArgumentException("UserInfoDTO and userId must not be null");
        }

        SecretKey key = getSecretKey(secret);
        long expiration = expirationMs != null ? expirationMs : DEFAULT_EXPIRATION_MS;
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        // Generate JWT ID (jti claim)
        String jti = UUID.randomUUID().toString();

        // Build claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userInfo.getUserId());
        claims.put("username", userInfo.getUsername());
        if (userInfo.getRealName() != null) {
            claims.put("realName", userInfo.getRealName());
        }

        String token = Jwts.builder()
                .id(jti)  // JWT ID
                .subject(userInfo.getUserId())  // Subject (user ID)
                .issuer(ISSUER)  // Issuer
                .audience().add(AUDIENCE).and()  // Audience
                .claims(claims)  // Custom claims
                .issuedAt(now)  // Issued at
                .expiration(expiryDate)  // Expiration
                .signWith(key)  // Sign with secret key
                .compact();

        log.debug("Generated JWT token for user: {} (jti: {})", userInfo.getUsername(), jti);
        return token;
    }

    /**
     * Generate JWT token with default settings
     */
    public static String generateToken(UserInfoDTO userInfo) {
        return generateToken(userInfo, null, null);
    }

    /**
     * Validate and parse JWT token
     *
     * @param token  JWT token string
     * @param secret Secret key for verification (if null, uses default)
     * @return Claims if token is valid, null otherwise
     */
    public static Claims validateAndParseToken(String token, String secret) {
        if (token == null || token.isBlank()) {
            log.debug("Token is null or blank");
            return null;
        }

        try {
            SecretKey key = getSecretKey(secret);
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .requireAudience(AUDIENCE)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Check expiration
            if (claims.getExpiration().before(new Date())) {
                log.debug("JWT token expired: jti={}", claims.getId());
                return null;
            }

            log.debug("JWT token validated successfully: jti={}, subject={}",
                    claims.getId(), claims.getSubject());
            return claims;
        } catch (JwtException e) {
            log.debug("JWT token validation failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error validating JWT token", e);
            return null;
        }
    }

    /**
     * Validate and parse JWT token with default secret
     */
    public static Claims validateAndParseToken(String token) {
        return validateAndParseToken(token, null);
    }

    /**
     * Extract UserInfoDTO from JWT claims
     *
     * @param claims JWT claims
     * @return UserInfoDTO if claims are valid, null otherwise
     */
    public static UserInfoDTO extractUserInfo(Claims claims) {
        if (claims == null) {
            return null;
        }

        try {
            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            String realName = claims.get("realName", String.class);

            if (userId == null || username == null) {
                log.warn("JWT claims missing required fields: userId = {}, username= {}",
                        userId, username);
                return null;
            }

            return UserInfoDTO.builder()
                    .userId(userId)
                    .username(username)
                    .realName(realName)
                    .build();
        } catch (Exception e) {
            log.error("Failed to extract user info from JWT claims", e);
            return null;
        }
    }


    /**
     * Extract JWT ID (jti claim) from token
     *
     * @param token  JWT token string
     * @param secret Secret key (if null, uses default)
     * @return JWT ID if token is valid, null otherwise
     */
    public static String extractJwtId(String token, String secret) {
        Claims claims = validateAndParseToken(token, secret);
        return claims != null ? claims.getId() : null;
    }

    /**
     * Extract JWT ID with default secret
     */
    public static String extractJwtId(String token) {
        return extractJwtId(token, null);
    }

    /**
     * Check if token is expired (without full validation)
     *
     * @param token  JWT token string
     * @param secret Secret key (if null, uses default)
     * @return true if expired, false otherwise
     */
    public static boolean isTokenExpired(String token, String secret) {
        Claims claims = validateAndParseToken(token, secret);
        if (claims == null) {
            return true; // Consider invalid tokens as expired
        }
        return claims.getExpiration().before(new Date());
    }

    /**
     * Check if token is expired with default secret
     */
    public static boolean isTokenExpired(String token) {
        return isTokenExpired(token, null);
    }
}
