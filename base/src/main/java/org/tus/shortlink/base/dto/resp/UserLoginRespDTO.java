package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User login response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLoginRespDTO {

    /**
     * User login token
     */
    private String token;

    /**
     * User information
     */
    private UserRespDTO userInfo;
}
