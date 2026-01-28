# Kafka Infrastructure Setup Guide

This document describes how to set up Kafka infrastructure for the ShortLink statistics module.

## Phase 1: Kafka Infrastructure Setup

### 1.1 Prerequisites

- Java 17+
- Maven 3.8+
- Docker (for local development) or access to Kafka cluster

### 1.2 Dependencies Added

**Parent POM** (`pom.xml`):

- Added Kafka version property: `kafka.version: 3.7.0`
- Added `kafka-clients` dependency management

**Shortlink Module** (`shortlink/pom.xml`):

- Added `spring-kafka` dependency

### 1.3 Configuration Files

#### Application Configuration (`application.yml`)

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

kafka:
  topics:
    stats-events: ${KAFKA_TOPIC_STATS_EVENTS:shortlink-stats-events}
    stats-events:
      partitions: ${KAFKA_TOPIC_STATS_EVENTS_PARTITIONS:20}
      replication-factor: ${KAFKA_TOPIC_STATS_EVENTS_REPLICATION_FACTOR:3}
  consumer:
    group-id: ${KAFKA_CONSUMER_GROUP_ID:shortlink-stats-aggregator}
```

#### Production Configuration (`application-prod.yml`)

Uses Kubernetes service DNS:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka.shortlink.svc.cluster.local:9092}
```

### 1.4 Kafka Configuration Classes

#### `ShortlinkKafkaConfig.java`

- Configures Kafka Producer Factory
- Optimized for high throughput:
    - Batch size: 16KB
    - Compression: Snappy
    - Idempotence: Enabled
    - Buffer memory: 32MB

#### `KafkaTopicConfig.java`

- Creates Kafka topics programmatically
- Topic: `shortlink-stats-events`
    - Partitions: 20 (configurable)
    - Replication factor: 3 (configurable)
    - Retention: 7 days

## 1.5 Kubernetes Deployment Setup

Kafka is deployed using Kustomize in Kubernetes, following the same pattern as Redis and PostgreSQL.

### Kubernetes Resources

Kafka resources are defined in `k8s/base/kafka/`:

- **Zookeeper StatefulSet**: Single replica for development, can be scaled for production
- **Kafka StatefulSet**: Single replica for development, can be scaled for production
- **Services**: Headless services for Zookeeper and Kafka

### Deployment

Kafka is automatically included in both dev and prod overlays:

**Dev Environment:**

```bash
kubectl apply -k k8s/overlays/dev/
```

**Prod Environment:**

```bash
kubectl apply -k k8s/overlays/prod/
```

### Service Discovery

Kafka service is available at:

- **Service Name**: `kafka.shortlink.svc.cluster.local`
- **Port**: `9092`
- **Zookeeper**: `zookeeper.shortlink.svc.cluster.local:2181`

### Verify Kafka Deployment

**Check Kafka pods:**

```bash
kubectl get pods -n shortlink | grep -E "kafka|zookeeper"
```

**Check Kafka service:**

```bash
kubectl get svc -n shortlink kafka zookeeper
```

**Check Kafka logs:**

```bash
kubectl logs -n shortlink -l app=kafka --tail=50
kubectl logs -n shortlink -l app=zookeeper --tail=50
```

**Verify topic creation (after application starts):**

```bash
# Exec into Kafka pod
kubectl exec -it -n shortlink kafka-0 -- bash

# List topics
kafka-topics --bootstrap-server localhost:9092 --list

# Describe topic
kafka-topics --bootstrap-server localhost:9092 --describe --topic shortlink-stats-events
```

### Alternative: Managed Kafka Service

For production, consider using managed Kafka services:

- **AWS MSK**: Amazon Managed Streaming for Apache Kafka
- **Confluent Cloud**: Fully managed Kafka service
- **Azure Event Hubs**: Kafka-compatible service

If using managed service, update `application-prod.yml`:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:your-managed-kafka-endpoint:9092}
```

## 1.6 Environment Variables

For production deployment, set these environment variables:

```bash
# Kafka bootstrap servers
export KAFKA_BOOTSTRAP_SERVERS=kafka.shortlink.svc.cluster.local:9092

# Topic configuration
export KAFKA_TOPIC_STATS_EVENTS=shortlink-stats-events
export KAFKA_TOPIC_STATS_EVENTS_PARTITIONS=20
export KAFKA_TOPIC_STATS_EVENTS_REPLICATION_FACTOR=3

# Consumer group
export KAFKA_CONSUMER_GROUP_ID=shortlink-stats-aggregator
```

## 1.7 Scaling for Production

For production, you may want to scale Kafka:

**Scale Kafka replicas:**

```bash
kubectl scale statefulset kafka -n shortlink --replicas=3
```

**Scale Zookeeper replicas:**

```bash
kubectl scale statefulset zookeeper -n shortlink --replicas=3
```

**Note**: When scaling Kafka, update:

- `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR` to match replica count
- `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR` to match replica count
- Update `kafka.topics.stats-events.replication-factor` in application config

## 1.8 Next Steps

After Kafka infrastructure is set up:

1. ✅ **Phase 1 Complete**: Kafka infrastructure configured
2. **Phase 2**: Implement event publishing service
3. **Phase 3**: Set up ClickHouse infrastructure
4. **Phase 4**: Implement Kafka consumer with Spring integration

## 1.9 Troubleshooting

### Topic not created automatically

- Check Kafka admin client can connect to brokers
- Verify `KAFKA_BOOTSTRAP_SERVERS` is correct
- Check application logs for KafkaAdmin errors

### Connection refused

- Verify Kafka is running: `docker ps` or `kubectl get pods`
- Check bootstrap servers address is correct
- Verify network connectivity

### Serialization errors

- Ensure `ShortLinkStatsRecordDTO` is properly serializable
- Check JSON serializer configuration
- Verify Jackson dependencies are present

## 1.10 Monitoring

### Kafka Metrics (via JMX)

- Message rate (messages/sec)
- Byte rate (bytes/sec)
- Request latency
- Error rate

### Application Metrics

- Producer send rate
- Producer error rate
- Producer latency

### Health Checks

Spring Boot Actuator provides Kafka health check:

```
GET /api/shortlink/actuator/health
```

---

**Status**: Phase 1 Complete ✅
**Next**: Phase 2 - Event Publishing Implementation
