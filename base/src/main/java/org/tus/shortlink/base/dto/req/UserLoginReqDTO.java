package org.tus.shortlink.base.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User login request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLoginReqDTO {

    /**
     * Username
     */
    private String username;

    /**
     * Password
     */
    private String password;
}
