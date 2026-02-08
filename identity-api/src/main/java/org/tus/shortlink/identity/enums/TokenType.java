package org.tus.shortlink.identity.enums;

/**
 * Token Type Enumeration:
 * <p>Defines the types of tokens supported by the Identity module.
 * <ul>
 *     <li>JWT: JSON Web Token - Stateless, self-contained token with claims</li>
 *     <li>UUID: UUID-based token - Stateful token stored in Redis/Database </li>
 * </ul>
 */
public enum TokenType {
    /**
     * JSON Web Token (JWT)
     * - Self-contained token with claims (userId, username, expirations, etc.)
     * - Stateless validation (no database lookup required for validation)
     * - Can be validated by signature verification.
     * - Format: header.payload.signature (base64url encoded)
     */
    JWT,

    /**
     * UUID-based Token
     * - Stateful token stored in Redis/Database
     * - Requires lookup for validation
     * - Simpler but requires storage
     * - Format: UUID string (e.g., "550e8400-e29b-41d4-a716-446655440000")
     */
    UUID
}
