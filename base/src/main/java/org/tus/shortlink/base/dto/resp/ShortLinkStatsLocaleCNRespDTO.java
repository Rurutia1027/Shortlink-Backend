package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsLocaleCNRespDTO {

    /**
     * Count
     */
    private Integer cnt;

    /**
     * Region / Locale
     */
    private String locale;

    /**
     * Ratio / Proportion
     */
    private Double ratio;
}
