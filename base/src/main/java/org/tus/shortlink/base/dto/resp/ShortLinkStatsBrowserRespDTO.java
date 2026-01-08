package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsBrowserRespDTO {

    /**
     * Count
     */
    private Integer cnt;

    /**
     * Browser
     */
    private String browser;

    /**
     * Ratio / Proportion
     */
    private Double ratio;
}
