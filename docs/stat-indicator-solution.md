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

## 6. Recommended Solution (UPDATED)

### Solution: Kafka + ClickHouse Enterprise Architecture

**Rationale:**
1. **Enterprise-Grade Scalability**: Kafka supports 100K+ events/sec with horizontal scaling, perfect for large-scale enterprise deployments
2. **Decoupled Architecture**: Kafka as message queue decouples event publishing from consumption, avoiding tight coupling with Spring framework
3. **High-Performance Analytics**: ClickHouse provides columnar storage and built-in aggregation functions, eliminating need for RDBMS-based aggregation
4. **No Framework Coupling**: Independent Kafka consumers (not Spring Scheduler) allow for better scalability and operational flexibility
5. **Separation of Concerns**: 
   - PostgreSQL (`t_link`) stores business data (link metadata)
   - ClickHouse stores and aggregates statistics data
   - Clear separation enables independent scaling and optimization
6. **Industry Standard**: Kafka + ClickHouse is a proven pattern for large-scale analytics systems (used by companies like Uber, Yandex, etc.)

**Architecture Flow:**
```
User Request → ShortLinkController.restoreUrl() 
  → Publish Event to Kafka (async, non-blocking)
  → Return 302 Redirect (immediate)

Kafka Topic: shortlink-stats-events
  → Kafka Consumer (independent service/worker)
  → Sync Events to ClickHouse (batch insert)
  
ClickHouse:
  → Raw Events Table (link_stats_events)
  → Materialized Views (auto-aggregate to daily/hourly/dimension tables)
  
Query Flow:
  Controller → Service → Query PostgreSQL (t_link) + ClickHouse (stats tables) → Merge Results → Return
```

### Implementation Phases

#### Phase 1: Kafka Infrastructure Setup (Week 1)
1. Set up Kafka cluster (or use managed Kafka service)
2. Create Kafka topic: `shortlink-stats-events` (partitioned by gid, replication factor 3)
3. Configure Kafka producer in Spring Boot application
4. Add Kafka dependencies (`spring-kafka` or `kafka-clients`)
5. Configure producer settings (acks, retries, batch size)

#### Phase 2: Event Publishing (Week 1-2)
1. Implement `ShortLinkStatsRecordDTO` builder
2. Add User-Agent parsing (browser, OS, device detection)
3. Implement async Kafka producer service
4. Integrate into `restoreUrl()` method (non-blocking)
5. Add error handling and fallback (graceful degradation)
6. Add metrics for event publishing (success/failure rates, latency)

#### Phase 3: ClickHouse Infrastructure Setup (Week 2)
1. Set up ClickHouse cluster (or use managed service)
2. Create ClickHouse database and tables
3. Design table schema (events table + materialized views)
4. Configure ClickHouse JDBC driver
5. Set up connection pooling

#### Phase 4: Kafka Consumer & ClickHouse Sync (Week 2-3)
1. Implement independent Kafka consumer (separate service/worker, NOT Spring Scheduler)
2. Consumer options:
   - Option A: Standalone Java application using Kafka Consumer API
   - Option B: Spring Boot application with `@KafkaListener` (but independent deployment)
   - Option C: Kubernetes Job/CronJob for batch processing
3. Implement batch processing (1000-10000 events per batch)
4. Implement ClickHouse batch insert (using `INSERT INTO ... VALUES` or `INSERT INTO ... SELECT`)
5. Add error handling and retry logic
6. Add monitoring (consumer lag, processing rate, error rate)

#### Phase 5: ClickHouse Aggregation (Week 3-4)
1. Create Materialized Views for automatic aggregation:
   - Daily statistics (PV, UV, UIP)
   - Hourly statistics
   - Dimension statistics (browser, OS, device, network)
2. Configure aggregation refresh intervals
3. Optimize ClickHouse table settings (partitioning, ordering keys)
4. Add indexes for query performance

#### Phase 6: Query Service Implementation (Week 4-5)
1. Implement `ShortLinkStatsServiceImpl` methods
2. Query flow:
   - Query PostgreSQL for link metadata (`t_link` table)
   - Query ClickHouse for statistics (aggregated tables)
   - Merge results in service layer
3. Implement query logic for:
   - Single link statistics
   - Group statistics
   - Access record pagination
4. Add caching layer (Redis) for frequently accessed statistics
5. Add query performance monitoring

#### Phase 7: Testing & Optimization (Week 5-6)
1. Load testing:
   - Event publishing: 100K+ events/sec
   - Consumer processing: Verify no lag
   - Query performance: < 100ms for aggregated stats
