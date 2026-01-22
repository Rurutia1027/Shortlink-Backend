package org.tus.shortlink.base.dto.req;

import lombok.Data;

@Data
public class ShortLinkStatsAccessRecordReqDTO {

    /**
     * Full short link URL
     */
    private String fullShortUrl;

    /**
     * Group ID
     */
    private String gid;

    /**
     * Start date (format: yyyy-MM-dd or ISO)
     */
    private String startDate;

    /**
     * End date (format: yyyy-MM-dd or ISO)
     */
    private String endDate;

    /**
     * Enable status: 0 = enabled, 1 = disabled
     */
    private Integer enableStatus;

    /**
     * Current page number (start from 1)
     */
    private Integer current = 1;

    /**
     * Page size
     */
    private Integer size = 10;
}
