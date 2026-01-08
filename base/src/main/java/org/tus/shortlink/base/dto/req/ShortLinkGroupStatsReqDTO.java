package org.tus.shortlink.base.dto.req;

import lombok.Data;

@Data
public class ShortLinkGroupStatsReqDTO {

    /**
     * Group identifier
     */
    private String gid;

    /**
     * Start date
     */
    private String startDate;

    /**
     * End date
     */
    private String endDate;
}
