# Docker Compose Configuration Reference

This document maps Docker Compose services to Spring Boot `application.yml` configuration.

## Service Network

All services are connected to the same Docker network: `shortlink-network`

- **Container-to-container communication**: Use service names (e.g., `kafka:9092`, `postgres:5432`)
- **Local application communication**: Use `localhost` with mapped ports (e.g., `localhost:9092`, `localhost:5432`)

## Service Mapping

### PostgreSQL

**Docker Compose:**
- Service name: `postgres`
- Port mapping: `5432:5432`
- User/Password: `admin/admin`
- Databases: `admin`, `shortlink` (auto-created)
- Tables: Automatically created from Flyway migration scripts
  - `admin` database: `t_user`, `t_group`, `t_group_unique`
  - `shortlink` database: `t_link`, `t_link_goto`

**application.yml:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/shortlink  # or admin for admin service
    username: admin
    password: admin
```

**Initialization Scripts** (executed in order):
1. `01_create_databases.sql` - Creates `admin` and `shortlink` databases
2. `02_init_admin_tables.sh` - Creates tables in `admin` database (from Flyway `migration/db/admin/V1__init_tables.sql`)
3. `03_init_shortlink_tables.sh` - Creates tables in `shortlink` database (from Flyway `migration/db/shortlink/V1__init_tables.sql`)

### Redis

**Docker Compose:**
- Service name: `redis`
- Port mapping: `6379:6379`
- Password: `StrongRedisPass123!`
- AOF persistence: Enabled

**application.yml:**
```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: StrongRedisPass123!
```

### Kafka

**Docker Compose:**
- Service name: `kafka`
- Port mapping: `9092:9092`
- Advertised listeners: `PLAINTEXT://localhost:9092` (for local app access)
- Topic: `shortlink-stats-events` (20 partitions, replication-factor=1)
- Auto-create topics: Enabled
- Topic creation: Handled by `kafka-init` service

**application.yml:**
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

kafka:
  topics:
    stats-events:
      name: shortlink-stats-events
      partitions: 20
      replication-factor: 1  # Matches Docker Compose
      auto-create: true  # Enabled for local dev
```

**Note:** Kafka topic is automatically created by `kafka-init` service, but `auto-create=true` allows the application to create it if missing.

### ClickHouse

**Docker Compose:**
- Service name: `clickhouse`
- Port mappings:
  - HTTP: `8123:8123`
  - Native: `9000:9000`
  - Interserver: `9009:9009`
- User/Password: `default/default`
- Database: `shortlink_stats` (auto-created)
- Kafka Engine: Configured to consume from `kafka:9092` (container network)

**application.yml:**
```yaml
clickhouse:
  url: jdbc:clickhouse://localhost:8123
  username: default
  password: default
  database: shortlink_stats
  kafka-sync-enabled: true
  kafka-broker-list: localhost:9092  # For local app, use localhost
  kafka-topic: shortlink-stats-events
  kafka-consumer-group: clickhouse-shortlink-stats
```

**Important:** 
- ClickHouse Kafka engine uses `kafka:9092` (container network) in `03_kafka_sync.sql`
- Application uses `localhost:9092` (host network) in `application.yml`

### Zookeeper

**Docker Compose:**
- Service name: `zookeeper`
- Port mapping: `2181:2181`
- Used by: Kafka (internal, not exposed to application)

**application.yml:**
- Not directly configured (used internally by Kafka)

## Initialization Order

1. **PostgreSQL** → Creates databases `admin` and `shortlink`
2. **Zookeeper** → Starts and becomes healthy
3. **Kafka** → Starts after Zookeeper is healthy
4. **Redis** → Starts independently
5. **ClickHouse** → Starts independently
6. **kafka-init** → Creates topic `shortlink-stats-events` after Kafka is healthy
7. **clickhouse-init** → Creates database, tables, and Kafka engine after ClickHouse and Kafka are healthy

## Verification

### Check all services are running:
```bash
docker-compose ps
```

### Verify Kafka topic exists:
```bash
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

### Verify ClickHouse database and tables:
```bash
docker-compose exec clickhouse clickhouse-client --user default --password default --query "SHOW DATABASES"
docker-compose exec clickhouse clickhouse-client --user default --password default --database shortlink_stats --query "SHOW TABLES"
```

### Verify ClickHouse Kafka engine:
```bash
docker-compose exec clickhouse clickhouse-client --user default --password default --database shortlink_stats --query "SELECT * FROM system.kafka_consumers"
```

## Troubleshooting

### Service not accessible from localhost

Ensure the service is running and port mapping is correct:
```bash
docker-compose ps
docker-compose logs [service-name]
```

### Kafka topic not created

Manually create the topic:
```bash
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 \
  --create --if-not-exists \
  --topic shortlink-stats-events \
  --partitions 20 \
  --replication-factor 1
```

### ClickHouse not consuming from Kafka

1. Check ClickHouse logs:
   ```bash
   docker-compose logs clickhouse
   ```

2. Verify Kafka engine table exists:
   ```bash
   docker-compose exec clickhouse clickhouse-client --user default --password default --database shortlink_stats --query "SHOW TABLES LIKE 'link_stats_kafka%'"
   ```

3. Check Kafka connectivity from ClickHouse container:
   ```bash
   docker-compose exec clickhouse ping -c 3 kafka
   ```
