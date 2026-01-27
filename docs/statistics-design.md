# Event-Driven Statistics (PV/UV) Design Document

## Executive Summary

This document outlines the design for implementing event-driven statistics collection and aggregation for PV (Page Views) and UV (Unique Visitors) metrics in the ShortLink platform. The design focuses on decoupling statistics collection from the redirect path, enabling scalable real-time analytics while maintaining low latency for core redirect operations.

---

## 1. Current State Analysis

### 1.1 Existing Components

#### Controllers (API Layer)
- **`ShortLinkStatsController`** (`/api/shortlink/v1/stats`)
  - `GET /stats` - Single short link statistics
  - `GET /stats/group` - Group statistics
  - `GET /stats/access` - Access records for single link
  - `GET /stats/group/access` - Access records for group

- **`ShortLinkController`** (`/api/shortlink/v1`)
  - `GET /{shortUri}` - Redirect endpoint (currently TODO, no statistics collection)

#### Service Layer
- **`ShortLinkStatsService`** - Interface defined, implementation returns `null` (mocked)
- **`ShortLinkStatsServiceImpl`** - All methods return `null` with TODO comments

#### Data Transfer Objects
- **`ShortLinkStatsRecordDTO`** - Event payload structure:
  - `fullShortUrl`, `remoteAddr`, `os`, `browser`, `device`, `network`
  - `uv`, `uvFirstFlag`, `uipFirstFlag`
  - `keys`, `currentDate`

- **`ShortLinkStatsRespDTO`** - Response structure:
  - Aggregated metrics: `pv`, `uv`, `uip`
  - Time-series: `daily`, `hourStats`, `weekdayStats`
  - Dimension breakdowns: `browserStats`, `osStats`, `deviceStats`, `networkStats`, `localeCnStats`, `topIpStats`

#### Infrastructure
- **Redis Streams** - Constants defined:
  - `SHORT_LINK_STATS_STREAM_TOPIC_KEY = "short-link:stats-stream"`
  - `SHORT_LINK_STATS_STREAM_GROUP_KEY = "short-link:stats-stream:only-group"`
  - `SHORT_LINK_STATS_UV_KEY = "short-link:stats:uv:"`
  - `SHORT_LINK_STATS_UIP_KEY = "short-link:stats:uip:"`

- **Database Schema**
  - `t_link` table exists (no statistics columns)
  - No dedicated statistics tables yet

### 1.2 Gaps Identified

1. **No Event Publishing**: Redirect endpoint (`restoreUrl`) doesn't publish statistics events
2. **No Event Consumption**: No consumer to process statistics events
3. **No Aggregation Logic**: No service to calculate PV/UV from events
4. **No Storage Strategy**: No decision on where/how to store aggregated statistics
5. **No UV/UIP Detection**: No logic to determine first-time visitors

---

## 2. Design Goals & Requirements

### 2.1 Functional Requirements

1. **Event Collection**
   - Capture access events on every redirect
   - Extract user context (IP, User-Agent, referrer)
   - Determine UV/UIP first-visit flags
   - Non-blocking, asynchronous event publishing

2. **Statistics Aggregation**
   - Calculate PV (total page views)
   - Calculate UV (unique visitors) using cookie/device fingerprint
   - Calculate UIP (unique IPs)
   - Time-based aggregations (hourly, daily, weekly)
   - Dimension-based breakdowns (browser, OS, device, network, region)

3. **Query Interface**
   - Query statistics for single short link
   - Query statistics for link group (gid)
   - Filter by date range
   - Paginated access records

### 2.2 Non-Functional Requirements

1. **Performance**
   - Redirect latency < 10ms (event publishing must not block)
   - Statistics query latency < 100ms for aggregated data
   - Support 10K+ redirects/second

2. **Scalability**
   - Horizontal scaling of event consumers
   - Support for millions of events per day
   - Efficient storage and retrieval

3. **Reliability**
   - Event delivery guarantees (at-least-once)
   - Graceful degradation if statistics system is down
   - Data consistency for aggregations

4. **Maintainability**
   - Clear separation of concerns
   - Testable components
   - Observable (metrics, logs, traces)

---

## 3. Architecture Overview

### 3.1 Event-Driven Flow

```
┌─────────────────┐
│  User Request   │
│  GET /{shortUri}│
└────────┬────────┘
         │
         ▼
┌─────────────────────────────┐
│  ShortLinkController        │
│  restoreUrl()               │
│  1. Lookup original URL     │
│  2. Publish event (async)   │
│  3. Return 302 redirect     │
└────────┬────────────────────┘
         │
         ├──────────────────────────┐
         │                          │
         ▼                          ▼
┌──────────────────┐      ┌─────────────────────┐
│ 302 Redirect     │      │  Event Publisher    │
│ (immediate)      │      │  (non-blocking)     │
└──────────────────┘      └──────────┬──────────┘
                                      │
                                      ▼
                            ┌─────────────────────┐
                            │  Redis Stream /     │
                            │  Message Queue      │
                            └──────────┬──────────┘
                                       │
                                       ▼
                            ┌─────────────────────┐
                            │  Event Consumer     │
                            │  (Statistics Worker)│
                            └──────────┬──────────┘
                                       │
                                       ▼
                            ┌─────────────────────┐
                            │  Aggregation Service│
                            │  (PV/UV Calculation)│
                            └──────────┬──────────┘
                                       │
                                       ▼
                            ┌─────────────────────┐
                            │  Storage Layer      │
                            │  (DB/Cache/TSDB)    │
                            └─────────────────────┘
```

### 3.2 Component Responsibilities

1. **Event Publisher** - Publishes access events asynchronously
2. **Event Queue/Stream** - Buffers events for processing
3. **Event Consumer** - Processes events in batches
4. **Aggregation Service** - Calculates PV/UV metrics
5. **Storage Layer** - Persists aggregated statistics
6. **Query Service** - Retrieves statistics for API responses

---

## 4. Solution Options

### Solution 1: Redis Streams + PostgreSQL Aggregation

