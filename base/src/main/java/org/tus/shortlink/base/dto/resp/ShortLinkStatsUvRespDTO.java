package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsUvRespDTO {

    /**
     * Count
     */
    private Integer cnt;

    /**
     * Visitor type
     */
    private String uvType;

    /**
     * Ratio / Proportion
     */
    private Double ratio;
}
