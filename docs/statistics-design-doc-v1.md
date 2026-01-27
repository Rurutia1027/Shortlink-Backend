# Event Driven Statistics (PV/UV) Design Document

## Executive Summary

This document outlines the design for implementing event-driven statistics collection and aggregation for PV (Page
Views) and UV(Unique Visitors) metrics in the Shortlink Platform. The design focuses on decoupling statistics collection
from the redirect path, enabling scalable real-time analytics while maintaining low latency for core redirect
operations.

---

## 1. Current State Analysis

### 1.1 Existing Components

#### Controllers (API Layer)

**`ShortLinkStatsController`** (`/api/shortlink/v1/stats`)

- `GET /stats` - Single short link statistics
- `GET /stats/group` - Group statistics
- `GET /stats/access` - Access records for single link
- `GET /stats/group/access` - Access records for group

**`ShortLinkController`** (`/api/shortlink/v1`)

- `GET /{shortUri}` - Redirect endpoint (currently TODO, no statistics collection)

#### Service Layer

- *`ShortLinkStatsService`* - Interface defined, implementation returns `null` (mocked)
- *`ShortLinkStatsServiceImpl`* - All methods return `null` with TODO comments

#### Data Transfer Objects

*`ShortLinkStatsRecordDTO`* - Event payload structure

- `fullShortUrl`, `remoteAddr`, `os`, `browser`, `device`, `network`
- `uv`, `uvFirstFlag`, `uipFirstFlag`
- `keys`, `currentDate`

*`ShortLinkStatsRespDTO`* - Response structure

- Aggregated metrics: `pv`, `uv`, `uip`
- Time-series: `daily`, `hourStats`, `weekdayStats`
- Dimension breakdowns: `browserStats`, `osStats`, `deviceStats`, `networkStats`, `localeCnStats`, `topIpStats`

#### Infrastructure

**Redis Streams** - Constants defined:

- `SHORT_LINK_STATS_STREAM_TOPIC_KEY = "short-link:stats-stream"`
- `SHORT_LINK_STATS_STREAMD_GROUP_KEY = "short-link:stats-stream:only-group"`
- `SHORT_LINK_STATS_UV_KEY = "short-link:stats:uv:"`
- `SHORT_LINK_STATS_UIP_KEY= "short-link:stats:uip:"`

**Database Schema**

- `t_link` table exists (no statistics columns)
- No dedicated statistics tables yet - i prefer using join or create FK rather than directly add new columns to
  t_link -> to complicated to the table syntas

### 1.2 Gaps Identified

- **No Event Publishing**: Redirect endpoint (`restoreUrl`) doesn't publish statistics events
- **No Event Consumption**: No consumer to process statistics events
- **No Aggregation Logic**: No service to calculate PV/UV from events
- **No Storage Strategy**: No decision on where/how to store aggregated statistics
- **No UV/UIP Detection**: No logic to determine first-time visitors

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
- Support 10K+ redirects/seconds

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
- Observable (metircs, logs, traces)

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

### 3.2 Component Responsibility

- **Event Publisher** - Publishes access events asynchronously
- **Event Queue/Stream** - Buffers events for processing
- **Event Consumer** - Processes events in batches
- **Aggregation Service** - Calculates PV/UV metrics
- **Storage Layer** - Persists aggregated statistics
- **Query Service** - Retrieves statistics for API responses

--- 

### TODO:

- diff message queues trades-off
- retry, intermediate storage of the event status, event losing solutions
- table design for event on db side, how sync aggregated data to db join with the biz layer table ? `t_link`

---

## 4. Solution Options

### Solution 1: Redis Streams + PostgresSQL Aggregation

#### Architecture

- Event Publisher: Redis Streams (using Redisson)
- Event Consumption: Spring scheduled tasks consuming from Redis Streams
- UV/UIP Detection: Redis Sets/Bloom Filters for deduplication
- Aggregation: In-memory aggregation, periodic flush to PostgresSQL
- Storage: PostgresSQL tables for aggregated statistics

#### Implementation Details

**Event Publishing**

```java
// In ShortlinkServiceImpl#restoreUrl()
ShortLinkStatsRecordDTO event = buildStatsRecord(fullShortUrl, request); 
redissionClient.

getStream(STREAM_KEY).

add(StreamMessageId.autoGenerate(),event); 
```

**Event Consumption**

- Consumer group reads from Redis Streams
- Batch processing (100-1000 events per batch)
- UV detection using Redis Sets: `$ADD short-link:stats:uv:{fullShortUrl}:{date} {uv}`
- UIP detection using Redis Sets: `$ADD short-link:stats:uip:{fullShortUrl}:{date} {ip}`

**Aggregation Storage**

```sql
CREATE TABLE t_link_stats_daily
(
    uuid           VARCHAR(255) PRIMARY KEY,
    full_short_url VARCHAR(255) NOT NULL,
    gid            VARCHAR(64)  NOT NULL,
    stat_date      DATE         NOT NULL,
    pv             INTEGER DEFAULT 0,
    uv             INTEGER DEFAULT 0,
    uip            INTEGER DEFAULT 0,
    created_date   TIMESTAMP,
    modified_date  TIMESTAMP,
    UNIQUE (full_short_url, stat_date)
);

CREATE TABLE t_link_stats_hourly
(
    uuid           VARCHAR(255) PRIMARY KEY,
    full_short_url VARCHAR(255) NOT NULL,
    stat_date      DATE         NOT NULL,
    stat_hour      INTEGER      NOT NULL,
    pv             INTEGER DEFAULT 0,
    UNIQUE (full_short_url, stat_date, stat_hour)
);

CREATE TABLE t_link_stats_dimension
(
    uuid            VARCHAR(255) PRIMARY KEY,
    full_short_url  VARCHAR(255) NOT NULL,
    stat_date       DATE         NOT NULL,
    dimension_type  VARCHAR(32)  NOT NULL, -- 'browser', 'os', 'device', 'network'
    dimension_value VARCHAR(128) NOT NULL,
    pv              INTEGER DEFAULT 0,
    UNIQUE (full_short_url, stat_date, dimension_type, dimension_value)
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

- Event Publisher: Redis Streams (same as Solution 1)
- Event Consumption: Batch consumer writing directly to ClickHouse
- UV/UIP Detection: ClickHouse `uniqExact()` functions
- Aggregation: ClickHouse Materialize Views
- Storage: ClickHouse tables

#### Implementation Details

```sql 
CREATE TABLE link_stats_events
(
    event_time     DateTime,
    full_short_url String,
    gid            String,
    remote_addr    String,
    uv             String,
    os             String,
    browser        String,
    device         String,
    network        String
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_time)
ORDER BY (full_short_url, event_time);

