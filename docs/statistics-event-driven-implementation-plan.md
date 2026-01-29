# Event-Driven Statistics: Two-Phase Implementation Plan

Based on the chosen solution in **statistics-design.md** (Kafka + ClickHouse, Spring Kafka), this document defines two
phases: **Phase 1- Spring + Kafka** (event publish at restoreUrl), then **Phase 2 - Spring + ClickHouse** (query service
and merge in stats API). `restoreUrl` redirect behavior is not blocked; we implement event publish and stats query in
parallel with any redirect fixes.

--- 

## Scope and Non-Goals

- **API / DTO**: No change. Existing `ShortLinkStatsRespDTO`, `ShortLinkStatReqDTO`, and stats controller endpoints stay
  as-is.
- **restoreUrl**: Implement redirect + event publish; detailed redirect testing/debugging is out of scope for this plan.
- **Controller**: Stats controller keeps calling `ShortLinkStatsService`; only the service implementation changes (Phase
  2: add ClickHouse-backed stats).

---

## Phase 1: Spring + Kafka (Event Publish at restoreUrl)

**Goal**: On each redirect, besides returning the redirect, **publish one statistics event to Kafka** (async,
non-blocking). No ClickHouse or consumer implementation in this phase.

### 1.1 Flow

```
Controller#restoreUrl(shortUri, request, response)
-> ShortLinkService#restoreUrl()
 1) Lookup original URL (existing / TODO)
 2) Publish event to Kafka (async, first-and-forget)
 3) Send 302 redirect (existing / TODO) 
```

- Event publishing must **not** block or delay the redirect.
- Event payload: **ShortLinkStatsRecordDTO** (fullShortUrl, remoteAddr, os, browser, device, network, uv, keys,
  currentDate, etc.). Add **referrer** to DTO if needed for ClickHouse dimensions later.

### 1.2 Implementation Tasks

| # | Task                         | Notes                                                                                                                                                                                                       |
|---|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | **Event publisher**          | New component (e.g. `ShortLinkStatsEventPublisher`) that uses existing `KafkaTemplate<String, ShortLinkStatsRecordDTO>` to send to `shortlink-stats-events`.                                                |
| 2 | **Build event from request** | In service layer, build `ShortLinkStatsRecordDTO` from `HttpServletRequest` (IP, User-Agent, referrer, fullShortUrl, gid, currentDate, keys). UV/UIP flags can be simplified (e.g. placeholder) in Phase 1. |
| 3 | **Wire into restoreUrl**     | In `ShortLinkServiceImpl.restoreUrl()`: after lookup, call publisher.send(event) asynchronously (e.g. `kafkaTemplate.send(...).whenComplete(...)` or wrapper), then perform redirect. No await on Kafka.    |
| 4 | **Config**                   | Reuse existing Kafka producer config and topic (`ShortlinkKafkaConfig`, `KafkaTopicConfig`). Ensure topic name matches consumer later (e.g. `shortlink-stats-events`).                                      |
| 5 | **Tests**                    | Unit test: event builder; integration test: restoreUrl triggers send (mock Kafka or Testcontainers).                                                                                                        |

### 1.3 Existing Pieces (shortlink module)

- `ShortlinkKafkaConfig` - producer factory and `KafkaTemplate<..., ShortLinkStatsRecordDTO>`
- `KafkaTopicConfig` - topic `shortlink-stats-events`
- `ShortLinkStatsRecordDTO` - in base; add `referrer` if required by design.
- `ShortLinkServiceImpl#restoreUrl()` - currently TODO; add lookup + event publish + redirect.

### 1.4 Completion Criteria

- Calling restoreUrl (with a valid short link) results in one message sent to `shortlink-stats-events` (observable via
  Kafka console consumer or tests)
- Redirect response is returned without waiting for Kafka

---

## Phase 2: Spring + ClickHouse (Query Service and Merge in Stats API)

**Goal** Implement **ClickHouse-backed statistics queries** and expose them through the existing stats API. Controller
continuous to use **DB Service (link/business data) + ClickHouse service (stats)**; no API/DTO changes.

### 2.1 Flow

