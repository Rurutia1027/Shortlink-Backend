package org.tus.shortlink.base.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Short link group sort request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortLinkGroupSortReqDTO {

    /**
     * Group identifier
     */
    private String gid;

    /**
     * Sort order
     */
    private Integer sortOrder;
}