2. ClickHouse optimization:
   - Partitioning strategy
   - Materialized view refresh optimization
   - Query performance tuning
3. Kafka optimization:
   - Partition count tuning
   - Consumer parallelism
   - Batch size optimization
4. End-to-end testing and monitoring setup

---

## 7. Detailed Design: Kafka + ClickHouse Architecture

### 7.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Event Publishing Layer                       │
│  ShortLinkController.restoreUrl()                              │
│    ↓ (async, non-blocking)                                     │
│  KafkaProducer.send(event)                                     │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Kafka Message Queue                          │
│  Topic: shortlink-stats-events                                  │
│  Partitions: 20 (partitioned by gid hash)                       │
│  Replication: 3                                                 │
│  Retention: 7 days                                               │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│              Independent Kafka Consumer Service                 │
│  (Standalone service, NOT Spring Scheduler)                    │
│    - Consumer Group: shortlink-stats-aggregator                 │
│    - Batch processing: 1000-10000 events/batch                   │
│    - Parallel consumers: Multiple instances                      │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ClickHouse Storage                          │
│  1. Raw Events Table (link_stats_events)                       │
│  2. Materialized Views (auto-aggregate)                        │
│     - link_stats_daily_mv                                       │
│     - link_stats_hourly_mv                                     │
│     - link_stats_dimension_mv                                  │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Query Service Layer                          │
│  Controller → Service → PostgreSQL (t_link) +                  │
│                          ClickHouse (stats) → Merge → Return    │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 Event Publishing Service (Kafka Producer)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ShortLinkStatsEventPublisher {
    private final KafkaTemplate<String, ShortLinkStatsRecordDTO> kafkaTemplate;
    private final UserAgentParser userAgentParser;
    
    @Value("${kafka.topics.stats-events:shortlink-stats-events}")
    private String statsEventsTopic;
    
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
                .referrer(request.getHeader("Referer"))
                .userAgent(request.getHeader("User-Agent"))
                .currentDate(new Date())
                .keys(UUID.randomUUID().toString())
                .build();
            
            // Publish to Kafka (partition by gid for better distribution)
            ListenableFuture<SendResult<String, ShortLinkStatsRecordDTO>> future = 
                kafkaTemplate.send(statsEventsTopic, gid, event);
            
            // Optional: Add callback for monitoring
            future.addCallback(
                result -> log.debug("Stats event published: {}", event.getKeys()),
                failure -> log.error("Failed to publish stats event", failure)
            );
        } catch (Exception e) {
            log.error("Failed to publish stats event", e);
            // Graceful degradation - don't fail redirect
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        // Extract real IP from headers (X-Forwarded-For, etc.)
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }
}
```

**Kafka Producer Configuration:**
```java
@Configuration
@EnableKafka
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Bean
    public ProducerFactory<String, ShortLinkStatsRecordDTO> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Performance tuning for high throughput
        configProps.put(ProducerConfig.ACKS_CONFIG, "1"); // Leader acknowledgment
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10); // Wait 10ms for batching
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    public KafkaTemplate<String, ShortLinkStatsRecordDTO> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

### 7.3 Kafka Consumer Service (Independent Service)

**Option A: Standalone Kafka Consumer Application**

```java
// Separate Spring Boot application: shortlink-stats-consumer
@SpringBootApplication
public class StatsConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(StatsConsumerApplication.class, args);
    }
}

@Component
@RequiredArgsConstructor
@Slf4j
public class ShortLinkStatsKafkaConsumer {
    private final ClickHouseService clickHouseService;
    
    @KafkaListener(
        topics = "${kafka.topics.stats-events:shortlink-stats-events}",
        groupId = "${kafka.consumer.group-id:shortlink-stats-aggregator}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeBatch(List<ConsumerRecord<String, ShortLinkStatsRecordDTO>> records) {
        log.info("Received {} events from Kafka", records.size());
        
        try {
            // Convert to DTOs
            List<ShortLinkStatsRecordDTO> events = records.stream()
                .map(ConsumerRecord::value)
                .collect(Collectors.toList());
            
            // Batch insert to ClickHouse
            clickHouseService.batchInsertEvents(events);
            
            log.info("Successfully processed {} events", events.size());
        } catch (Exception e) {
            log.error("Failed to process events", e);
            // In production, implement dead-letter queue or retry mechanism
            throw e; // Will trigger Kafka retry
        }
    }
}

@Configuration
@EnableKafka
public class KafkaConsumerConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Bean
    public ConsumerFactory<String, ShortLinkStatsRecordDTO> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "shortlink-stats-aggregator");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // Performance tuning
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10000); // Max records per poll
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1048576); // 1MB
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500); // 500ms
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ShortLinkStatsRecordDTO> 
            kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ShortLinkStatsRecordDTO> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setBatchListener(true); // Enable batch processing
        factory.setConcurrency(10); // 10 concurrent consumers per instance
        return factory;
    }
}
```

