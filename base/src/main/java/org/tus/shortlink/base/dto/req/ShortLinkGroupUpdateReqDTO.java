package org.tus.shortlink.base.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Short link group update request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortLinkGroupUpdateReqDTO {

    /**
     * Group identifier
     */
    private String gid;

    /**
     * Group name
     */
    private String name;
}
