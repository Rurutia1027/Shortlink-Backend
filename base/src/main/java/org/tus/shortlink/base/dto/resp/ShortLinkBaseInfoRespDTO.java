package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkBaseInfoRespDTO {

    /**
     * Description
     */
    private String describe;

    /**
     * Original URL
     */
    private String originUrl;

    /**
     * Short URL
     */
    private String fullShortUrl;
}
