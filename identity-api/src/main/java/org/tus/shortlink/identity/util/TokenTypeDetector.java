package org.tus.shortlink.identity.util;

import lombok.extern.slf4j.Slf4j;
import org.tus.shortlink.identity.enums.TokenType;

import java.util.regex.Pattern;

/**
 * Token Type Detector
 *
 * <p>Detects the type of token (JWT or UUID) based on its format.
 *
 * <p>Detection logic:
 * <ul>
 *     <li>JWT: Containers exactly 3 parts separated by dots (header.payload.signature)</li>
 *     <li>UUID: Matches UUID format (8-4-4-4-12 hexadecimal digits)</li>
 * </ul>
 */

@Slf4j
public class TokenTypeDetector {
    /**
     * JWT format: three base64url-encoded parts separated by dots
     * Example: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
     */
    private static final Pattern JWT_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");

    /**
     * UUID format: 8-4-4-4-12 hexadecimal digits separated by hyphens
     * Example: 550e8400-e29b-41d4-a716-446655440000
     */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );


    /**
     * Detects the token type based on its format.
     *
     * @param token The token string to analyze
     * @return TokenType (JWT or UUID), or null if format is unrecognized
     */
    public static TokenType detect(String token) {
        if (token == null || token.isBlank()) {
            log.debug("Token is null or blank, cannot detect type");
            return null;
        }

        // Remove Bearer prefix if present
        String cleanToken = token.trim();
        if (cleanToken.startsWith("Bearer ")) {
            cleanToken = cleanToken.substring(7).trim();
        }

        // Check JWT format first (more specific pattern)
        if (JWT_PATTERN.matcher(cleanToken).matches()) {
            log.debug("Detected JWT token (format: header.payload.signature)");
            return TokenType.JWT;
        }

        // Check UUID format
        if (UUID_PATTERN.matcher(cleanToken).matches()) {
            log.debug("Detected UUID token (format: 8-4-4-4-12)");
            return TokenType.UUID;
        }

        // If token looks like it might be a JWT (has dots but doesn't match exact pattern)
        // This handles edge cases where JWT might have different encoding
        if (cleanToken.contains(".") && cleanToken.split("\\.").length == 3) {
            log.debug("Detected JWT token (has 3 dot-separated parts)");
            return TokenType.JWT;
        }

        log.warn("Unable to detect token type for token: {} (length: {})",
                cleanToken.substring(0, Math.min(20, cleanToken.length())),
                cleanToken.length());
        return null;
    }

    /**
     * Checks if the token is a JWT token.
     *
     * @param token The token string to check
     * @return true if token appears to be a JWT, false otherwise
     */
    public static boolean isJWT(String token) {
        return detect(token) == TokenType.JWT;
    }

    /**
     * Checks if the token is a UUID token.
     *
     * @param token The token string to check
     * @return true if token appears to be a UUID, false otherwise
     */
    public static boolean isUUID(String token) {
        return detect(token) == TokenType.UUID;
    }
}
