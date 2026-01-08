package org.tus.shortlink.base.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsAccessDailyRespDTO {

    /**
     * Date
     */
    private String date;

    /**
     * Page views (PV)
     */
    private Integer pv;

    /**
     * Unique visitors (UV)
     */
    private Integer uv;

    /**
     * Unique IPs (UIP)
     */
    private Integer uip;
}
