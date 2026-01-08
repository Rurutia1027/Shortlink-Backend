package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsDeviceRespDTO {

    /**
     * Count
     */
    private Integer cnt;

    /**
     * Device type
     */
    private String device;

    /**
     * Ratio / Proportion
     */
    private Double ratio;
}
