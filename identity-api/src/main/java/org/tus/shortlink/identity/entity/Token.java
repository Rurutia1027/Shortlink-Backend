package org.tus.shortlink.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tus.common.domain.persistence.PersistedObject;
import org.tus.shortlink.identity.enums.TokenType;

import java.util.Date;


/**
 * Token Entity
 * <p>Stores token information in the database for both JWT and UUID token types.
 * Supports token revocation, expiration tracking, and metadata storage.
 */
@Entity
@Table(
        name = "t_token",
        indexes = {
                @Index(name = "idx_token_user_id", columnList = "user_id"),
                @Index(name = "idx_token_username", columnList = "username"),
                @Index(name = "idx_token_type", columnList = "token_type"),
                @Index(name = "idx_token_expires_at", columnList = "expires_at"),
                @Index(name = "idx_token_revoked_at", columnList = "revoked_at"),
                @Index(name = "idx_token_jwt_id", columnList = "jwt_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Token extends PersistedObject {
    /**
     * Token value (JWT string or UUID string)
     * Unique constraint ensures no duplicate tokens
     */
    @Column(name = "token_value", nullable = false, unique = true, length = 2048)
    private String tokenValue;

    /**
     * Token type: JWT or UUID
     */
    @Column(name = "token_type", nullable = false, length = 50)
    private String tokenType; // Store as String, convert to/from TokenType enum

    /**
     * User ID (foreign key to t_user.uuid)
     */
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    /**
     * Username (denormalized for quick lookup)
     */
    @Column(name = "username", nullable = false, length = 255)
    private String username;

    /**
     * JWT ID (jti claim) - Only for JWT tokens
     */
    @Column(name = "jwt_id", length = 255)
    private String jwtId;

    /**
     * JWT Subject (sub claim) - Only for JWT tokens
     */
    @Column(name = "jwt_subject", length = 255)
    private String jwtSubject;

    /**
     * JWT Issuer (iss claim) - Only for JWT tokens
     */
    @Column(name = "jwt_issuer", length = 255)
    private String jwtIssuer;

    /**
     * JWT Audience (aud claim) - Only for JWT tokens
     */
    @Column(name = "jwt_audience", length = 255)
    private String jwtAudience;

    /**
     * Token issuance timestamp
     */
    @Column(name = "issued_at", nullable = false)
    private Date issuedAt;

    /**
     * Token expiration timestamp (null for tokens that don't expire)
     */
    @Column(name = "expires_at")
    private Date expiresAt;

    /**
     * Token revocation timestamp (null if not revoked)
     */
    @Column(name = "revoked_at")
    private Date revokedAt;

    /**
     * Additional metadata stored as JSON
     */
    @Column(name = "metadata", columnDefinition = "text")
    private String metadata;

    /**
     * Get TokenType enum from string
     */
    public TokenType getTokenTypeEnum() {
        return TokenType.valueOf(tokenType);
    }

    /**
     * Set TokenType enum as string
     */
    public void setTokenTypeEnum(TokenType type) {
        this.tokenType = type.name();
    }

    /**
     * Check if token is revoked
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Check if token is expired
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false; // No expiration
        }
        return expiresAt.before(new Date());
    }

    /**
     * Check if token is valid (not revoked and not expired)
     */
    public boolean isValid() {
        return !isRevoked() && !isExpired();
    }

    /**
     * Revoke the token
     */
    public void revoke() {
        this.revokedAt = new Date();
    }
}