#### Architecture
- **Event Publishing**: Redis Streams (using Redisson)
- **Event Consumption**: Spring scheduled tasks consuming from Redis Streams
- **UV/UIP Detection**: Redis Sets/Bloom Filters for deduplication
- **Aggregation**: In-memory aggregation, periodic flush to PostgreSQL
- **Storage**: PostgreSQL tables for aggregated statistics

#### Implementation Details

**Event Publishing:**
```java
// In ShortLinkServiceImpl.restoreUrl()
ShortLinkStatsRecordDTO event = buildStatsRecord(fullShortUrl, request);
redissonClient.getStream(STREAM_KEY).add(StreamMessageId.autoGenerate(), event);
```

**Event Consumption:**
- Consumer group reads from Redis Streams
- Batch processing (100-1000 events per batch)
- UV detection using Redis Sets: `SADD short-link:stats:uv:{fullShortUrl}:{date} {uv}`
- UIP detection using Redis Sets: `SADD short-link:stats:uip:{fullShortUrl}:{date} {ip}`

**Aggregation Storage:**
```sql
CREATE TABLE t_link_stats_daily (
    uuid VARCHAR(255) PRIMARY KEY,
    full_short_url VARCHAR(255) NOT NULL,
    gid VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    pv INTEGER DEFAULT 0,
    uv INTEGER DEFAULT 0,
    uip INTEGER DEFAULT 0,
    created_date TIMESTAMP,
    modified_date TIMESTAMP,
    UNIQUE(full_short_url, stat_date)
);

CREATE TABLE t_link_stats_hourly (
    uuid VARCHAR(255) PRIMARY KEY,
    full_short_url VARCHAR(255) NOT NULL,
    stat_date DATE NOT NULL,
    stat_hour INTEGER NOT NULL,
    pv INTEGER DEFAULT 0,
    UNIQUE(full_short_url, stat_date, stat_hour)
);

CREATE TABLE t_link_stats_dimension (
    uuid VARCHAR(255) PRIMARY KEY,
    full_short_url VARCHAR(255) NOT NULL,
    stat_date DATE NOT NULL,
    dimension_type VARCHAR(32) NOT NULL, -- 'browser', 'os', 'device', 'network'
    dimension_value VARCHAR(128) NOT NULL,
    pv INTEGER DEFAULT 0,
    UNIQUE(full_short_url, stat_date, dimension_type, dimension_value)
);
```

**Pros:**
- ✅ Leverages existing Redis infrastructure
- ✅ Redis Streams provide persistence and consumer groups
- ✅ Low latency for event publishing
- ✅ PostgreSQL provides ACID guarantees for aggregations
- ✅ Good query performance with proper indexing
- ✅ Supports complex queries (JOINs, aggregations)

**Cons:**
- ❌ Redis memory usage for UV/UIP Sets (can be large)
- ❌ Requires periodic cleanup of Redis Sets
- ❌ PostgreSQL may become bottleneck for high write volumes
- ❌ Complex aggregation logic in application layer

**Scalability:**
- Event publishing: 50K+ events/sec (Redis Streams)
- Aggregation: Limited by PostgreSQL write capacity (~5K writes/sec per instance)
- Query: Excellent with proper indexing

---

### Solution 2: Redis Streams + ClickHouse Aggregation

#### Architecture
- **Event Publishing**: Redis Streams (same as Solution 1)
- **Event Consumption**: Batch consumer writing directly to ClickHouse
- **UV/UIP Detection**: ClickHouse `uniqExact()` functions
- **Aggregation**: ClickHouse Materialized Views
- **Storage**: ClickHouse tables

#### Implementation Details

**ClickHouse Schema:**
```sql
CREATE TABLE link_stats_events (
    event_time DateTime,
    full_short_url String,
    gid String,
    remote_addr String,
    uv String,
    os String,
    browser String,
    device String,
    network String
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_time)
ORDER BY (full_short_url, event_time);

CREATE MATERIALIZED VIEW link_stats_daily_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (full_short_url, stat_date)
AS SELECT
    toDate(event_time) as stat_date,
    full_short_url,
    gid,
    count() as pv,
    uniqExact(uv) as uv,
    uniqExact(remote_addr) as uip
FROM link_stats_events
GROUP BY stat_date, full_short_url, gid;
```

**Pros:**
- ✅ Excellent write performance (100K+ inserts/sec)
- ✅ Built-in aggregation functions (uniqExact, count)
- ✅ Columnar storage, efficient for analytics queries
- ✅ Automatic partitioning and compression
- ✅ Materialized views for pre-aggregation

**Cons:**
- ❌ Additional infrastructure (ClickHouse cluster)
- ❌ Learning curve for ClickHouse SQL
- ❌ Less suitable for transactional queries
- ❌ Higher operational complexity

**Scalability:**
- Event ingestion: 100K+ events/sec
- Aggregation: Real-time via Materialized Views
- Query: Excellent for time-series analytics

---

### Solution 3: Message Queue (RabbitMQ/Kafka) + PostgreSQL

#### Architecture
- **Event Publishing**: RabbitMQ/Kafka topic
- **Event Consumption**: Multiple consumers for parallel processing
- **UV/UIP Detection**: Redis Sets (same as Solution 1)
- **Aggregation**: In-memory, flush to PostgreSQL
- **Storage**: PostgreSQL (same schema as Solution 1)

#### Implementation Details

**RabbitMQ Setup:**
- Exchange: `shortlink.stats.exchange` (topic)
- Queue: `shortlink.stats.queue` (durable, multiple consumers)
- Routing: `stats.access.{gid}`

**Kafka Setup:**
- Topic: `shortlink-stats-events` (partitioned by gid)
- Consumer Group: `shortlink-stats-aggregator`
- Partitions: 10-20 for parallel processing

**Pros:**
- ✅ Better message durability guarantees
- ✅ Supports multiple consumer groups
- ✅ Better for microservices architecture
- ✅ Kafka provides replay capability
- ✅ RabbitMQ provides dead-letter queues

**Cons:**
- ❌ Additional infrastructure complexity
- ❌ Higher latency than Redis Streams
- ❌ More operational overhead
- ❌ Kafka requires Zookeeper/KRaft

