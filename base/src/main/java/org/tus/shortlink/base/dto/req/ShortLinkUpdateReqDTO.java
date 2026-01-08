package org.tus.shortlink.base.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class ShortLinkUpdateReqDTO {

    /**
     * Original URL
     */
    private String originUrl;

    /**
     * Full short URL
     */
    private String fullShortUrl;

    /**
     * Original group identifier
     */
    private String originGid;

    /**
     * Group identifier
     */
    private String gid;

    /**
     * Validity type: 0 - permanent, 1 - custom
     */
    private Integer validDateType;

    /**
     * Validity date
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date validDate;

    /**
     * Description
     */
    private String describe;
}
