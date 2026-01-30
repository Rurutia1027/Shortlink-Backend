package org.tus.shortlink.svc.service;

import org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessDailyRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsBrowserRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsDeviceRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsNetworkRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsOsRespDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * Query service for short link statistics from ClickHouse.
 * Returns empty lists / zeros when ClickHouse is not configured or query fails.
 */
public interface ClickHouseStatsService {
    /**
     * Daily stats (pv, uv, uip) for one link in data range.
     */
    List<ShortLinkStatsAccessDailyRespDTO> queryDailyStats(
            String fullShortUrl, String gid, LocalDate startDate, LocalDate endDate);

    /**
     * Daily stats aggregated for a group (all links under gid) in date range.
     */
    List<ShortLinkStatsAccessDailyRespDTO> queryGroupDailyStats(
            String gid, LocalDate startDate, LocalDate endDate);

    /**
     * Hourly PV for one link in date range. Returns 24 elements (index=hour, value=pv).
     */
    List<Integer> queryHourlyStats(String fullShortUrl, LocalDate startDate,
                                   LocalDate endDate);

    /**
     * Browser dimension stats for one link in date range.
     */
    List<ShortLinkStatsBrowserRespDTO> queryBrowserStats(
            String fullShortUrl, String gid, LocalDate startDate, LocalDate endDate);

    /**
     * OS dimension stats for one link in date range.
     */
    List<ShortLinkStatsOsRespDTO> queryOsStats(String fullShortUrl, String gid,
                                               LocalDate startDate, LocalDate endDate);

    /**
     * Device dimension stats for one link in date range.
     */
    List<ShortLinkStatsDeviceRespDTO> queryDeviceStats(
            String fullShortUrl, String gid, LocalDate startDate, LocalDate endDate);

    /**
     * Network dimension stats for one link ein date range.
     */
    List<ShortLinkStatsNetworkRespDTO> queryNetworkStats(
            String fullShortUrl, String gid, LocalDate startDate, LocalDate endDate);

    /**
     * Total pv, uv, uip for one link in date range (for response totals).
     */
    TotalStats queryTotalStats(String fullShortUrl, String gid, LocalDate startDate,
                               LocalDate endDate);

    /**
     * Total pv, uv, uip for a group in date range
     */
    TotalStats queryGroupTotalStats(String gid, LocalDate startDate, LocalDate endDate);

    record TotalStats(long pv, long uv, long uip) {
    }
}
