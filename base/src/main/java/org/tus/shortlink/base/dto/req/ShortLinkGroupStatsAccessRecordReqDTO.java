package org.tus.shortlink.base.dto.req;

import lombok.Data;

@Data
public class ShortLinkGroupStatsAccessRecordReqDTO {

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

    /**
     * Current page number (start from 1)
     */
    private Integer current = 1;

    /**
     * Page size
     */
    private Integer size = 10;
}
