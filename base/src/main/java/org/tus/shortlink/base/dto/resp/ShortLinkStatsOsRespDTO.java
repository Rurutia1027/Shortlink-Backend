package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsOsRespDTO {

    /**
     * Count
     */
    private Integer cnt;

    /**
     * Operating system
     */
    private String os;

    /**
     * Ratio / Proportion
     */
    private Double ratio;
}