**Scalability:**
- Event publishing: 20K+ events/sec (RabbitMQ), 100K+ (Kafka)
- Aggregation: Limited by PostgreSQL (same as Solution 1)
- Query: Same as Solution 1

---

### Solution 4: Hybrid Approach (Redis Streams + Redis TimeSeries + PostgreSQL)

#### Architecture
- **Event Publishing**: Redis Streams
- **Event Consumption**: Batch consumer
- **UV/UIP Detection**: Redis Sets
- **Real-time Aggregation**: Redis TimeSeries module (for recent data)
- **Historical Aggregation**: PostgreSQL (for older data)
- **Storage**: Redis TimeSeries (last 30 days) + PostgreSQL (older data)

#### Implementation Details

**Redis TimeSeries:**
```redis
TS.CREATE shortlink:pv:{fullShortUrl}:{date} RETENTION 2592000
TS.CREATE shortlink:uv:{fullShortUrl}:{date} RETENTION 2592000
TS.ADD shortlink:pv:{fullShortUrl}:{date} * 1
```

**Pros:**
- ✅ Very fast queries for recent data (Redis)
- ✅ Cost-effective for historical data (PostgreSQL)
- ✅ Best of both worlds
- ✅ Leverages existing Redis infrastructure

**Cons:**
- ❌ Requires Redis TimeSeries module (Redis Stack)
- ❌ More complex query logic (check Redis first, fallback to PostgreSQL)
- ❌ Data migration between Redis and PostgreSQL

**Scalability:**
- Event publishing: Same as Solution 1
- Real-time queries: Excellent (Redis)
- Historical queries: Same as Solution 1

---

## 5. Trade-off Analysis

| Criteria | Solution 1<br/>(Redis Streams + PG) | Solution 2<br/>(Redis Streams + ClickHouse) | Solution 3<br/>(MQ + PG) | Solution 4<br/>(Hybrid) |
|----------|-------------------------------------|----------------------------------------------|---------------------------|--------------------------|
| **Implementation Complexity** | Medium | High | Medium-High | High |
| **Infrastructure Cost** | Low | Medium-High | Medium | Medium |
| **Write Performance** | Good (5K/sec) | Excellent (100K+/sec) | Good (20K/sec) | Good (5K/sec) |
| **Query Performance** | Good | Excellent | Good | Excellent (recent) |
| **Operational Overhead** | Low | High | Medium | Medium-High |
| **Data Durability** | Good | Excellent | Excellent | Good |
| **Scalability** | Medium | High | Medium-High | High |
| **Learning Curve** | Low | Medium | Medium | Medium |
| **Existing Infrastructure** | ✅ Redis + PG | ❌ Need ClickHouse | ❌ Need MQ | ✅ Redis + PG |

---

## 6. Recommended Solution

### Solution 1: Redis Streams + PostgreSQL Aggregation

**Rationale:**
1. **Leverages Existing Infrastructure**: Uses Redis (already in place) and PostgreSQL (already configured)
2. **Balanced Complexity**: Moderate implementation effort, well-understood technologies
3. **Good Performance**: Sufficient for expected load (10K+ redirects/sec)
4. **Cost-Effective**: No additional infrastructure required
5. **Maintainable**: Team familiarity with Redis and PostgreSQL

### Implementation Phases

#### Phase 1: Event Publishing (Week 1-2)
1. Implement `ShortLinkStatsRecordDTO` builder
2. Add User-Agent parsing (browser, OS, device detection)
3. Implement async event publisher using Redis Streams
4. Integrate into `restoreUrl()` method
5. Add error handling and fallback (graceful degradation)

#### Phase 2: Event Consumption & UV Detection (Week 2-3)
1. Implement Redis Streams consumer group
2. Add UV detection using Redis Sets with TTL
3. Add UIP detection using Redis Sets with TTL
4. Implement batch processing (100-1000 events)
5. Add monitoring and alerting

#### Phase 3: Aggregation & Storage (Week 3-4)
1. Create PostgreSQL statistics tables (daily, hourly, dimension)
2. Implement aggregation logic
3. Implement periodic flush (every 5-10 minutes)
4. Add database indexes for query performance

#### Phase 4: Query Service (Week 4-5)
1. Implement `ShortLinkStatsServiceImpl` methods
2. Add query logic for single link statistics
3. Add query logic for group statistics
4. Add access record pagination
5. Add caching layer for frequently accessed statistics

#### Phase 5: Testing & Optimization (Week 5-6)
1. Load testing (10K+ redirects/sec)
2. Query performance optimization
3. Redis memory optimization (Set cleanup strategy)
4. Database query optimization (indexes, partitioning)

---

## 7. Detailed Design: Solution 1

### 7.1 Event Publishing Service

```java
@Service
@RequiredArgsConstructor
public class ShortLinkStatsEventPublisher {
    private final RedissonClient redissonClient;
    private final UserAgentParser userAgentParser;
    
    @Async
    public void publishAccessEvent(String fullShortUrl, String gid, 
                                   HttpServletRequest request) {
        try {
            ShortLinkStatsRecordDTO event = ShortLinkStatsRecordDTO.builder()
                .fullShortUrl(fullShortUrl)
                .gid(gid)
                .remoteAddr(getClientIp(request))
                .uv(generateUv(request))
                .os(userAgentParser.parseOS(request.getHeader("User-Agent")))
                .browser(userAgentParser.parseBrowser(request.getHeader("User-Agent")))
                .device(userAgentParser.parseDevice(request.getHeader("User-Agent")))
                .network(parseNetwork(request))
                .currentDate(new Date())
                .keys(UUID.randomUUID().toString())
                .build();
            
            // Check UV/UIP first visit flags
            checkFirstVisitFlags(event);
            
            // Publish to Redis Stream
            RStream<String, Object> stream = redissonClient.getStream(
                RedisConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY);
            stream.add(StreamMessageId.autoGenerate(), event);
        } catch (Exception e) {
            log.error("Failed to publish stats event", e);
            // Graceful degradation - don't fail redirect
        }
    }
    
    private void checkFirstVisitFlags(ShortLinkStatsRecordDTO event) {
        String dateKey = DateUtil.formatDate(event.getCurrentDate(), "yyyyMMdd");
        String uvKey = RedisConstant.SHORT_LINK_STATS_UV_KEY + 
                      event.getFullShortUrl() + ":" + dateKey;
        String uipKey = RedisConstant.SHORT_LINK_STATS_UIP_KEY + 
                        event.getFullShortUrl() + ":" + dateKey;
        
        RSet<String> uvSet = redissonClient.getSet(uvKey);
        RSet<String> uipSet = redissonClient.getSet(uipKey);
        
        event.setUvFirstFlag(uvSet.add(event.getUv()));
        event.setUipFirstFlag(uipSet.add(event.getRemoteAddr()));
        
        // Set TTL to 2 days (cleanup old sets)
        uvSet.expire(2, TimeUnit.DAYS);
        uipSet.expire(2, TimeUnit.DAYS);
    }
}
```