```
Stats request (e.g., GET /api/shortlink/v1/stats?fullShortUrl=...&startDate=...&endDate...)
-> ShortLinkStatsController 
-> ShortLinkStatsService#oneShortLinkStats(req) / groupShortLinkStats(req)
-> ShortLinkStatsServiceImpl
   1) Optional: use existing DB/link service for link metadata if needed. 
   2) Call ClickHouseService to query by dimensions (daily, hourly, browser, os, device,network, etc.).
   3) Map ClickHouse results to ShortLinkStatsRespDTO (pv, uv, uip, daily, hourStats, browserStats, osStats, deviceStats, networkStats, ...).
   
-> Return Result<ShortLinkStatsRespDTO>
```

- No transaction spanning DB and ClickHouse required for **ready-only** stats; coordination is in the service layer (
  call DB if needed, then ClickHouse, then merge into DTO.).
- If a future requirement needs "transactional" consistency between DB and stats, that can be limited to specific write
  paths; for stats **query**, controller -> DB service + ClickHouse service is sufficient.

### 2.2 Implementation Tasks

| # | Task                          | Notes                                                                                                                                                                                                                                                                         |
|---|-------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | **ClickHouse client**         | Add ClickHouse JDBC (or HTTP) dependency; configure datasource (e.g. `clickhouse.shortlink.svc.cluster.local:8123`).                                                                                                                                                          |
| 2 | **ClickHouseService**         | New service: methods like `getDailyStats(fullShortUrl, startDate, endDate)`, `getHourlyStats(...)`, `getBrowserStats(...)`, `getOsStats(...)`, `getDeviceStats(...)`, `getNetworkStats(...)`, and optionally topIp, localeCn, etc., matching dimensions in statistics-design. |
| 3 | **Schema alignment**          | ClickHouse tables/views (link_stats_events, materialized views for daily/hourly/dimensions) as per statistics-design; ensure column names and types match what ClickHouseService expects.                                                                                     |
| 4 | **ShortLinkStatsServiceImpl** | Implement `oneShortLinkStats` / `groupShortLinkStats` (and access record methods if in scope): call ClickHouseService for each dimension, aggregate into `ShortLinkStatsRespDTO`. No change to controller or DTO.                                                             |
| 5 | **Controller**                | No change. Controller already uses ShortLinkStatsService; “通过事务” is achieved by service layer coordinating DB (if used) and ClickHouse and returning a single DTO.                                                                                                            |

### 2.3 Dependencies

- Phase 2 assumes **Kafka consumer + ClickHouse sync** (event -> Kafka -> consumer -> ClickHouse) is implement or
  stubbed so that ClickHouse has data (or test data). If consumer is not yet implemented, ClickHouseService can be built
  and tested with manual insert or fixtures.

### 2.4 Completion Criteria

- `GET /api/shortlink/v1/stats` (and group/access if implemented) returns `ShortLinkStatsRespDTO` filled from
  ClickHouse (different dimensions).
- No API or DTO changes; only service layer and new ClickHouseService.

---

## Phase 3: restoreUrl API Tune-Up (After Kafka + ClickHouse Ready)

**Goal**: Once Kafka (Phase1) and ClickHouse (Phase 2) are in place, **revisit the restoreUrl API** to ensure redirect
and event logic are correct end-to-end.

### 3.1 Tasks

- Debugging and test **restoreUrl** (lookup -> redirect 302, status/headers, target URL)
- Ensure **event publish** runs after lookup, does not block redirect, and payload (fullShortUrl, gid, IP, etc.) is
  correct.
- Optional: add integration test (restoreUrl -> Kafka message + 302)

### 3.2 Completion Criteria

- restoreUrl logic is verified; redirect and event publish both behave as expected.

--- 

## Phase 4: UIP + Redis Cache (1-Day IP State)

**Goal**: Accurately compute **Unique (Unique IP)** by recording "this IP has visited (for this link/date)" in **Redis
**, with **1-day TTL**, so each event can set **uipFirstFlag** correctly.

### 4.1 Rationale

- UIP = unique IPs in the time window. To know if an IP is “first time today” (or per link+date), we need to check/store
  state.
