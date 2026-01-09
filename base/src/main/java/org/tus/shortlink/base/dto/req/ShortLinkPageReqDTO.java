package org.tus.shortlink.base.dto.req;

import lombok.Data;

@Data
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
