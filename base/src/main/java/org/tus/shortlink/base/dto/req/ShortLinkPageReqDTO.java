package org.tus.shortlink.base.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortLinkPageReqDTO {

    /**
     * Group identifier
     */
    private String gid;

    /**
     * Sorting tag
     */
    private String orderTag;

    /**
     * Page number (start from 1)
     */
    private Integer pageNo = 1;

    /**
     * Page size
     */
    private Integer pageSize = 10;
}
