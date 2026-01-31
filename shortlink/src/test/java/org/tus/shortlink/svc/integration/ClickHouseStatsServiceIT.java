package org.tus.shortlink.svc.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessDailyRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsBrowserRespDTO;
import org.tus.shortlink.svc.config.ClickHouseTestConfig;
import org.tus.shortlink.svc.service.ClickHouseStatsService;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for ClickHouseStatService against a real ClickHouse Test-container.
 * Uses the real services (not mocked) and asserts on data inserted in ClickHouse.
 */
@SpringJUnitConfig(classes = ClickHouseTestConfig.class)
public class ClickHouseStatsServiceIT {
    private static final String URL1 = "https://short.example/abc";
    private static final String GID = "g1";
    private static final LocalDate START = LocalDate.of(2025, 1, 15);
    private static final LocalDate END = LocalDate.of(2025, 1, 18);


    @Autowired
    private ClickHouseStatsService clickHouseStatsService;


    @Test
    void queryDailyStats_returnsDataForLink() {
        List<ShortLinkStatsAccessDailyRespDTO> list =
                clickHouseStatsService.queryDailyStats(URL1, GID, START, END);
        assertNotNull(list);
        assertFalse(list.isEmpty(), "daily stats for link should not be empty");
        assertTrue(list.stream().anyMatch(d -> d.getPv() != null && d.getPv() > 0));
    }

    @Test
    void queryGroupDailyStats_returnsDataForGroup() {
        List<ShortLinkStatsAccessDailyRespDTO> list = clickHouseStatsService.queryGroupDailyStats(GID, START, END);
        assertNotNull(list);
        assertFalse(list.isEmpty(), "group daily stats should not be empty");
    }

    @Test
    void queryHourlyStats_returns24Elements() {
        List<Integer> hours = clickHouseStatsService.queryHourlyStats(URL1, START, END);
        assertNotNull(hours);
        assertEquals(24, hours.size());
        assertTrue(hours.stream().anyMatch(h -> h != null && h > 0), "at least one hour should have pv");
    }

    @Test
    void queryTotalStats_returnsPositiveTotalsForLink() {
        ClickHouseStatsService.TotalStats total = clickHouseStatsService.queryTotalStats(URL1, GID, START, END);
        assertNotNull(total);
        assertTrue(total.pv() > 0, "pv should be positive");
        assertTrue(total.uv() >= 0);
        assertTrue(total.uip() >= 0);
    }

    @Test
    void queryGroupTotalStats_returnsPositiveTotalsForGroup() {
        ClickHouseStatsService.TotalStats total = clickHouseStatsService.queryGroupTotalStats(GID, START, END);
        assertNotNull(total);
        assertTrue(total.pv() > 0);
    }

    @Test
    void queryBrowserStats_returnsDimensionData() {
        List<ShortLinkStatsBrowserRespDTO> list =
                clickHouseStatsService.queryBrowserStats(URL1, GID, START, END);
        assertNotNull(list);
        assertFalse(list.isEmpty(), "browser stats should not be empty");
        assertTrue(list.stream().anyMatch(b -> b.getCnt() != null && b.getCnt() > 0));
    }

    @Test
    void queryOsStats_returnsDimensionData() {
        List<?> list = clickHouseStatsService.queryOsStats(URL1, GID, START, END);
        assertNotNull(list);
        assertFalse(list.isEmpty());
    }

    @Test
    void queryDeviceStats_returnsDimensionData() {
        List<?> list = clickHouseStatsService.queryDeviceStats(URL1, GID, START, END);
        assertNotNull(list);
        assertFalse(list.isEmpty());
    }

    @Test
    void queryNetworkStats_returnsDimensionData() {
        List<?> list = clickHouseStatsService.queryNetworkStats(URL1, GID, START, END);
        assertNotNull(list);
        assertFalse(list.isEmpty());
    }

    @Test
    void queryAccessRecords_returnsPaginatedRecordsForLink() {
        ClickHouseStatsService.AccessRecordPage page = clickHouseStatsService.queryAccessRecords(URL1, GID, START, END, 1, 5);
        assertNotNull(page);
        assertTrue(page.total() > 0);
        assertFalse(page.records().isEmpty());
        assertTrue(page.records().size() <= 5);
        for (ShortLinkStatsAccessRecordRespDTO r : page.records()) {
            assertNotNull(r.getCreateTime());
            assertNotNull(r.getIp());
        }
    }

    @Test
    void queryAccessRecords_page2() {
        ClickHouseStatsService.AccessRecordPage page1 = clickHouseStatsService.queryAccessRecords(URL1, GID, START, END, 1, 3);
        ClickHouseStatsService.AccessRecordPage page2 = clickHouseStatsService.queryAccessRecords(URL1, GID, START, END, 2, 3);
        assertNotNull(page1);
        assertNotNull(page2);
        assertTrue(page1.total() == page2.total());
        assertTrue(page1.records().size() <= 3);
        assertTrue(page2.records().size() <= 3);
    }

    @Test
    void queryGroupAccessRecords_returnsPaginatedRecordsForGroup() {
        ClickHouseStatsService.AccessRecordPage page = clickHouseStatsService.queryGroupAccessRecords(GID, START, END, 1, 10);
        assertNotNull(page);
        assertTrue(page.total() >= 10, "sample data has 10 events");
        assertFalse(page.records().isEmpty());
    }
}