### 7.2 Event Consumer Service

```java
@Service
@RequiredArgsConstructor
public class ShortLinkStatsEventConsumer {
    private final RedissonClient redissonClient;
    private final ShortLinkStatsAggregationService aggregationService;
    
    @Scheduled(fixedDelay = 5000) // Every 5 seconds
    public void consumeEvents() {
        RStream<String, Object> stream = redissonClient.getStream(
            RedisConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY);
        RStreamGroup<String, Object> group = stream.getGroup(
            RedisConstant.SHORT_LINK_STATS_STREAM_GROUP_KEY);
        
        // Read up to 1000 pending messages
        Map<StreamMessageId, Map<String, Object>> messages = 
            group.read(1000, 100, TimeUnit.MILLISECONDS);
        
        if (messages.isEmpty()) {
            return;
        }
        
        List<ShortLinkStatsRecordDTO> events = messages.values().stream()
            .map(this::toStatsRecord)
            .collect(Collectors.toList());
        
        // Process in batches
        aggregationService.aggregate(events);
        
        // Acknowledge processed messages
        messages.keySet().forEach(group::ack);
    }
}
```

### 7.3 Aggregation Service

```java
@Service
@RequiredArgsConstructor
public class ShortLinkStatsAggregationService {
    private final QueryService queryService;
    
    // In-memory aggregation cache
    private final Map<String, DailyStats> dailyStatsCache = new ConcurrentHashMap<>();
    
    public void aggregate(List<ShortLinkStatsRecordDTO> events) {
        // Group by (fullShortUrl, date)
        Map<String, List<ShortLinkStatsRecordDTO>> grouped = events.stream()
            .collect(Collectors.groupingBy(e -> 
                e.getFullShortUrl() + ":" + 
                DateUtil.formatDate(e.getCurrentDate(), "yyyyMMdd")));
        
        for (Map.Entry<String, List<ShortLinkStatsRecordDTO>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            List<ShortLinkStatsRecordDTO> dayEvents = entry.getValue();
            
            DailyStats stats = dailyStatsCache.computeIfAbsent(key, 
                k -> new DailyStats());
            
            // Aggregate PV, UV, UIP
            stats.pv += dayEvents.size();
            stats.uv += dayEvents.stream()
                .filter(ShortLinkStatsRecordDTO::getUvFirstFlag)
                .count();
            stats.uip += dayEvents.stream()
                .filter(ShortLinkStatsRecordDTO::getUipFirstFlag)
                .count();
            
            // Aggregate by dimensions
            aggregateDimensions(stats, dayEvents);
        }
    }
    
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void flushToDatabase() {
        // Flush aggregated stats to PostgreSQL
        // Clear cache after successful flush
    }
}
```

### 7.4 Query Service Implementation

```java
@Service
@RequiredArgsConstructor
public class ShortLinkStatsServiceImpl implements ShortLinkStatsService {
    private final QueryService queryService;
    private final CacheService cacheService;
    
    @Override
    public ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam) {
        // Check cache first
        String cacheKey = buildCacheKey(requestParam);
        ShortLinkStatsRespDTO cached = cacheService.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Query from database
        ShortLinkStatsRespDTO stats = queryStatsFromDB(requestParam);
        
        // Cache for 5 minutes
        cacheService.set(cacheKey, stats, 5, TimeUnit.MINUTES);
        
        return stats;
    }
    
    private ShortLinkStatsRespDTO queryStatsFromDB(ShortLinkStatsReqDTO req) {
        // Query daily stats
        List<ShortLinkStatsAccessDailyRespDTO> daily = queryDailyStats(
            req.getFullShortUrl(), req.getGid(), req.getStartDate(), req.getEndDate());
        
        // Query hourly stats
        List<Integer> hourly = queryHourlyStats(
            req.getFullShortUrl(), req.getStartDate(), req.getEndDate());
        
        // Query dimension stats
        List<ShortLinkStatsBrowserRespDTO> browserStats = queryBrowserStats(...);
        List<ShortLinkStatsOsRespDTO> osStats = queryOsStats(...);
        // ... other dimensions
        
        // Calculate totals
        int totalPv = daily.stream().mapToInt(ShortLinkStatsAccessDailyRespDTO::getPv).sum();
        int totalUv = daily.stream().mapToInt(ShortLinkStatsAccessDailyRespDTO::getUv).sum();
        int totalUip = daily.stream().mapToInt(ShortLinkStatsAccessDailyRespDTO::getUip).sum();
        
        return ShortLinkStatsRespDTO.builder()
            .pv(totalPv)
            .uv(totalUv)
            .uip(totalUip)
            .daily(daily)
            .hourStats(hourly)
            .browserStats(browserStats)
            .osStats(osStats)
            // ... other fields
            .build();
    }
}
```

---

## 8. Database Schema Design

### 8.1 Daily Statistics Table

```sql
CREATE TABLE t_link_stats_daily (
    uuid VARCHAR(255) PRIMARY KEY,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    full_short_url VARCHAR(255) NOT NULL,
    gid VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    pv INTEGER DEFAULT 0,
    uv INTEGER DEFAULT 0,
    uip INTEGER DEFAULT 0,
    CONSTRAINT uk_link_stats_daily UNIQUE (full_short_url, stat_date)
);

CREATE INDEX idx_link_stats_daily_gid_date ON t_link_stats_daily(gid, stat_date);
CREATE INDEX idx_link_stats_daily_date ON t_link_stats_daily(stat_date);
```

