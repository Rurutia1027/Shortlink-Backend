package org.tus.shortlink.svc.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessDailyRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsBrowserRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsDeviceRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsNetworkRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsOsRespDTO;
import org.tus.shortlink.svc.service.ClickHouseStatsService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ClickHouse-backed stats queries. Returns empty/zero when JdbcTemplate is absent or query fails.
 */
@Slf4j
@Service
public class ClickHouseStatsServiceImpl implements ClickHouseStatsService {

    @Autowired(required = false)
    @Qualifier("clickHouseJdbcTemplate")
    private JdbcTemplate clickHouseJdbcTemplate;

    private static final String TABLE_DAILY = "link_stats_daily";
    private static final String TABLE_HOURLY = "link_stats_hourly";
    private static final String TABLE_EVENTS = "link_stats_events";
    private static final String TABLE_BROWSER = "link_stats_browser_mv";
    private static final String TABLE_OS = "link_stats_os_mv";
    private static final String TABLE_DEVICE = "link_stats_device_mv";
    private static final String TABLE_NETWORK = "link_stats_network_mv";

    @Override
    public List<ShortLinkStatsAccessDailyRespDTO> queryDailyStats(
            String fullShortUrl, String gid, LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT stat_date, sum(pv) AS pv, sum(uv) AS uv, sum(uip) AS uip " +
                "FROM " + TABLE_DAILY + " WHERE full_short_url = ? AND gid = ? " +
                "AND stat_date >= ? AND stat_date <= ? GROUP BY stat_date, full_short_url, gid ORDER BY stat_date";
        return queryDaily(sql, fullShortUrl, gid, startDate, endDate, true);
    }

    @Override
    public List<ShortLinkStatsAccessDailyRespDTO> queryGroupDailyStats(
            String gid, LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT stat_date, sum(pv) AS pv, sum(uv) AS uv, sum(uip) AS uip " +
                "FROM " + TABLE_DAILY + " WHERE gid = ? AND stat_date >= ? AND stat_date <= ? " +
                "GROUP BY stat_date, gid ORDER BY stat_date";
        return queryDailyGroup(sql, gid, startDate, endDate);
    }

    @Override
    public List<Integer> queryHourlyStats(String fullShortUrl, LocalDate startDate, LocalDate endDate) {
        return getJdbcTemplate().map(tpl -> {
            try {
                String sql = "SELECT toUInt8(stat_hour) AS h, sum(pv) AS pv FROM " + TABLE_HOURLY +
                        " WHERE full_short_url = ? AND stat_date >= ? AND stat_date <= ? " +
                        "GROUP BY stat_date, stat_hour, full_short_url ORDER BY stat_date, stat_hour";
                List<Map<String, Object>> rows = tpl.queryForList(sql, fullShortUrl, startDate, endDate);
                int[] hours = new int[24];
                for (Map<String, Object> row : rows) {
                    Number h = (Number) row.get("h");
                    Number pv = (Number) row.get("pv");
                    if (h != null && h.intValue() >= 0 && h.intValue() < 24) {
                        hours[h.intValue()] += pv != null ? pv.intValue() : 0;
                    }
                }
                List<Integer> list = new ArrayList<>(24);
                for (int i = 0; i < 24; i++) list.add(hours[i]);
                return list;
            } catch (Exception e) {
                log.warn("ClickHouse queryHourlyStats failed: {}", e.getMessage());
                return zeroHourStats();
            }
        }).orElse(zeroHourStats());
    }

    @Override
    public List<ShortLinkStatsBrowserRespDTO> queryBrowserStats(
            String fullShortUrl, String gid, LocalDate startDate, LocalDate endDate) {
        return queryDimension(TABLE_BROWSER, "browser", startDate, endDate, fullShortUrl, gid,
                ShortLinkStatsBrowserRespDTO::new, ShortLinkStatsBrowserRespDTO::setBrowser);
    }

