package org.tus.shortlink.base.dto.req;

import lombok.Data;

@Data
public class ShortLinkStatsReqDTO {

    /**
     * Full short URL
     */
    private String fullShortUrl;

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
     * Enable status: 0 - enabled, 1 - disabled
     */
    private Integer enableStatus;
}