**Option B: Native Kafka Consumer (No Spring)**

```java
// Pure Kafka Consumer API - completely decoupled from Spring
public class ShortLinkStatsConsumerWorker implements Runnable {
    private final KafkaConsumer<String, ShortLinkStatsRecordDTO> consumer;
    private final ClickHouseService clickHouseService;
    private volatile boolean running = true;
    
    public ShortLinkStatsConsumerWorker(String bootstrapServers, 
                                       ClickHouseService clickHouseService) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", "shortlink-stats-aggregator");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", JsonDeserializer.class.getName());
        props.put("max.poll.records", "10000");
        props.put("enable.auto.commit", "false");
        
        this.consumer = new KafkaConsumer<>(props);
        this.clickHouseService = clickHouseService;
    }
    
    @Override
    public void run() {
        consumer.subscribe(Collections.singletonList("shortlink-stats-events"));
        
        while (running) {
            ConsumerRecords<String, ShortLinkStatsRecordDTO> records = 
                consumer.poll(Duration.ofMillis(1000));
            
            if (!records.isEmpty()) {
                List<ShortLinkStatsRecordDTO> events = new ArrayList<>();
                for (ConsumerRecord<String, ShortLinkStatsRecordDTO> record : records) {
                    events.add(record.value());
                }
                
                try {
                    clickHouseService.batchInsertEvents(events);
                    consumer.commitSync(); // Manual commit after successful processing
                } catch (Exception e) {
                    log.error("Failed to process events", e);
                    // Implement retry or dead-letter queue logic
                }
            }
        }
        
        consumer.close();
    }
    
    public void shutdown() {
        running = false;
    }
}
```