CREATE MATERIALIZED VIEW link_stats_daily_mv
            ENGINE = SummingMergeTree()
            PARTITION BY toYYYYMM(stat_date)
            ORDER BY (full_short_url, stat_date)
AS
SELECT toDate(event_time)     as stat_date,
       full_short_url,
       gid,
       count()                as pv,
       uniqExact(uv)          as uv,
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

### Solution 3: Message Queue (RabbitMQ / Kafka) + PostgresSQL

#### Architecture

- Event Publishing: RabbitMQ/Kafka topic
- Event Consumption: Multiple consumers for parallel processing
- UV/UIP Detection: Redis Sets (same as Solution 1)
- Aggregation: In-memory, flush to PostgresSQL
- Storage: PostgresSQL (same schema as Solution1)

#### Implementation Details

**RabbitMQ Setup**:

- Exchange: `shortlink.stats.exchange` (topic)
- Queue: `shortlink.stats.queue` (durable, multiple consumers)
- Routing: `stats.access.{gid}`

**Kafka Setup**:

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

### Solution 4: Hybrid Approach (Redis Stream + Redis TimeSeries + PostgresSQL)

#### Architecture

- Event Publishing: Redis Streams
- Event Consumption: Batch consumer
- UV/UIP Detection: Redis Sets
- Real-Time Aggregation: Redis TimeSeries module (for recent data)
- Histogram Aggregation: PostgresSQL (for older data)
- Storage: Redis TimeSeries (last 30 days) + PostgresSQL (older data)

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

| Criteria                      | Solution 1<br/>(Redis Streams + PG) | Solution 2<br/>(Redis Streams + ClickHouse) | Solution 3<br/>(MQ + PG) | Solution 4<br/>(Hybrid) |
|-------------------------------|-------------------------------------|---------------------------------------------|--------------------------|-------------------------|
| **Implementation Complexity** | Medium                              | High                                        | Medium-High              | High                    |
| **Infrastructure Cost**       | Low                                 | Medium-High                                 | Medium                   | Medium                  |
| **Write Performance**         | Good (5K/sec)                       | Excellent (100K+/sec)                       | Good (20K/sec)           | Good (5K/sec)           |
| **Query Performance**         | Good                                | Excellent                                   | Good                     | Excellent (recent)      |
| **Operational Overhead**      | Low                                 | High                                        | Medium                   | Medium-High             |
| **Data Durability**           | Good                                | Excellent                                   | Excellent                | Good                    |
| **Scalability**               | Medium                              | High                                        | Medium-High              | High                    |
| **Learning Curve**            | Low                                 | Medium                                      | Medium                   | Medium                  |
| **Existing Infrastructure**   | ✅ Redis + PG                        | ❌ Need ClickHouse                           | ❌ Need MQ                | ✅ Redis + PG            |

---

## 6. Recommended Solutions

### Solution 1: Redis Streams + PostgreSQL Aggregation

**Rationale**:

- **Leverages Existing Infrastructure**: Use Redis (already in place) and PostgreSQL (already configured)
- **Balanced Complexity**: Moderate implementation effort, well-understood technologies
- **Good Performance**: Sufficient for expected load (10K+ redirects/sec)
- **Cost-Effect**: No additional infrastructure required
- **Maintainable**: Team familiarity with Redis and PostgresSQL

### Implementation Phases

#### Phase 1: Event Publishing (Week1-2)

- Implement `ShortLinkStatsRecordDTO` builder
- Add User-Agent parsing (browser, OS, device detection)
- Implement async event publisher using Redis Streams
- Integrate into `restoreUrl()` method
- Add error handling and fallback (graceful degradation)

#### Phase 2: Event consumption & UV Detection (Week2-3)

- Implement Redis Streams consumer group
- Add UV detection using Redis Sets with TTL
- Add UIP detection using Redis Sets with TTL
- Implement batch processing (100-1000 events)
- Add monitoring and alerting

#### Phase 3: Aggregation & Storage (Week 3-4)

- Create PostgresSQL statistics tables (daily, hourly, dimension)
- Implement aggregation logic
- Implement periodic flush (every 5-10 minutes)
- Add database indexes for query performance

#### Phase 4: Query Service (Week 4-5)

- Implement `ShortLinkStatsServiceImpl` methods
- Add query logic for single link statistics
- Add query logic for group statistics
- Add access record pagination
- Add caching layer for frequently accessed statistics

#### Phase 5: Testing & Optimization (Week 5-6)

- Load testing (10K+ redirects/sec)
- Query performance optimization
- Redis memory optimization (Set cleanup strategy )
- Database query optimization (indexes, partitioning)

---

## 7. Detailed Design: Solution 1

### Solution 2: 