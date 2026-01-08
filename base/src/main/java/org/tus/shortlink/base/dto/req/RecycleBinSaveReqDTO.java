package org.tus.shortlink.base.dto.req;

import lombok.Data;

@Data
public class RecycleBinSaveReqDTO {

    /**
     * Group identifier
     */
    private String gid;

    /**
     * Full short URL
     */
    private String fullShortUrl;
}