### 8.2 Hourly Statistics Table

```sql
CREATE TABLE t_link_stats_hourly (
    uuid VARCHAR(255) PRIMARY KEY,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    full_short_url VARCHAR(255) NOT NULL,
    stat_date DATE NOT NULL,
    stat_hour INTEGER NOT NULL CHECK (stat_hour >= 0 AND stat_hour <= 23),
    pv INTEGER DEFAULT 0,
    CONSTRAINT uk_link_stats_hourly UNIQUE (full_short_url, stat_date, stat_hour)
);

CREATE INDEX idx_link_stats_hourly_date ON t_link_stats_hourly(stat_date, stat_hour);
```

### 8.3 Dimension Statistics Table

```sql
CREATE TABLE t_link_stats_dimension (
    uuid VARCHAR(255) PRIMARY KEY,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    full_short_url VARCHAR(255) NOT NULL,
    gid VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    dimension_type VARCHAR(32) NOT NULL, -- 'browser', 'os', 'device', 'network', 'locale'
    dimension_value VARCHAR(128) NOT NULL,
    pv INTEGER DEFAULT 0,
    uv INTEGER DEFAULT 0,
    CONSTRAINT uk_link_stats_dimension UNIQUE (full_short_url, stat_date, dimension_type, dimension_value)
);

CREATE INDEX idx_link_stats_dimension_date ON t_link_stats_dimension(stat_date, dimension_type);
```

### 8.4 Access Records Table (Optional - for detailed access logs)

```sql
CREATE TABLE t_link_stats_access_record (
    uuid VARCHAR(255) PRIMARY KEY,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    full_short_url VARCHAR(255) NOT NULL,
    gid VARCHAR(64) NOT NULL,
    remote_addr VARCHAR(64),
    uv VARCHAR(128),
    os VARCHAR(64),
    browser VARCHAR(64),
    device VARCHAR(64),
    network VARCHAR(64),
    access_time TIMESTAMP NOT NULL
);

CREATE INDEX idx_link_stats_access_url_time ON t_link_stats_access_record(full_short_url, access_time);
CREATE INDEX idx_link_stats_access_gid_time ON t_link_stats_access_record(gid, access_time);
```

**Note**: Access records table can be large. Consider:
- Partitioning by date
- Archiving old records (>90 days) to cold storage
- Or use ClickHouse/Elasticsearch for access logs only

---

## 9. Performance Considerations

### 9.1 Redis Memory Management

**UV/UIP Sets Memory Usage:**
- Average UV per link per day: ~1000
- Memory per Set entry: ~50 bytes
- Per link per day: ~50KB
- 10K active links: ~500MB per day
- **Solution**: Set TTL to 2 days, automatic cleanup

**Redis Streams Memory:**
- Average event size: ~200 bytes
- Retention: 24 hours
- 10K events/sec: ~173GB per day (too large!)
- **Solution**: Keep retention to 1 hour, ensure consumers process quickly

### 9.2 Database Optimization

**Indexing Strategy:**
- Primary indexes on (full_short_url, stat_date) for daily stats
- Index on (gid, stat_date) for group queries
- Index on (stat_date) for time-range queries

**Partitioning (Future):**
- Partition `t_link_stats_daily` by month
- Partition `t_link_stats_access_record` by week (if implemented)

**Query Optimization:**
- Use materialized views for common aggregations
- Cache frequently accessed statistics
- Batch queries where possible

### 9.3 Caching Strategy

**Cache Layers:**
1. **L1 Cache (Local)**: Caffeine cache for hot statistics (TTL: 1 minute)
2. **L2 Cache (Redis)**: Cached aggregated statistics (TTL: 5 minutes)
3. **Database**: Source of truth for historical data

**Cache Invalidation:**
- Invalidate on new statistics flush
- Use cache-aside pattern
- Consider cache warming for popular links

---

## 10. Monitoring & Observability

### 10.1 Metrics to Track

1. **Event Publishing**
   - `shortlink.stats.events.published.total`
   - `shortlink.stats.events.published.failed`
   - `shortlink.stats.events.publish.latency`

2. **Event Consumption**
   - `shortlink.stats.events.consumed.total`
   - `shortlink.stats.events.consumed.lag` (Redis Stream lag)
   - `shortlink.stats.events.consumption.latency`

3. **Aggregation**
   - `shortlink.stats.aggregation.duration`
   - `shortlink.stats.aggregation.batch.size`
   - `shortlink.stats.flush.duration`

4. **Query Performance**
   - `shortlink.stats.query.duration`
   - `shortlink.stats.query.cache.hit.rate`

5. **Storage**
   - `shortlink.stats.redis.memory.usage`
   - `shortlink.stats.db.connection.pool.usage`

### 10.2 Alerts

- Event consumption lag > 1 minute
- Event publishing failure rate > 1%
- Aggregation flush failures
- Database connection pool exhaustion
- Redis memory usage > 80%

---

## 11. Migration Plan

### Phase 1: Infrastructure Setup (Week 1)
- [ ] Create database tables (Flyway migration)
- [ ] Configure Redis Streams consumer group
- [ ] Set up monitoring dashboards

### Phase 2: Event Publishing (Week 2)
- [ ] Implement `ShortLinkStatsEventPublisher`
- [ ] Add User-Agent parsing library
- [ ] Integrate into `restoreUrl()` method
- [ ] Add unit tests

### Phase 3: Event Consumption (Week 3)
- [ ] Implement `ShortLinkStatsEventConsumer`
- [ ] Implement UV/UIP detection logic
- [ ] Add integration tests
- [ ] Load testing

### Phase 4: Aggregation (Week 4)
- [ ] Implement `ShortLinkStatsAggregationService`
- [ ] Implement database flush logic
- [ ] Add batch processing optimization
- [ ] Performance testing

### Phase 5: Query Service (Week 5)
- [ ] Implement `ShortLinkStatsServiceImpl` methods
- [ ] Add caching layer
- [ ] Add query optimization
- [ ] API testing

