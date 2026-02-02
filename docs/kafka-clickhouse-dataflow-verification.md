# Kafka → ClickHouse Data Flow Verification Guide

This guide helps you verify the complete data flow: **Application → Kafka → ClickHouse**.

---

## Prerequisites

1. ✅ Kafka running on `localhost:9092`
2. ✅ ClickHouse running on `localhost:8123` (user: `default`, password: `default`)
3. ✅ ClickHouse Kafka engine tables created (via init job or manually)
4. ✅ Application configured with `clickhouse.kafka-sync-enabled=true`

---

## Step 1: Verify Kafka Topic Exists

```bash
# List all topics
kafka-topics.sh --bootstrap-server localhost:9092 --list

# Check topic details
kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic shortlink-stats-events

# If topic doesn't exist, create it
kafka-topics.sh --bootstrap-server localhost:9092 --create \
  --topic shortlink-stats-events \
  --partitions 2 \
  --replication-factor 1
```

---

## Step 2: Verify ClickHouse Kafka Engine Table

Connect to ClickHouse (via IntelliJ IDEA or `clickhouse-client`):

```sql
-- Check if Kafka engine table exists
SHOW TABLES FROM shortlink_stats LIKE '%kafka%';

-- Should see: link_stats_kafka

-- Check Kafka table configuration
SHOW CREATE TABLE shortlink_stats.link_stats_kafka;

-- Check Materialized View
SHOW CREATE TABLE shortlink_stats.link_stats_kafka_mv;
```

**Expected output:**
- `link_stats_kafka` - Kafka engine table
- `link_stats_kafka_mv` - Materialized View that syncs to `link_stats_events`

---

## Step 3: Send Test Event via Application

### Option A: Via REST API (if you have restoreUrl endpoint)

```bash
# Send a request to restore a short link
curl -X GET "http://localhost:8081/api/shortlink/restore?shortUri=abc123" \
  -H "Accept: application/json"

# This should trigger:
# 1. Application publishes event to Kafka topic
# 2. ClickHouse Kafka engine consumes from topic
# 3. Data flows into link_stats_events table
```

### Option B: Direct Kafka Producer (for testing)

```bash
# Send a test message directly to Kafka
kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic shortlink-stats-events

# Then paste this JSON (press Enter, then Ctrl+D):
{
  "gid": "test-group-001",
  "fullShortUrl": "http://shortlink.tus/abc123",
  "remoteAddr": "192.168.1.100",
  "uv": "user-123",
  "os": "MacOS",
  "browser": "Chrome",
  "device": "Desktop",
  "network": "WiFi",
  "referrer": "https://example.com",
  "userAgent": "Mozilla/5.0...",
  "uvFirstFlag": 1,
  "uipFirstFlag": 1,
  "keys": "test-key",
  "currentDate": 1706860800000
}
```

---

## Step 4: Verify Event in Kafka

```bash
# Consume messages from Kafka topic (verify event was published)
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic shortlink-stats-events \
  --from-beginning \
  --max-messages 10
```

**Expected:** You should see the JSON event you sent.

---

## Step 5: Verify Event in ClickHouse

Wait a few seconds (ClickHouse polls Kafka every 1-2 seconds), then check:

```sql
-- Check raw events table
SELECT count() FROM shortlink_stats.link_stats_events;

-- View recent events
SELECT * FROM shortlink_stats.link_stats_events 
ORDER BY event_time DESC 
LIMIT 10;

-- Check Kafka engine table (should show consumed messages)
SELECT * FROM shortlink_stats.link_stats_kafka LIMIT 5;

-- Check Kafka consumer status
SELECT * FROM system.kafka_consumers 
WHERE table = 'link_stats_kafka';
```

**Expected:**
- `link_stats_events` should have new rows
- `link_stats_kafka` may show messages (or empty if already consumed)
- `system.kafka_consumers` shows consumer lag (should be near 0)

---

## Step 6: Verify Aggregated Data

```sql
-- Check daily stats (aggregated)
SELECT 
    stat_date,
    full_short_url,
    sumMerge(pv) AS pv,
    uniqExactMerge(uv) AS uv,
    uniqExactMerge(uip) AS uip
FROM shortlink_stats.link_stats_daily
GROUP BY stat_date, full_short_url
ORDER BY stat_date DESC;

-- Check hourly stats
SELECT * FROM shortlink_stats.link_stats_hourly
ORDER BY stat_hour DESC LIMIT 10;
```

---

## Troubleshooting

### Issue: No data in ClickHouse

1. **Check Kafka connectivity from ClickHouse:**
   ```sql
   SELECT * FROM system.kafka_consumers 
   WHERE table = 'link_stats_kafka';
   ```
   - If empty or shows errors → Kafka connection issue

2. **Check ClickHouse logs:**
   ```bash
   # If ClickHouse is in K8s
   kubectl logs -n shortlink clickhouse-0 | grep -i kafka
   
   # If local
   tail -f /var/log/clickhouse-server/clickhouse-server.err.log | grep -i kafka
   ```

3. **Verify Kafka broker is reachable:**
   ```sql
   -- Test from ClickHouse
   SELECT * FROM system.kafka_consumers 
   WHERE table = 'link_stats_kafka' 
   FORMAT Vertical;
   ```

### Issue: Kafka topic has messages but ClickHouse doesn't consume

1. **Check consumer group:**
   ```bash
   kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
     --group clickhouse-shortlink-stats --describe
   ```

2. **Reset consumer group offset (if needed):**
   ```bash
   kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
     --group clickhouse-shortlink-stats \
     --topic shortlink-stats-events \
     --reset-offsets --to-earliest --execute
   ```

### Issue: Authentication failed

- Verify ClickHouse user/password: `default/default`
- Check `application.yml`: `clickhouse.username` and `clickhouse.password`

---

## Quick Test Script

```bash
#!/bin/bash
# Quick verification script

echo "=== Step 1: Check Kafka Topic ==="
kafka-topics.sh --bootstrap-server localhost:9092 --list | grep shortlink-stats-events

echo ""
echo "=== Step 2: Send Test Event ==="
echo '{"gid":"test","fullShortUrl":"http://test.com/abc","remoteAddr":"1.2.3.4","uv":"user1","os":"Linux","browser":"Chrome","device":"Desktop","network":"WiFi","referrer":"","userAgent":"test","uvFirstFlag":1,"uipFirstFlag":1,"keys":"","currentDate":'$(date +%s000)'}' | \
kafka-console-producer.sh --bootstrap-server localhost:9092 --topic shortlink-stats-events

echo ""
echo "=== Step 3: Wait 5 seconds for ClickHouse to consume ==="
sleep 5

echo ""
echo "=== Step 4: Check ClickHouse ==="
echo "Run in ClickHouse:"
echo "  SELECT count() FROM shortlink_stats.link_stats_events;"
```

---

## Expected Data Flow

```
1. Application (restoreUrl) 
   ↓ publishes event
2. Kafka Topic (shortlink-stats-events)
   ↓ ClickHouse Kafka engine consumes
3. link_stats_kafka (Kafka engine table)
   ↓ Materialized View triggers
4. link_stats_events (raw events table)
   ↓ Materialized Views aggregate
5. link_stats_daily, link_stats_hourly, etc. (aggregated tables)
```

---

## Success Criteria

✅ Kafka topic receives events  
✅ ClickHouse `link_stats_kafka` table can see messages  
✅ `link_stats_events` table has new rows  
✅ Aggregated tables (`link_stats_daily`) show data  
✅ Consumer lag is near zero  
