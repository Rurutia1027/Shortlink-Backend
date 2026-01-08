package org.tus.shortlink.base.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsAccessRecordRespDTO {

    /**
     * Visitor type
     */
    private String uvType;

    /**
     * Browser
     */
    private String browser;

    /**
     * Operating system
     */
    private String os;

    /**
     * IP address
     */
    private String ip;

    /**
     * Network
     */
    private String network;

    /**
     * Device
     */
    private String device;

    /**
     * Region / Locale
     */
    private String locale;

    /**
     * User information
     */
    private String user;

    /**
     * Access time
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