### Phase 6: Production Rollout (Week 6)
- [ ] Deploy to staging environment
- [ ] Monitor for 1 week
- [ ] Gradual rollout to production (10% → 50% → 100%)
- [ ] Documentation

---

## 12. Future Enhancements

1. **Real-time Dashboard**: WebSocket updates for live statistics
2. **Advanced Analytics**: Funnel analysis, conversion tracking
3. **Geographic Analytics**: IP-based location detection
4. **A/B Testing**: Statistics for different link variants
5. **Export Functionality**: CSV/Excel export of statistics
6. **Alerting**: Notifications for unusual traffic patterns
7. **Machine Learning**: Anomaly detection, traffic prediction

---

## 13. References

### Controllers
- `ShortLinkStatsController` - `/api/shortlink/v1/stats`
- `ShortLinkController` - `/api/shortlink/v1/{shortUri}` (redirect endpoint)

### DTOs
- `ShortLinkStatsRecordDTO` - Event payload structure
- `ShortLinkStatsRespDTO` - Response structure with PV/UV/UIP
- `ShortLinkStatsReqDTO` - Query request parameters

### Constants
- `RedisConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY`
- `RedisConstant.SHORT_LINK_STATS_UV_KEY`
- `RedisConstant.SHORT_LINK_STATS_UIP_KEY`

### Documentation
- `docs/observability.md` - Current observability strategy
- Redis Streams: https://redis.io/docs/data-types/streams/
- Redisson Streams: https://github.com/redisson/redisson/wiki/7.-distributed-collections#715-stream

---

## Appendix A: User-Agent Parsing

Recommend using a library like:
- **User-Agent-Utils** (Java)
- **ua-parser** (Java port)
- **BrowserDetector** (Lightweight)

Example:
```java
UserAgentParser parser = new UserAgentParser();
UserAgent ua = parser.parse(request.getHeader("User-Agent"));
String browser = ua.getBrowser().getName();
String os = ua.getOperatingSystem().getName();
String device = ua.getDeviceType().getName();
```

---

## Appendix B: UV Generation Strategy

**Option 1: Cookie-based**
- Set cookie on first visit: `shortlink_uv={UUID}`
- Read cookie for subsequent visits
- **Pros**: Accurate, persistent
- **Cons**: Requires cookie support, privacy concerns

**Option 2: Device Fingerprint**
- Generate fingerprint from: IP + User-Agent + Screen resolution + Timezone
- Hash to create unique identifier
- **Pros**: No cookie required
- **Cons**: Less accurate (same user, different devices = different UV)

**Option 3: Hybrid**
- Prefer cookie if available
- Fallback to device fingerprint
- **Pros**: Best accuracy
- **Cons**: More complex

**Recommendation**: Start with Option 2 (device fingerprint), add Option 1 (cookie) in Phase 2.

---

## Appendix C: Additional Statistics Indicators

As a senior developer perspective, here are additional valuable statistics indicators that should be considered for a comprehensive short link analytics platform. These metrics provide deeper insights into user behavior, link performance, and system health.

### C.1 Traffic Source & Referrer Analysis

**Purpose**: Understand where traffic is coming from and which channels drive the most engagement.

**Metrics**:
- **Referrer Domain** - Top referring websites (e.g., google.com, facebook.com, direct)
- **Referrer Type** - Categorized as: Direct, Search Engine, Social Media, Email, Other
- **UTM Parameter Tracking** - Campaign, source, medium, term, content (if provided)
- **Referrer PV/UV** - Page views and unique visitors per referrer

**Implementation**:
```java
// In ShortLinkStatsRecordDTO, add:
private String referrer;           // Full referrer URL
private String referrerDomain;     // Extracted domain
private String referrerType;       // Categorized type
private String utmSource;          // UTM source parameter
private String utmMedium;          // UTM medium parameter
private String utmCampaign;        // UTM campaign parameter
```

**Database Schema Addition**:
```sql
CREATE TABLE t_link_stats_referrer (
    uuid VARCHAR(255) PRIMARY KEY,
    full_short_url VARCHAR(255) NOT NULL,
    stat_date DATE NOT NULL,
    referrer_domain VARCHAR(255),
    referrer_type VARCHAR(32), -- 'direct', 'search', 'social', 'email', 'other'
    pv INTEGER DEFAULT 0,
    uv INTEGER DEFAULT 0,
    CONSTRAINT uk_link_stats_referrer UNIQUE (full_short_url, stat_date, referrer_domain)
);
```

**Business Value**: 
- Identify most effective marketing channels
- Optimize marketing spend
- Understand user acquisition sources

---

### C.2 Geographic Distribution (Global)

**Purpose**: Expand beyond domestic (China) to global geographic analysis.

**Metrics**:
- **Country Distribution** - PV/UV breakdown by country
- **Region Distribution** - Continent/region level aggregation
- **City Distribution** - Top cities (for major countries)
- **Timezone Analysis** - Peak access times by timezone

**Implementation**:
```java
// In ShortLinkStatsRecordDTO, add:
private String country;            // ISO country code (e.g., "US", "CN", "GB")
private String region;             // Region/state (e.g., "California", "Beijing")
private String city;               // City name
private String timezone;           // Timezone (e.g., "America/Los_Angeles")
```

**Database Schema Addition**:
```sql
CREATE TABLE t_link_stats_geography (
    uuid VARCHAR(255) PRIMARY KEY,
    full_short_url VARCHAR(255) NOT NULL,
    stat_date DATE NOT NULL,
    country_code VARCHAR(8),       -- ISO 3166-1 alpha-2
    country_name VARCHAR(128),
    region VARCHAR(128),
    city VARCHAR(128),
    pv INTEGER DEFAULT 0,
    uv INTEGER DEFAULT 0,
    CONSTRAINT uk_link_stats_geography UNIQUE (full_short_url, stat_date, country_code, city)
);
```

**IP Geolocation Library**: Use MaxMind GeoIP2 or IP2Location

**Business Value**:
- Global market insights
- Regional content optimization
- Compliance with regional regulations (GDPR, etc.)

---

### C.3 Return Visitor Rate & Engagement Metrics

**Purpose**: Measure user loyalty and engagement quality.

