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
}