- **Redis cache**: key e.g. `shortlink:stats:uip:{fullShortUrl}:{date}:{ip}` (or similar), TTL 1 day. On restoreUrl: if
  key absent → first visit (uipFirstFlag=true), then set key; if key exists → not first (uipFirstFlag=false).
- Event payload **ShortLinkStatsRecordDTO** already has **uipFirstFlag**; Phase 4 fills it using Redis before publishing
  to Kafka.

### 4.2 Tasks

| # | Task                      | Notes                                                                                                                                          |
|---|---------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | **Redis key design**      | e.g. `shortlink:stats:uip:{fullShortUrl}:{yyyyMMdd}:{remoteAddr}` with TTL 86400s (1 day). Use existing Redis/CacheService.                    |
| 2 | **UIP first-visit check** | In restoreUrl path (before building event): check if key exists; set uipFirstFlag=true if absent, then set key; uipFirstFlag=false if present. |
| 3 | **Wire into event**       | Pass uipFirstFlag into ShortLinkStatsRecordDTO so Kafka/ClickHouse consumer can aggregate UIP correctly.                                       |
| 4 | **Tests**                 | Unit/integration: same IP first call → uipFirstFlag=true; second call within TTL → uipFirstFlag=false; after TTL expiry → true again.          |

### 4.3 Completion Criteria

- UIP first-visit state is stored in Redis with 1-day TTL; events carry correct uipFirstFlag; downstream (ClickHouse)
  UIP metrics are accurate.

---

## Phase 5: Full Event Flow Integration

**Goal**: After restoreUrl is tuned (Phase 3) and UIP cache is in place (Phase 4), run **end-to-end functional
integration** of the whole event pipeline.

### 5.1 Flow

```
restoreUrl(shortUri, request, response)
  → Lookup URL
  → UIP: Redis check/set (1-day TTL), set uipFirstFlag
  → Build ShortLinkStatsRecordDTO (with uipFirstFlag, uv, etc.)
  → Publish event to Kafka (async)
  → 302 redirect
Kafka → Consumer → ClickHouse (raw events + materialized views)
Stats API → ClickHouseService → ShortLinkStatsRespDTO (pv, uv, uip, dimensions)
```

### 5.2 Tasks

- **Functional E2E**: restoreUrl → verify event in Kafka → verify row/aggregation in ClickHouse → call stats API →
  verify pv/uv/uip and dimension breakdowns.
- **Data consistency**: Same link, same IP, multiple visits: PV increases, UIP stays 1 within day; after 1 day, UIP can
  increment if design allows.
- **Optional**: UV (unique visitor) with Redis in same way (e.g. cookie/fingerprint + Redis 1-day) if not yet done; then
  full UV/UIP validation.

### 5.3 Completion Criteria

- Full event flow (restoreUrl → Kafka → ClickHouse → stats API) works; UIP and other metrics are correct in API
  responses.

---

## Order of Work

1. **Phase 1:** Spring + Kafka – event publish at restoreUrl. Validate events in Kafka.
2. **Phase 2:** Spring + ClickHouse – ClickHouseService + ShortLinkStatsServiceImpl; stats API returns ClickHouse-backed
   data. (Kafka consumer + ClickHouse sync as needed.)
3. **Phase 3:** With Kafka and ClickHouse ready, **revisit restoreUrl API** – debug and test redirect + event logic
   until correct.
4. **Phase 4:** **UIP + Redis cache** – 1-day TTL for IP visit state; set uipFirstFlag in event; ensure UIP is accurate
   in ClickHouse and stats API.
5. **Phase 5:** **Full event flow integration** – E2E functional test of restoreUrl → Kafka → ClickHouse → stats API,
   with correct UIP (and UV if applicable).

---

## Reference

- **statistics-design.md** – Kafka + ClickHouse architecture, event payload, ClickHouse schema, materialized views, and
  query patterns.
- **Existing code:** `ShortLinkServiceImpl.restoreUrl`, `ShortlinkKafkaConfig`, `KafkaTopicConfig`,
  `ShortLinkStatsServiceImpl`, `ShortLinkStatsController`, `ShortLinkStatsRespDTO`, `ShortLinkStatsRecordDTO`.