    @Override
    public List<ShortLinkStatsOsRespDTO> queryOsStats(
            String fullShortUrl, String gid, LocalDate startDate, LocalDate endDate) {
        return queryDimension(TABLE_OS, "os", startDate, endDate, fullShortUrl, gid,
                ShortLinkStatsOsRespDTO::new, ShortLinkStatsOsRespDTO::setOs);
    }

    @Override
    public List<ShortLinkStatsDeviceRespDTO> queryDeviceStats(
            String fullShortUrl, String gid, LocalDate startDate, LocalDate endDate) {
        return queryDimension(TABLE_DEVICE, "device", startDate, endDate, fullShortUrl, gid,
                ShortLinkStatsDeviceRespDTO::new, ShortLinkStatsDeviceRespDTO::setDevice);
    }

    @Override
    public List<ShortLinkStatsNetworkRespDTO> queryNetworkStats(
            String fullShortUrl, String gid, LocalDate startDate, LocalDate endDate) {
        return queryDimension(TABLE_NETWORK, "network", startDate, endDate, fullShortUrl, gid,
                ShortLinkStatsNetworkRespDTO::new, ShortLinkStatsNetworkRespDTO::setNetwork);
    }

    @Override
    public TotalStats queryTotalStats(String fullShortUrl, String gid, LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT sum(pv) AS pv, sum(uv) AS uv, sum(uip) AS uip FROM " + TABLE_DAILY +
                " WHERE full_short_url = ? AND gid = ? AND stat_date >= ? AND stat_date <= ?";
        return getJdbcTemplate().map(tpl -> {
            try {
                List<Map<String, Object>> rows = tpl.queryForList(sql, fullShortUrl, gid, startDate, endDate);
                if (rows.isEmpty()) return new TotalStats(0, 0, 0);
                Map<String, Object> row = rows.get(0);
                return new TotalStats(
                        toLong(row.get("pv")),
                        toLong(row.get("uv")),
                        toLong(row.get("uip"))
                );
            } catch (Exception e) {
                log.warn("ClickHouse queryTotalStats failed: {}", e.getMessage());
                return new TotalStats(0, 0, 0);
            }
        }).orElse(new TotalStats(0, 0, 0));
    }

    @Override
    public TotalStats queryGroupTotalStats(String gid, LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT sum(pv) AS pv, sum(uv) AS uv, sum(uip) AS uip FROM " + TABLE_DAILY +
                " WHERE gid = ? AND stat_date >= ? AND stat_date <= ?";
        return getJdbcTemplate().map(tpl -> {
            try {
                List<Map<String, Object>> rows = tpl.queryForList(sql, gid, startDate, endDate);
                if (rows.isEmpty()) return new TotalStats(0, 0, 0);
                Map<String, Object> row = rows.get(0);
                return new TotalStats(toLong(row.get("pv")), toLong(row.get("uv")), toLong(row.get("uip")));
            } catch (Exception e) {
                log.warn("ClickHouse queryGroupTotalStats failed: {}", e.getMessage());
                return new TotalStats(0, 0, 0);
            }
        }).orElse(new TotalStats(0, 0, 0));
    }

    private Optional<JdbcTemplate> getJdbcTemplate() {
        return Optional.ofNullable(clickHouseJdbcTemplate);
    }

