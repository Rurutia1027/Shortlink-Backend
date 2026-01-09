package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
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