**Metrics**:
- **Return Visitor Count** - Users who visited the link multiple times
- **Return Visitor Rate** - Percentage of UV that are return visitors
- **Average Visits Per User** - Total PV / Total UV
- **New vs Returning Visitor Breakdown** - Daily/weekly comparison

**Implementation**:
```java
// Track in Redis Sets with longer TTL (30 days)
// Compare current UV against historical UV sets
// Calculate: returnVisitorRate = (totalUV - newUV) / totalUV * 100
```

**Database Schema Addition**:
```sql
-- Add columns to t_link_stats_daily:
ALTER TABLE t_link_stats_daily ADD COLUMN return_visitor_count INTEGER DEFAULT 0;
ALTER TABLE t_link_stats_daily ADD COLUMN new_visitor_count INTEGER DEFAULT 0;
ALTER TABLE t_link_stats_daily ADD COLUMN avg_visits_per_user DECIMAL(10,2) DEFAULT 0;
```

**Business Value**:
- Measure link effectiveness and user engagement
- Identify viral or high-retention links
- Understand user behavior patterns

---

### C.4 Link Performance & Health Metrics

**Purpose**: Monitor link health and identify potential issues.

**Metrics**:
- **Redirect Success Rate** - Percentage of successful redirects (200/302)
- **Error Rate** - Failed redirects (404, 500, timeout)
- **Average Redirect Latency** - Time taken to process redirect
- **Link Age** - Days since link creation
- **Link Performance Score** - Composite health metric (0-100)

**Implementation**:
```java
// In ShortLinkStatsRecordDTO, add:
private Integer httpStatus;        // HTTP status code (200, 302, 404, 500)
private Long redirectLatencyMs;    // Redirect processing time in milliseconds
private Boolean isError;           // True if status >= 400
```

**Database Schema Addition**:
```sql
CREATE TABLE t_link_stats_performance (
    uuid VARCHAR(255) PRIMARY KEY,
    full_short_url VARCHAR(255) NOT NULL,
    stat_date DATE NOT NULL,
    total_requests INTEGER DEFAULT 0,
    successful_redirects INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    avg_latency_ms DECIMAL(10,2) DEFAULT 0,
    p95_latency_ms DECIMAL(10,2) DEFAULT 0,
    p99_latency_ms DECIMAL(10,2) DEFAULT 0,
    CONSTRAINT uk_link_stats_performance UNIQUE (full_short_url, stat_date)
);
```

**Business Value**:
- Proactive issue detection
- SLA monitoring
- Performance optimization insights

---

### C.5 Peak Traffic & Time Analysis

**Purpose**: Understand traffic patterns and optimize resource allocation.

**Metrics**:
- **Peak Hour** - Hour with highest traffic
- **Peak Day of Week** - Day with highest traffic
- **Traffic Distribution Curve** - Visual representation of traffic over time
- **Traffic Growth Rate** - Week-over-week or month-over-month growth
- **Traffic Predictability Score** - How consistent traffic patterns are

**Implementation**:
```java
// Already partially covered by hourStats and weekdayStats
// Add calculation for:
// - Peak hour identification
// - Traffic variance/standard deviation
// - Growth rate calculation
```

**Response DTO Addition**:
```java
// In ShortLinkStatsRespDTO, add:
private Integer peakHour;                    // 0-23
private Integer peakDayOfWeek;               // 0-6 (Sunday-Saturday)
private Double trafficGrowthRate;            // Percentage growth
private Double trafficVariance;              // Variance in traffic patterns
```

**Business Value**:
- Capacity planning
- Cost optimization (scale down during low-traffic periods)
- Marketing timing optimization

---

### C.6 Device Category & Mobile Analysis

**Purpose**: Understand device preferences and mobile vs desktop behavior.

**Metrics**:
- **Mobile vs Desktop Ratio** - Percentage breakdown
- **Tablet Usage** - Tablet-specific metrics
- **Mobile OS Breakdown** - iOS vs Android distribution
- **Screen Resolution Distribution** - Common screen sizes
- **Mobile Network Types** - 4G, 5G, WiFi distribution

**Implementation**:
```java
// Enhance existing deviceStats with:
// - Device category aggregation (Mobile/Desktop/Tablet)
// - Mobile-specific OS breakdown
// - Screen resolution parsing from User-Agent
```

**Response DTO Addition**:
```java
// In ShortLinkStatsRespDTO, add:
private List<ShortLinkStatsDeviceCategoryRespDTO> deviceCategoryStats;
// Fields: category (Mobile/Desktop/Tablet), pv, uv, percentage

private List<ShortLinkStatsMobileOsRespDTO> mobileOsStats;
// Fields: os (iOS/Android), pv, uv, percentage
```

**Business Value**:
- Mobile-first optimization priorities
- App vs web strategy decisions
- Responsive design insights

---

### C.7 Language & Locale Analysis

**Purpose**: Understand user language preferences for content localization.

**Metrics**:
- **Language Distribution** - Top languages from Accept-Language header
- **Locale Distribution** - Country-language combinations (e.g., en-US, zh-CN)
- **Language Preference Trends** - Changes over time

**Implementation**:
```java
// In ShortLinkStatsRecordDTO, add:
private String acceptLanguage;     // Full Accept-Language header
private String primaryLanguage;   // Primary language (e.g., "en", "zh")
private String locale;             // Locale code (e.g., "en-US", "zh-CN")
```

**Database Schema Addition**:
```sql
CREATE TABLE t_link_stats_language (
    uuid VARCHAR(255) PRIMARY KEY,
    full_short_url VARCHAR(255) NOT NULL,
    stat_date DATE NOT NULL,
    language_code VARCHAR(8),      -- ISO 639-1 (e.g., "en", "zh")
    locale_code VARCHAR(16),       -- Full locale (e.g., "en-US")
    pv INTEGER DEFAULT 0,
    uv INTEGER DEFAULT 0,
    CONSTRAINT uk_link_stats_language UNIQUE (full_short_url, stat_date, locale_code)
);
```

**Business Value**:
- Content localization strategy
- Multi-language support prioritization
- Regional content optimization

---

