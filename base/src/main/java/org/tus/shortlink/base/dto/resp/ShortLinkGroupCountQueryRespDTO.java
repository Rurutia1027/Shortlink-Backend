package org.tus.shortlink.base.dto.resp;

import lombok.Data;

@Data
public class ShortLinkGroupCountQueryRespDTO {

    /**
     * Group identifier
     */
    private String gid;

    /**
     * Number of short links
     */
    private Integer shortLinkCount;
}
