package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Short link group response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortLinkGroupRespDTO {

    /**
     * Group identifier
     */
    private String gid;

    /**
     * Group name
     */
    private String name;

    /**
     * Username who created the group
     */
    private String username;

    /**
     * Group sort order
     */
    private Integer sortOrder;

    /**
     * Number of short links in this group
     */
    private Integer shortLinkCount;
}