    private List<ShortLinkStatsAccessDailyRespDTO> queryDaily(
            String sql, String fullShortUrl, String gid, LocalDate startDate, LocalDate endDate, boolean withUrl) {
        Optional<List<ShortLinkStatsAccessDailyRespDTO>> opt = getJdbcTemplate().map(tpl -> {
            try {
                List<Map<String, Object>> rows = withUrl
                        ? tpl.queryForList(sql, fullShortUrl, gid, startDate, endDate)
                        : tpl.queryForList(sql, gid, startDate, endDate);
                List<ShortLinkStatsAccessDailyRespDTO> result = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    result.add(ShortLinkStatsAccessDailyRespDTO.builder()
                            .date(row.get("stat_date") != null ? row.get("stat_date").toString() : null)
                            .pv(toInt(row.get("pv")))
                            .uv(toInt(row.get("uv")))
                            .uip(toInt(row.get("uip")))
                            .build());
                }
                return result;
            } catch (Exception e) {
                log.warn("ClickHouse queryDaily failed: {}", e.getMessage());
                return new ArrayList<ShortLinkStatsAccessDailyRespDTO>();
            }
        });
        return opt.orElse(Collections.<ShortLinkStatsAccessDailyRespDTO>emptyList());
    }

    private List<ShortLinkStatsAccessDailyRespDTO> queryDailyGroup(
            String sql, String gid, LocalDate startDate, LocalDate endDate) {
        return queryDaily(sql, null, gid, startDate, endDate, false);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> queryDimension(String table, String dimensionColumn,
                                       LocalDate startDate, LocalDate endDate, String fullShortUrl, String gid,
                                       java.util.function.Supplier<T> constructor,
                                       java.util.function.BiConsumer<T, String> setDimension) {
        String sql = "SELECT " + dimensionColumn + ", sum(pv) AS pv, sum(uv) AS uv " +
                "FROM " + table + " WHERE full_short_url = ? AND gid = ? " +
                "AND stat_date >= ? AND stat_date <= ? GROUP BY full_short_url, gid, " + dimensionColumn;
        return (List<T>) getJdbcTemplate().map(tpl -> {
            try {
                List<Map<String, Object>> rows = tpl.queryForList(sql, fullShortUrl, gid, startDate, endDate);
                long totalPv = rows.stream().mapToLong(r -> toLong(r.get("pv"))).sum();
                List<T> result = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    T dto = constructor.get();
                    setDimension.accept(dto, row.get(dimensionColumn) != null ? row.get(dimensionColumn).toString() : "");
                    int cnt = toInt(row.get("pv"));
                    double ratio = totalPv > 0 ? (double) cnt / totalPv : 0.0;
                    if (dto instanceof ShortLinkStatsBrowserRespDTO b) {
                        b.setCnt(cnt);
                        b.setRatio(ratio);
                    } else if (dto instanceof ShortLinkStatsOsRespDTO o) {
                        o.setCnt(cnt);
                        o.setRatio(ratio);
                    } else if (dto instanceof ShortLinkStatsDeviceRespDTO d) {
                        d.setCnt(cnt);
                        d.setRatio(ratio);
                    } else if (dto instanceof ShortLinkStatsNetworkRespDTO n) {
                        n.setCnt(cnt);
                        n.setRatio(ratio);
                    }
                    result.add(dto);
                }
                return result;
            } catch (Exception e) {
                log.warn("ClickHouse queryDimension {} failed: {}", dimensionColumn, e.getMessage());
                return Collections.<T>emptyList();
            }
        }).orElse(Collections.emptyList());
    }

    private static List<Integer> zeroHourStats() {
        List<Integer> list = new ArrayList<>(24);
        for (int i = 0; i < 24; i++) list.add(0);
        return list;
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public ClickHouseStatsService.AccessRecordPage queryAccessRecords(
            String fullShortUrl, String gid, LocalDate startDate, LocalDate endDate, int current, int size) {
        final int page = current < 1 ? 1 : current;
        final int pageSize = size < 1 ? 10 : size;
        final int offset = (page - 1) * pageSize;
        return getJdbcTemplate().map(tpl -> {
            try {
                String countSql = "SELECT count() AS cnt FROM " + TABLE_EVENTS +
                        " WHERE full_short_url = ? AND gid = ? AND toDate(event_time) >= ? AND toDate(event_time) <= ?";
                Long total = tpl.queryForObject(countSql, Long.class, fullShortUrl, gid, startDate, endDate);
                if (total == null || total == 0) {
                    return new ClickHouseStatsService.AccessRecordPage(0, Collections.emptyList());
                }
                String sql = "SELECT event_time, remote_addr, uv, os, browser, device, network, locale_code, country_code " +
                        "FROM " + TABLE_EVENTS +
                        " WHERE full_short_url = ? AND gid = ? AND toDate(event_time) >= ? AND toDate(event_time) <= ? " +
                        "ORDER BY event_time DESC LIMIT ? OFFSET ?";
                List<Map<String, Object>> rows = tpl.queryForList(sql, fullShortUrl, gid, startDate, endDate, pageSize, offset);
                List<ShortLinkStatsAccessRecordRespDTO> records = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    records.add(mapRowToAccessRecord(row));
                }
                return new ClickHouseStatsService.AccessRecordPage(total, records);
            } catch (Exception e) {
                log.warn("ClickHouse queryAccessRecords failed: {}", e.getMessage());
                return new ClickHouseStatsService.AccessRecordPage(0, Collections.emptyList());
            }
        }).orElse(new ClickHouseStatsService.AccessRecordPage(0, Collections.emptyList()));
    }

    @Override
    public ClickHouseStatsService.AccessRecordPage queryGroupAccessRecords(
            String gid, LocalDate startDate, LocalDate endDate, int current, int size) {
        final int page = current < 1 ? 1 : current;
        final int pageSize = size < 1 ? 10 : size;
        final int offset = (page - 1) * pageSize;
        return getJdbcTemplate().map(tpl -> {
            try {
                String countSql = "SELECT count() AS cnt FROM " + TABLE_EVENTS +
                        " WHERE gid = ? AND toDate(event_time) >= ? AND toDate(event_time) <= ?";
                Long total = tpl.queryForObject(countSql, Long.class, gid, startDate, endDate);
                if (total == null || total == 0) {
                    return new ClickHouseStatsService.AccessRecordPage(0, Collections.emptyList());
                }
                String sql = "SELECT event_time, full_short_url, remote_addr, uv, os, browser, device, network, locale_code, country_code " +
                        "FROM " + TABLE_EVENTS +
                        " WHERE gid = ? AND toDate(event_time) >= ? AND toDate(event_time) <= ? " +
                        "ORDER BY event_time DESC LIMIT ? OFFSET ?";
                List<Map<String, Object>> rows = tpl.queryForList(sql, gid, startDate, endDate, pageSize, offset);
                List<ShortLinkStatsAccessRecordRespDTO> records = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    records.add(mapRowToAccessRecord(row));
                }
                return new ClickHouseStatsService.AccessRecordPage(total, records);
            } catch (Exception e) {
                log.warn("ClickHouse queryGroupAccessRecords failed: {}", e.getMessage());
                return new ClickHouseStatsService.AccessRecordPage(0, Collections.emptyList());
            }
        }).orElse(new ClickHouseStatsService.AccessRecordPage(0, Collections.emptyList()));
    }

    private static ShortLinkStatsAccessRecordRespDTO mapRowToAccessRecord(Map<String, Object> row) {
        Object et = row.get("event_time");
        Date createTime = null;
        if (et != null) {
            if (et instanceof java.sql.Timestamp ts) {
                createTime = Date.from(ts.toInstant());
            } else if (et instanceof java.time.LocalDateTime ldt) {
                createTime = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            } else if (et instanceof Instant inst) {
                createTime = Date.from(inst);
            }
        }
        String locale = str(row.get("locale_code"));
        if (locale == null || locale.isEmpty()) {
            locale = str(row.get("country_code"));
        }
        return ShortLinkStatsAccessRecordRespDTO.builder()
                .createTime(createTime)
                .ip(str(row.get("remote_addr")))
                .user(str(row.get("uv")))
                .os(str(row.get("os")))
                .browser(str(row.get("browser")))
                .device(str(row.get("device")))
                .network(str(row.get("network")))
                .locale(locale)
                .build();
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
