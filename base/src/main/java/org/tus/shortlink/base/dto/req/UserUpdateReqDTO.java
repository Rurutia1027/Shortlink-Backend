package org.tus.shortlink.base.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User update request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateReqDTO {

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
}
