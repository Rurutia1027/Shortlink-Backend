package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsNetworkRespDTO {

    /**
     * Count
     */
    private Integer cnt;

    /**
     * Network
     */
    private String network;

    /**
     * Ratio / Proportion
     */
    private Double ratio;
}
