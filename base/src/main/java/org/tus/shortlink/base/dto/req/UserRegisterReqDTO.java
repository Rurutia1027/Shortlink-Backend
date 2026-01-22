package org.tus.shortlink.base.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User register request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRegisterReqDTO {

    /**
     * Username
     */
    private String username;

    /**
     * Password
     */
    private String password;

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
