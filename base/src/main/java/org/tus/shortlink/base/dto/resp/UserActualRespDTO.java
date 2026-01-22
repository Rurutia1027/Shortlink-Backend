package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User actual response DTO (without desensitization)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActualRespDTO {

    /**
     * User ID
     */
    private String id;

    /**
     * Username
     */
    private String username;

    /**
     * Real name
     */
    private String realName;

    /**
     * Phone number
     */
    private String phone;

    /**
     * Email address
     */
    private String mail;

    /**
     * Deletion timestamp (soft delete)
     */
    private Long deletionTime;
}
