package org.tus.shortlink.identity.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for token validation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenValidationRequest {
    /**
     * Authentication token to validate
     * Can be UUID token, JWT token, or session ID
     */
    private String token;
}
