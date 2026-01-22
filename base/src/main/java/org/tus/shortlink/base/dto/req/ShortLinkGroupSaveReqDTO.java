package org.tus.shortlink.base.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Short link group save request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortLinkGroupSaveReqDTO {

    /**
     * Group name
     */
    private String name;
}