### 7.4 ClickHouse Service Implementation

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ClickHouseService {
    private final ClickHouseDataSource dataSource;
    
    /**
     * Batch insert events to ClickHouse
     */
    public void batchInsertEvents(List<ShortLinkStatsRecordDTO> events) {
        if (events.isEmpty()) {
            return;
        }
        
        String sql = "INSERT INTO link_stats_events " +
                    "(event_time, full_short_url, gid, remote_addr, uv, os, browser, " +
                    "device, network, referrer, user_agent, keys) VALUES";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            
            // ClickHouse supports batch insert
            for (ShortLinkStatsRecordDTO event : events) {
                stmt.setTimestamp(1, new Timestamp(event.getCurrentDate().getTime()));
                stmt.setString(2, event.getFullShortUrl());
                stmt.setString(3, event.getGid());
                stmt.setString(4, event.getRemoteAddr());
                stmt.setString(5, event.getUv());
                stmt.setString(6, event.getOs());
                stmt.setString(7, event.getBrowser());
                stmt.setString(8, event.getDevice());
                stmt.setString(9, event.getNetwork());
                stmt.setString(10, event.getReferrer());
                stmt.setString(11, event.getUserAgent());
                stmt.setString(12, event.getKeys());
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            log.debug("Inserted {} events to ClickHouse", events.size());
        } catch (SQLException e) {
            log.error("Failed to insert events to ClickHouse", e);
            throw new RuntimeException("ClickHouse insert failed", e);
        }
    }
    
    /**
     * Query daily statistics from ClickHouse
     */
    public List<ShortLinkStatsAccessDailyRespDTO> queryDailyStats(
            String fullShortUrl, String gid, String startDate, String endDate) {
        
        String sql = "SELECT " +
                    "  stat_date, " +
                    "  sum(pv) as pv, " +
                    "  uniqExact(uv) as uv, " +
                    "  uniqExact(remote_addr) as uip " +
                    "FROM link_stats_daily_mv " +
                    "WHERE full_short_url = ? " +
                    "  AND gid = ? " +
                    "  AND stat_date >= ? " +
                    "  AND stat_date <= ? " +
                    "GROUP BY stat_date " +
                    "ORDER BY stat_date";
        
        // Execute query and map results
        // ... implementation details
    }
}
```

### 7.5 Query Service Implementation (Merge PostgreSQL + ClickHouse)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ShortLinkStatsServiceImpl implements ShortLinkStatsService {
    private final QueryService queryService; // For PostgreSQL (t_link)
    private final ClickHouseService clickHouseService; // For statistics
    private final CacheService cacheService;
    
    @Override
    public ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam) {
        // Check cache first
        String cacheKey = buildCacheKey(requestParam);
        ShortLinkStatsRespDTO cached = cacheService.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Step 1: Verify link exists in PostgreSQL
        ShortLink link = queryService.findByFullShortUrl(requestParam.getFullShortUrl());
        if (link == null) {
            throw new ServiceException("Short link not found");
        }
        
        // Step 2: Query statistics from ClickHouse
        ShortLinkStatsRespDTO stats = queryStatsFromClickHouse(requestParam);
        
        // Step 3: Merge with link metadata if needed
        // (stats already contains all needed info, but can enrich if required)
        
        // Cache for 5 minutes
        cacheService.set(cacheKey, stats, 5, TimeUnit.MINUTES);
        
        return stats;
    }
    
    private ShortLinkStatsRespDTO queryStatsFromClickHouse(ShortLinkStatsReqDTO req) {
        // Query daily stats from ClickHouse materialized view
        List<ShortLinkStatsAccessDailyRespDTO> daily = 
            clickHouseService.queryDailyStats(
                req.getFullShortUrl(), 
                req.getGid(), 
                req.getStartDate(), 
                req.getEndDate()
            );
        
        // Query hourly stats
        List<Integer> hourly = clickHouseService.queryHourlyStats(
            req.getFullShortUrl(), req.getStartDate(), req.getEndDate());
        
        // Query dimension stats
        List<ShortLinkStatsBrowserRespDTO> browserStats = 
            clickHouseService.queryBrowserStats(...);
        List<ShortLinkStatsOsRespDTO> osStats = 
            clickHouseService.queryOsStats(...);
        // ... other dimensions
        
        // Calculate totals from daily stats
        int totalPv = daily.stream()
            .mapToInt(ShortLinkStatsAccessDailyRespDTO::getPv)
            .sum();
        int totalUv = daily.stream()
            .mapToInt(ShortLinkStatsAccessDailyRespDTO::getUv)
            .sum();
        int totalUip = daily.stream()
            .mapToInt(ShortLinkStatsAccessDailyRespDTO::getUip)
            .sum();
        
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

## 8. ClickHouse Database Schema Design

### 8.1 Raw Events Table (Source Table)

This table stores all raw access events from Kafka. ClickHouse will automatically aggregate these events via Materialized Views.

```sql
CREATE DATABASE IF NOT EXISTS shortlink_stats;

USE shortlink_stats;

-- Raw events table (MergeTree engine for high-performance writes)
CREATE TABLE link_stats_events
(
    event_time DateTime DEFAULT now(),
    full_short_url String,
    gid String,
    remote_addr String,
    uv String,
    os String,
    browser String,
    device String,
    network String,
    referrer String,
    user_agent String,
    country_code String,
    region String,
    city String,
    language_code String,
    locale_code String,
    keys String,
    http_status UInt16,
    redirect_latency_ms UInt32
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_time)  -- Partition by month for efficient queries
ORDER BY (full_short_url, event_time, gid)  -- Primary key for fast lookups
TTL event_time + INTERVAL 90 DAY;  -- Auto-delete events older than 90 days

-- Index for faster queries by gid
ALTER TABLE link_stats_events ADD INDEX idx_gid gid TYPE minmax GRANULARITY 4;
```

**Key Design Decisions:**
- **MergeTree Engine**: Optimized for high-volume inserts (100K+ events/sec)
- **Partitioning by Month**: Efficient data management and query performance
- **Ordering Key**: `(full_short_url, event_time, gid)` for fast link-specific queries
- **TTL**: Automatic cleanup of old data (90 days retention)

### 8.2 Daily Statistics Materialized View

Automatically aggregates events to daily statistics using ClickHouse's Materialized Views.

```sql
-- Daily statistics materialized view
CREATE MATERIALIZED VIEW link_stats_daily_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (full_short_url, stat_date, gid)
AS SELECT
    toDate(event_time) as stat_date,
    full_short_url,
    gid,
    count() as pv,
    uniqExact(uv) as uv,
    uniqExact(remote_addr) as uip
FROM link_stats_events
GROUP BY stat_date, full_short_url, gid;

