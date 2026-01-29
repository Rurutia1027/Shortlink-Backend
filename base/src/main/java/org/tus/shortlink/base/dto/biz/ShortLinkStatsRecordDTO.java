package org.tus.shortlink.base.dto.biz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsRecordDTO {

    /**
     * Group ID
     */
    private String gid;

    /**
     * Full short url
     */
    private String fullShortUrl;

    /**
     * Visitor IP
     */
    private String remoteAddr;

    /**
     * Source (Referer)
     */
    private String referrer;

    /**
     * User Agent
     */
    private String userAgent;

    /**
     * Request os
     */
    private String os;

    /**
     * Request browser
     */
    private String browser;

    /**
     * Device info
     */
    private String device;

    /**
     * Network
     */
    private String network;

    /**
     * UV
     */
    private String uv;

    /**
     * UV First Flag
     */
    private Boolean uvFirstFlag;

    /**
     * UIP First Flag
     */
    private Boolean uipFirstFlag;

    /**
     * Message Queue UID
     */
    private String keys;

    /**
     * current date
     */
    private Date currentDate;
}