### C.8 Link Lifecycle & Trend Analysis

**Purpose**: Understand how link performance changes over time.

**Metrics**:
- **Link Age** - Days since creation
- **Performance Trend** - Improving, declining, or stable
- **Traffic Decay Rate** - How quickly traffic decreases over time
- **Viral Coefficient** - Rate of link sharing/growth
- **Link Longevity Score** - Expected remaining active period

**Implementation**:
```java
// Calculate by comparing current period vs previous periods
// Trend analysis: compare last 7 days vs previous 7 days
// Decay rate: exponential decay model fitting
```

**Response DTO Addition**:
```java
// In ShortLinkStatsRespDTO, add:
private Integer linkAgeDays;               // Days since creation
private String performanceTrend;           // "improving", "declining", "stable"
private Double trafficDecayRate;            // Percentage decay per day
private Double viralCoefficient;           // Growth multiplier
```

**Business Value**:
- Link expiration strategy
- Content refresh recommendations
- Long-term link value assessment

---

### C.9 Security & Anomaly Detection Metrics

**Purpose**: Identify suspicious activity and potential security threats.

**Metrics**:
- **Suspicious IP Count** - IPs with unusual access patterns
- **Bot Traffic Percentage** - Automated vs human traffic
- **Rate Limit Violations** - Requests exceeding rate limits
- **Geographic Anomalies** - Unusual geographic access patterns
- **Access Pattern Anomalies** - Unusual time/pattern deviations

**Implementation**:
```java
// Bot detection: User-Agent analysis, behavior patterns
// Anomaly detection: Statistical analysis (z-score, IQR)
// Rate limiting: Track requests per IP per time window
```

**Database Schema Addition**:
```sql
CREATE TABLE t_link_stats_security (
    uuid VARCHAR(255) PRIMARY KEY,
    full_short_url VARCHAR(255) NOT NULL,
    stat_date DATE NOT NULL,
    suspicious_ip_count INTEGER DEFAULT 0,
    bot_traffic_count INTEGER DEFAULT 0,
    rate_limit_violations INTEGER DEFAULT 0,
    anomaly_score DECIMAL(5,2) DEFAULT 0,  -- 0-100 anomaly score
    CONSTRAINT uk_link_stats_security UNIQUE (full_short_url, stat_date)
);
```

**Business Value**:
- Security threat detection
- DDoS protection insights
- Data quality assurance

---

### C.10 Conversion & Goal Tracking (Optional)

**Purpose**: Track business outcomes beyond clicks (if applicable).

**Metrics**:
- **Conversion Events** - Custom events (signup, purchase, download)
- **Conversion Rate** - Conversions / Total Clicks
- **Revenue Attribution** - Revenue per link (if e-commerce)
- **Goal Completion Rate** - Percentage of users completing goals

**Implementation**:
```java
// Requires integration with destination site analytics
// Or custom tracking pixels/API calls
// Optional feature - only if business needs conversion tracking
```

**Note**: This requires additional integration and may not be applicable to all use cases. Include only if business requirements demand conversion tracking.

---

### C.11 Summary: Priority Implementation Order

**Phase 1 (High Priority - Core Metrics)**:
1. ✅ PV, UV, UIP (already in design)
2. ✅ Browser, OS, Device, Network (already in design)
3. **Referrer Analysis** (C.1) - Critical for marketing insights
4. **Return Visitor Rate** (C.3) - Important engagement metric

**Phase 2 (Medium Priority - Enhanced Analytics)**:
5. **Geographic Distribution** (C.2) - Global expansion support
6. **Link Performance Metrics** (C.4) - System health monitoring
7. **Peak Traffic Analysis** (C.5) - Capacity planning
8. **Device Category Analysis** (C.6) - Mobile optimization

**Phase 3 (Lower Priority - Advanced Features)**:
9. **Language & Locale** (C.7) - If multi-language support needed
10. **Link Lifecycle Analysis** (C.8) - Long-term insights
11. **Security & Anomaly Detection** (C.9) - If security is concern
12. **Conversion Tracking** (C.10) - If business goals require it

---

### C.12 Updated DTO Structure Recommendation

To support these additional indicators, consider extending `ShortLinkStatsRespDTO`:

```java
@Data
@Builder
public class ShortLinkStatsRespDTO {
    // Existing fields...
    private Integer pv;
    private Integer uv;
    private Integer uip;
    
    // ... existing dimension stats ...
    
    // NEW: Additional indicators
    private List<ShortLinkStatsReferrerRespDTO> referrerStats;
    private List<ShortLinkStatsGeographyRespDTO> geographyStats;
    private Double returnVisitorRate;
    private Integer returnVisitorCount;
    private Integer newVisitorCount;
    private Double avgVisitsPerUser;
    private Double redirectSuccessRate;
    private Double errorRate;
    private Double avgRedirectLatencyMs;
    private Integer peakHour;
    private Integer peakDayOfWeek;
    private Double trafficGrowthRate;
    private List<ShortLinkStatsDeviceCategoryRespDTO> deviceCategoryStats;
    private List<ShortLinkStatsLanguageRespDTO> languageStats;
    private Integer linkAgeDays;
    private String performanceTrend;
    private Double anomalyScore;
}
```

---

### C.13 Implementation Considerations

**Storage Impact**:
- Additional tables will increase database size
- Consider partitioning strategies for high-volume metrics
- Use materialized views for complex aggregations

**Performance Impact**:
- More dimensions = more aggregation work
- Consider async processing for complex metrics
- Cache frequently accessed statistics

**Cost Impact**:
- IP geolocation services may have costs (MaxMind, IP2Location)
- Additional Redis memory for tracking sets
- Database storage growth

**Privacy & Compliance**:
- Ensure GDPR/privacy compliance for IP tracking
- Consider data retention policies
- Anonymize IP addresses if required by regulations

---

**Note**: These additional indicators should be implemented incrementally based on business priorities and user feedback. Start with Phase 1 indicators, then expand based on actual usage patterns and requirements.

---

**Document Version**: 1.1  
**Last Updated**: 2026-01-27  
**Author**: Design Team  
**Status**: Draft for Review (Updated with Additional Indicators)
