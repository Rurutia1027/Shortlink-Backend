package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response object for batch creation of short links
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkBatchCreateRespDTO {

    /**
     * Number of successful creations
     */
    private Integer total;

    /**
     * Parameters returned for each created short link
     */
    private List<ShortLinkBaseInfoRespDTO> baseLinkInfos;
}
