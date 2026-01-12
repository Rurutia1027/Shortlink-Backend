package org.tus.shortlink.base.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class ShortLinkPageRespDTO {

    /**
     * ID
     */
    private String id;

    /**
     * Domain
     */
    private String domain;

    /**
     * Short URI
     */
    private String shortUri;

    /**
     * Full short URL
     */
    private String fullShortUrl;

    /**
     * Original URL
     */
    private String originUrl;

    /**
     * Group identifier
     */
    private String gid;

    /**
     * Validity type: 0 - permanent, 1 - custom
     */
    private Integer validDateType;

    /**
     * Enable status: 0 - enabled, 1 - disabled
     */
    private Integer enableStatus;

    /**
     * Validity date
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date validDate;

    /**
     * Creation time
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /**
     * Description
     */
    private String describe;

    /**
     * Website identifier (favicon)
     */
    private String favicon;

    /**
     * Historical page views (PV)
     */
    private Integer totalPv;

    /**
     * Today's page views (PV)
     */
    private Integer todayPv;

    /**
     * Historical unique visitors (UV)
     */
    private Integer totalUv;

    /**
     * Today's unique visitors (UV)
     */
    private Integer todayUv;

    /**
     * Historical unique IPs (UIP)
     */
    private Integer totalUip;

    /**
     * Today's unique IPs (UIP)
     */
    private Integer todayUip;
}