-- Query table (for direct queries)
CREATE TABLE link_stats_daily
(
    stat_date Date,
    full_short_url String,
    gid String,
    pv UInt64,
    uv UInt64,
    uip UInt64
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (full_short_url, stat_date, gid);
```

**How Materialized Views Work:**
- ClickHouse automatically aggregates new events as they're inserted
- Uses `SummingMergeTree` engine for efficient aggregation
- `uniqExact()` function calculates unique visitors/IPs accurately
- No manual aggregation needed - fully automatic!

### 8.3 Hourly Statistics Materialized View

```sql
-- Hourly statistics materialized view
CREATE MATERIALIZED VIEW link_stats_hourly_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (full_short_url, stat_date, stat_hour)
AS SELECT
    toDate(event_time) as stat_date,
    toHour(event_time) as stat_hour,
    full_short_url,
    count() as pv
FROM link_stats_events
GROUP BY stat_date, stat_hour, full_short_url;

-- Query table
CREATE TABLE link_stats_hourly
(
    stat_date Date,
    stat_hour UInt8,
    full_short_url String,
    pv UInt64
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (full_short_url, stat_date, stat_hour);
```

### 8.4 Dimension Statistics Materialized Views

Separate materialized views for each dimension (browser, OS, device, network, etc.)

```sql
-- Browser statistics
CREATE MATERIALIZED VIEW link_stats_browser_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (full_short_url, stat_date, browser)
AS SELECT
    toDate(event_time) as stat_date,
    full_short_url,
    gid,
    browser,
    count() as pv,
    uniqExact(uv) as uv
FROM link_stats_events
WHERE browser != ''
GROUP BY stat_date, full_short_url, gid, browser;

-- OS statistics
CREATE MATERIALIZED VIEW link_stats_os_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (full_short_url, stat_date, os)
AS SELECT
    toDate(event_time) as stat_date,
    full_short_url,
    gid,
    os,
    count() as pv,
    uniqExact(uv) as uv
FROM link_stats_events
WHERE os != ''
GROUP BY stat_date, full_short_url, gid, os;

-- Device statistics
CREATE MATERIALIZED VIEW link_stats_device_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (full_short_url, stat_date, device)
AS SELECT
    toDate(event_time) as stat_date,
    full_short_url,
    gid,
    device,
    count() as pv,
    uniqExact(uv) as uv
FROM link_stats_events
WHERE device != ''
GROUP BY stat_date, full_short_url, gid, device;

-- Network statistics
CREATE MATERIALIZED VIEW link_stats_network_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (full_short_url, stat_date, network)
AS SELECT
    toDate(event_time) as stat_date,
    full_short_url,
    gid,
    network,
    count() as pv,
    uniqExact(uv) as uv
FROM link_stats_events
WHERE network != ''
GROUP BY stat_date, full_short_url, gid, network;

-- Referrer statistics
CREATE MATERIALIZED VIEW link_stats_referrer_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (full_short_url, stat_date, referrer_domain)
AS SELECT
    toDate(event_time) as stat_date,
    full_short_url,
    gid,
    extractDomain(referrer) as referrer_domain,
    count() as pv,
    uniqExact(uv) as uv
FROM link_stats_events
WHERE referrer != ''
GROUP BY stat_date, full_short_url, gid, referrer_domain;
```

### 8.5 Geographic Statistics Materialized View

```sql
-- Geographic statistics
CREATE MATERIALIZED VIEW link_stats_geography_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (full_short_url, stat_date, country_code, city)
AS SELECT
    toDate(event_time) as stat_date,
    full_short_url,
    gid,
    country_code,
    region,
    city,
    count() as pv,
    uniqExact(uv) as uv
FROM link_stats_events
WHERE country_code != ''
GROUP BY stat_date, full_short_url, gid, country_code, region, city;
```

### 8.6 Access Records Table (Optional - for detailed logs)

If you need to query individual access records (not just aggregated stats):

```sql
-- Access records table (same as events table, but optimized for queries)
CREATE TABLE link_stats_access_records
(
    event_time DateTime,
    full_short_url String,
    gid String,
    remote_addr String,
    uv String,
    os String,
    browser String,
    device String,
    network String,
    referrer String,
    country_code String,
    city String
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_time)
ORDER BY (full_short_url, event_time)
TTL event_time + INTERVAL 30 DAY;  -- Shorter retention for detailed logs

-- Populate from events table (optional, if needed)
-- INSERT INTO link_stats_access_records SELECT * FROM link_stats_events;
```

**Note**: For most use cases, querying aggregated materialized views is sufficient. Only create access records table if you need to query individual events.

### 8.7 PostgreSQL Schema (Business Data Only)

**Important**: PostgreSQL (`t_link`) stores only business data (link metadata), NOT statistics.

```sql
-- PostgreSQL: t_link table (existing, unchanged)
-- This table stores:
-- - Link metadata (origin_url, short_uri, domain, etc.)
-- - Business fields (gid, valid_date, description, etc.)
-- - NO statistics data

-- Statistics are stored ONLY in ClickHouse
```

**Data Flow:**
- **PostgreSQL**: Link creation, updates, metadata queries
- **ClickHouse**: All statistics data (PV, UV, aggregations)
- **Query Service**: Merges data from both sources when needed

---

## 9. Performance Considerations

### 9.1 Kafka Performance Optimization

**Producer Configuration:**
- **Batch Size**: 16KB - 32KB for better throughput
- **Linger.ms**: 10-50ms to allow batching
- **Compression**: Snappy or LZ4 for network efficiency
- **Acks**: 1 (leader acknowledgment) for low latency
- **Retries**: 3 with exponential backoff

**Consumer Configuration:**
- **Max Poll Records**: 10000 for batch processing
- **Fetch Min Bytes**: 1MB to reduce network round trips
- **Fetch Max Wait**: 500ms for batching
- **Concurrency**: 10+ consumers per instance for parallel processing

**Kafka Topic Configuration:**
- **Partitions**: 20-50 (partitioned by gid hash for even distribution)
- **Replication Factor**: 3 for high availability
- **Retention**: 7 days (sufficient for reprocessing if needed)
- **Compression**: Snappy or LZ4

### 9.2 ClickHouse Performance Optimization

**Write Optimization:**
- **Batch Inserts**: Insert 1000-10000 events per batch
- **Async Inserts**: Use async insert mode for better throughput
- **Partitioning**: Monthly partitions for efficient data management
- **Ordering Key**: `(full_short_url, event_time, gid)` optimized for link queries

**Query Optimization:**
- **Materialized Views**: Pre-aggregated data eliminates real-time aggregation overhead
- **Partition Pruning**: Queries automatically filter by partition (month)
- **Primary Key**: Efficient lookups by `full_short_url`
- **Columnar Storage**: Only reads required columns, not entire rows

**Storage Optimization:**
- **TTL**: Automatic cleanup of old data (90 days for events, longer for aggregates)
- **Compression**: LZ4 or ZSTD compression (ClickHouse default)
- **MergeTree Settings**: Optimize `max_bytes_to_merge_at_max_space_in_pool` for your workload

**Example Query Performance:**
- Daily stats query: < 50ms (from materialized view)
- Hourly stats query: < 100ms
- Dimension stats query: < 200ms
- Raw events query: < 500ms (with proper WHERE clauses)

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

1. **Kafka Producer Metrics**
   - `shortlink.stats.kafka.producer.records_sent_total`
   - `shortlink.stats.kafka.producer.records_send_failed_total`
   - `shortlink.stats.kafka.producer.record_send_rate`
   - `shortlink.stats.kafka.producer.record_error_rate`
   - `shortlink.stats.kafka.producer.request_latency_avg`
   - `shortlink.stats.kafka.producer.batch_size_avg`

2. **Kafka Consumer Metrics**
   - `shortlink.stats.kafka.consumer.records_consumed_total`
   - `shortlink.stats.kafka.consumer.consumer_lag` (critical!)
   - `shortlink.stats.kafka.consumer.consumption_rate`
   - `shortlink.stats.kafka.consumer.batch_processing_duration`
   - `shortlink.stats.kafka.consumer.commit_latency`

3. **ClickHouse Metrics**
   - `shortlink.stats.clickhouse.inserts_total`
   - `shortlink.stats.clickhouse.inserts_failed_total`
   - `shortlink.stats.clickhouse.insert_duration`
   - `shortlink.stats.clickhouse.query_duration`
   - `shortlink.stats.clickhouse.query_count`
   - `shortlink.stats.clickhouse.table_size_bytes`
   - `shortlink.stats.clickhouse.part_count`

4. **Query Service Metrics**
   - `shortlink.stats.query.duration` (p50, p95, p99)
   - `shortlink.stats.query.cache.hit_rate`
   - `shortlink.stats.query.postgresql.duration`
   - `shortlink.stats.query.clickhouse.duration`
   - `shortlink.stats.query.error_rate`

5. **System Health Metrics**
   - `shortlink.stats.kafka.broker_availability`
   - `shortlink.stats.clickhouse.connection_pool.usage`
   - `shortlink.stats.clickhouse.disk_usage_percent`
   - `shortlink.stats.clickhouse.memory_usage_percent`

### 10.2 Alerts

**Critical Alerts:**
- Kafka consumer lag > 5 minutes (indicates processing bottleneck)
- Kafka producer failure rate > 1%
- ClickHouse insert failure rate > 0.1%
- ClickHouse disk usage > 85%
- Query p99 latency > 500ms

**Warning Alerts:**
- Kafka consumer lag > 1 minute
- ClickHouse query duration p95 > 200ms
- ClickHouse connection pool usage > 80%
- Materialized view refresh lag > 1 hour

### 10.3 Monitoring Tools

**Kafka Monitoring:**
- **Kafka Manager / CMAK**: Topic/partition monitoring
- **Kafka Exporter + Prometheus**: Metrics collection
- **Grafana Dashboards**: Visualization

**ClickHouse Monitoring:**
- **ClickHouse System Tables**: Built-in monitoring queries
- **ClickHouse Exporter + Prometheus**: Metrics export
- **Grafana Dashboards**: Query performance, storage usage

**Application Monitoring:**
- **Micrometer + Prometheus**: Application metrics
- **Distributed Tracing**: Zipkin/Jaeger for request tracing
- **Log Aggregation**: ELK Stack or Loki for log analysis

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

## Appendix D: Why Kafka + ClickHouse for Enterprise-Level Solutions

### D.1 Architecture Decoupling Benefits

**Problem with Spring Scheduler Approach:**
- ❌ Tight coupling with Spring framework lifecycle
- ❌ Difficult to scale independently (must scale entire application)
- ❌ Single point of failure (if app crashes, statistics stop)
- ❌ Resource contention (shares resources with main application)
- ❌ Difficult to monitor and operate separately

**Kafka + Independent Consumer Benefits:**
- ✅ **Complete Decoupling**: Consumer runs as separate service/worker
- ✅ **Independent Scaling**: Scale consumers independently based on load
- ✅ **Fault Isolation**: Consumer failures don't affect main application
- ✅ **Resource Isolation**: Dedicated resources for statistics processing
- ✅ **Operational Flexibility**: Can use different technologies (Java, Python, Go)
- ✅ **Multiple Consumer Groups**: Support different processing pipelines

### D.2 Why Not RDBMS for Aggregation?

**PostgreSQL Limitations for Analytics:**
- ❌ **Row-based Storage**: Inefficient for analytical queries (reads entire rows)
- ❌ **Write Bottleneck**: ACID guarantees limit write throughput (~5K writes/sec)
- ❌ **Aggregation Overhead**: Real-time aggregation is expensive (CPU-intensive)
- ❌ **Storage Cost**: Storing raw events + aggregates is expensive
- ❌ **Scaling Challenges**: Vertical scaling is expensive, horizontal scaling is complex

**ClickHouse Advantages:**
- ✅ **Columnar Storage**: Only reads required columns, 10-100x faster queries
- ✅ **High Write Throughput**: 100K+ inserts/sec per node
- ✅ **Built-in Aggregation**: Materialized views automatically aggregate data
- ✅ **Compression**: 5-10x better compression than row-based databases
- ✅ **Horizontal Scaling**: Easy to add nodes for more capacity
- ✅ **Analytical Functions**: `uniqExact()`, `quantile()`, etc. optimized for analytics

### D.3 Enterprise Scalability Requirements

**Current System Requirements:**
- Daily redirects: 80 million+ (from README)
- Peak QPS: 56,000 redirects/sec
- Need to support 10x growth (800M+ daily redirects)

**Kafka + ClickHouse Capabilities:**
- **Kafka**: Proven at scale (used by LinkedIn, Netflix, Uber)
  - Handles millions of messages per second
  - Horizontal scaling via partitions
  - High availability with replication
  
- **ClickHouse**: Designed for analytics at scale (used by Yandex, Cloudflare)
  - Handles billions of rows efficiently
  - Sub-second queries on large datasets
  - Automatic data compression and partitioning

### D.4 Separation of Concerns

**Clear Data Boundaries:**

| Data Type | Storage | Purpose | Access Pattern |
|-----------|---------|---------|----------------|
| **Link Metadata** | PostgreSQL (`t_link`) | Business data, CRUD operations | Transactional, low volume |
| **Statistics Events** | Kafka → ClickHouse | Raw access events | High volume, append-only |
| **Aggregated Stats** | ClickHouse (Materialized Views) | Pre-computed aggregations | Read-heavy, analytical queries |

**Benefits:**
- ✅ **Independent Optimization**: Optimize each storage for its use case
- ✅ **Independent Scaling**: Scale PostgreSQL for business data, ClickHouse for analytics
- ✅ **Cost Optimization**: Use appropriate storage tier for each data type
- ✅ **Technology Flexibility**: Can replace/upgrade components independently

### D.5 Real-World Enterprise Patterns

**Similar Architectures Used By:**

1. **Uber**: Kafka + ClickHouse for real-time analytics
2. **Yandex**: ClickHouse for web analytics (billions of events/day)
3. **Cloudflare**: Kafka + ClickHouse for network analytics
4. **LinkedIn**: Kafka for event streaming at massive scale

**Why These Companies Chose This Pattern:**
- Need to handle billions of events per day
- Require sub-second query performance
- Need horizontal scalability
- Want to decouple event ingestion from processing

### D.6 Operational Benefits

**Monitoring & Observability:**
- **Kafka**: Rich metrics (lag, throughput, partition distribution)
- **ClickHouse**: Built-in system tables for monitoring
- **Independent Services**: Easier to debug and troubleshoot

**Deployment Flexibility:**
- **Kafka Consumer**: Can deploy as:
  - Kubernetes Deployment (always running)
  - Kubernetes Job (batch processing)
  - Separate microservice
  - Serverless function (AWS Lambda, etc.)

**Disaster Recovery:**
- **Kafka**: Event replay capability (reprocess events if needed)
- **ClickHouse**: Can rebuild materialized views from raw events
- **PostgreSQL**: Only stores business data (smaller, easier to backup)

### D.7 Cost Considerations

**Infrastructure Costs:**

| Component | Cost Factor | Optimization |
|-----------|-------------|--------------|
| **Kafka** | Broker nodes, storage | Use managed service (Confluent Cloud, AWS MSK) |
| **ClickHouse** | Compute nodes, storage | Columnar compression reduces storage by 5-10x |
| **PostgreSQL** | Smaller instance | Only stores business data (not statistics) |

**Total Cost of Ownership:**
- **Initial Setup**: Higher (need Kafka + ClickHouse)
- **Operational Cost**: Lower (better resource utilization)
- **Scaling Cost**: Lower (horizontal scaling is cheaper than vertical)
- **Long-term**: More cost-effective at scale

### D.8 Migration Path

**Phase 1: Start Simple**
- Use existing PostgreSQL for business data
- Add Kafka for event streaming
- Add ClickHouse for statistics

**Phase 2: Optimize**
- Move more analytics to ClickHouse
- Optimize Kafka partitioning
- Tune ClickHouse materialized views

**Phase 3: Scale**
- Add more Kafka partitions
- Scale ClickHouse cluster
- Add more consumer instances

**Backward Compatibility:**
- Existing PostgreSQL schema unchanged
- Query service abstracts storage details
- Can migrate gradually without breaking changes

---

## Summary: Enterprise Architecture Decision

**Why Kafka + ClickHouse is the Right Choice:**

1. ✅ **Decoupled Architecture**: Independent services, no framework coupling
2. ✅ **Enterprise Scale**: Proven to handle billions of events/day
3. ✅ **Performance**: Sub-second queries on massive datasets
4. ✅ **Separation of Concerns**: Right tool for each job (PostgreSQL for business, ClickHouse for analytics)
5. ✅ **Operational Excellence**: Better monitoring, scaling, and fault isolation
6. ✅ **Industry Standard**: Used by major tech companies for similar use cases
7. ✅ **Future-Proof**: Can scale to 10x+ current load without architectural changes

**Trade-offs:**
- ⚠️ **Initial Complexity**: More components to manage
- ⚠️ **Learning Curve**: Team needs to learn Kafka and ClickHouse
- ⚠️ **Infrastructure Cost**: Additional infrastructure required

**Conclusion:**
For an enterprise-level short link platform handling 80M+ daily redirects with potential for 10x growth, **Kafka + ClickHouse** provides the scalability, performance, and operational flexibility needed. The initial investment in infrastructure and learning is justified by the long-term benefits of a scalable, maintainable architecture.

---

**Document Version**: 2.0  
**Last Updated**: 2026-01-27  
**Author**: Design Team  
**Status**: Final - Kafka + ClickHouse Enterprise Architecture
