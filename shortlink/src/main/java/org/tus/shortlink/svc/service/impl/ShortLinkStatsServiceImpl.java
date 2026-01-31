package org.tus.shortlink.svc.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tus.common.domain.model.PageResponse;
import org.tus.shortlink.base.dto.req.ShortLinkGroupStatsAccessRecordReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkGroupStatsReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkStatsAccessRecordReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkStatsReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsRespDTO;
import org.tus.shortlink.svc.service.ClickHouseStatsService;
import org.tus.shortlink.svc.service.ShortLinkStatsService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;

/**
 * Stats service: merges ClickHouse-backed statistics into ShortLinkStatsRespDTO.
 * Access record endpoints query link_stats_events (paginated).
 */
@Service
@RequiredArgsConstructor
public class ShortLinkStatsServiceImpl implements ShortLinkStatsService {

    private final ClickHouseStatsService clickHouseStatsService;

    @Override
    public ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam) {
        if (requestParam == null || requestParam.getFullShortUrl() == null || requestParam.getFullShortUrl().isBlank()) {
            return emptyResp();
        }
        LocalDate start = parseDate(requestParam.getStartDate());
        LocalDate end = parseDate(requestParam.getEndDate());
        if (start == null || end == null || start.isAfter(end)) {
            return emptyResp();
        }
        String fullShortUrl = requestParam.getFullShortUrl().trim();
        String gid = requestParam.getGid() != null ? requestParam.getGid() : "";

        ClickHouseStatsService.TotalStats total = clickHouseStatsService.queryTotalStats(fullShortUrl, gid, start, end);
        List<org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessDailyRespDTO> daily =
                clickHouseStatsService.queryDailyStats(fullShortUrl, gid, start, end);
        List<Integer> hourStats = clickHouseStatsService.queryHourlyStats(fullShortUrl, start, end);

        return ShortLinkStatsRespDTO.builder()
                .pv((int) Math.min(total.pv(), Integer.MAX_VALUE))
                .uv((int) Math.min(total.uv(), Integer.MAX_VALUE))
                .uip((int) Math.min(total.uip(), Integer.MAX_VALUE))
                .daily(daily != null ? daily : Collections.emptyList())
                .localeCnStats(Collections.emptyList())
                .hourStats(hourStats != null ? hourStats : zero24())
                .topIpStats(Collections.emptyList())
                .weekdayStats(Collections.emptyList())
                .browserStats(clickHouseStatsService.queryBrowserStats(fullShortUrl, gid, start, end))
                .osStats(clickHouseStatsService.queryOsStats(fullShortUrl, gid, start, end))
                .uvTypeStats(Collections.emptyList())
                .deviceStats(clickHouseStatsService.queryDeviceStats(fullShortUrl, gid, start, end))
                .networkStats(clickHouseStatsService.queryNetworkStats(fullShortUrl, gid, start, end))
                .build();
    }

    @Override
    public ShortLinkStatsRespDTO groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam) {
        if (requestParam == null || requestParam.getGid() == null || requestParam.getGid().isBlank()) {
            return emptyResp();
        }
        LocalDate start = parseDate(requestParam.getStartDate());
        LocalDate end = parseDate(requestParam.getEndDate());
        if (start == null || end == null || start.isAfter(end)) {
            return emptyResp();
        }
        String gid = requestParam.getGid().trim();

        ClickHouseStatsService.TotalStats total = clickHouseStatsService.queryGroupTotalStats(gid, start, end);
        List<org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessDailyRespDTO> daily =
                clickHouseStatsService.queryGroupDailyStats(gid, start, end);

        return ShortLinkStatsRespDTO.builder()
                .pv((int) Math.min(total.pv(), Integer.MAX_VALUE))
                .uv((int) Math.min(total.uv(), Integer.MAX_VALUE))
                .uip((int) Math.min(total.uip(), Integer.MAX_VALUE))
                .daily(daily != null ? daily : Collections.emptyList())
                .localeCnStats(Collections.emptyList())
                .hourStats(zero24())
                .topIpStats(Collections.emptyList())
                .weekdayStats(Collections.emptyList())
                .browserStats(Collections.emptyList())
                .osStats(Collections.emptyList())
                .uvTypeStats(Collections.emptyList())
                .deviceStats(Collections.emptyList())
                .networkStats(Collections.emptyList())
                .build();
    }

    @Override
    public PageResponse<ShortLinkStatsAccessRecordRespDTO> shortLinkStatsAccessRecord(
            ShortLinkStatsAccessRecordReqDTO requestParam) {
        if (requestParam == null || requestParam.getFullShortUrl() == null || requestParam.getFullShortUrl().isBlank()) {
            return emptyPage();
        }
        LocalDate start = parseDate(requestParam.getStartDate());
        LocalDate end = parseDate(requestParam.getEndDate());
        if (start == null || end == null || start.isAfter(end)) {
            return emptyPage();
        }
        int current = requestParam.getCurrent() != null && requestParam.getCurrent() > 0 ? requestParam.getCurrent() : 1;
        int size = requestParam.getSize() != null && requestParam.getSize() > 0 ? requestParam.getSize() : 10;
        String fullShortUrl = requestParam.getFullShortUrl().trim();
        String gid = requestParam.getGid() != null ? requestParam.getGid() : "";

        ClickHouseStatsService.AccessRecordPage page = clickHouseStatsService.queryAccessRecords(
                fullShortUrl, gid, start, end, current, size);
        PageResponse<ShortLinkStatsAccessRecordRespDTO> resp = new PageResponse<>();
        resp.setStart((current - 1) * size);
        resp.setPageSize(size);
        resp.setTotal((int) Math.min(page.total(), Integer.MAX_VALUE));
        resp.setElements(page.records() != null ? page.records() : Collections.emptyList());
        return resp;
    }

    @Override
    public PageResponse<ShortLinkStatsAccessRecordRespDTO> groupShortLinkStatsAccessRecord(
            ShortLinkGroupStatsAccessRecordReqDTO requestParam) {
        if (requestParam == null || requestParam.getGid() == null || requestParam.getGid().isBlank()) {
            return emptyPage();
        }
        LocalDate start = parseDate(requestParam.getStartDate());
        LocalDate end = parseDate(requestParam.getEndDate());
        if (start == null || end == null || start.isAfter(end)) {
            return emptyPage();
        }
        int current = requestParam.getCurrent() != null && requestParam.getCurrent() > 0 ? requestParam.getCurrent() : 1;
        int size = requestParam.getSize() != null && requestParam.getSize() > 0 ? requestParam.getSize() : 10;
        String gid = requestParam.getGid().trim();

        ClickHouseStatsService.AccessRecordPage page = clickHouseStatsService.queryGroupAccessRecords(
                gid, start, end, current, size);
        PageResponse<ShortLinkStatsAccessRecordRespDTO> resp = new PageResponse<>();
        resp.setStart((current - 1) * size);
        resp.setPageSize(size);
        resp.setTotal((int) Math.min(page.total(), Integer.MAX_VALUE));
        resp.setElements(page.records() != null ? page.records() : Collections.emptyList());
        return resp;
    }

    private static ShortLinkStatsRespDTO emptyResp() {
        return ShortLinkStatsRespDTO.builder()
                .pv(0)
                .uv(0)
                .uip(0)
                .daily(Collections.emptyList())
                .localeCnStats(Collections.emptyList())
                .hourStats(zero24())
                .topIpStats(Collections.emptyList())
                .weekdayStats(Collections.emptyList())
                .browserStats(Collections.emptyList())
                .osStats(Collections.emptyList())
                .uvTypeStats(Collections.emptyList())
                .deviceStats(Collections.emptyList())
                .networkStats(Collections.emptyList())
                .build();
    }

    private static List<Integer> zero24() {
        return Collections.nCopies(24, 0);
    }

    private static PageResponse<ShortLinkStatsAccessRecordRespDTO> emptyPage() {
        PageResponse<ShortLinkStatsAccessRecordRespDTO> r = new PageResponse<>();
        r.setStart(0);
        r.setPageSize(10);
        r.setTotal(0);
        r.setElements(Collections.emptyList());
        return r;
    }

    private static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
