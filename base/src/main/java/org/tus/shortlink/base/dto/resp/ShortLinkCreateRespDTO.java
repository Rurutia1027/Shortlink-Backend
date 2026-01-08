package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkCreateRespDTO {

    /**
     * Group information
     */
    private String gid;

    /**
     * Original URL
     */
    private String originUrl;

    /**
     * Short URL
     */
    private String fullShortUrl;
}
