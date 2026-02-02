# Docker Compose Local Development Environment

This Docker Compose setup provides a complete local development environment matching the production configuration.

## Services (All on `shortlink-network`)

- **PostgreSQL** (port 5432): Databases `admin` and `shortlink`
- **Redis** (port 6379): Cache with password `StrongRedisPass123!`
- **Zookeeper** (port 2181): For Kafka coordination
- **Kafka** (port 9092): Message broker with topic `shortlink-stats-events` (auto-created)
- **ClickHouse** (ports 8123 HTTP, 9000 Native, 9009 Interserver): Analytics database

All services are connected to the same Docker network (`shortlink-network`) for inter-container communication.

## Quick Start

### 1. Start all services

```bash
docker-compose up -d
```

### 2. Check service status

```bash
docker-compose ps
```

### 3. View logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f kafka
docker-compose logs -f clickhouse
```

### 4. Stop services

```bash
docker-compose down
```

### 5. Stop and remove volumes (clean slate)

```bash
docker-compose down -v
```

## Service Details

### PostgreSQL

- **User/Password**: `admin/admin`
- **Databases**: `admin`, `shortlink` (auto-created on first start)
- **Tables**: Automatically created from Flyway migration scripts
  - `admin` database: `t_user`, `t_group`, `t_group_unique`
  - `shortlink` database: `t_link`, `t_link_goto`
- **Port**: `5432`
- **Storage**: Container filesystem (data lost on container removal)
- **Initialization Order**:
  1. Create databases (`01_create_databases.sql`)
  2. Create admin tables (`02_init_admin_tables.sh`)
  3. Create shortlink tables (`03_init_shortlink_tables.sh`)

### Redis

- **Password**: `StrongRedisPass123!`
- **Port**: `6379`
- **Storage**: Container filesystem (data lost on container removal)
- **Persistence**: Disabled (simple dev setup)

### Kafka

- **Bootstrap Server**: `localhost:9092`
- **Topic**: `shortlink-stats-events` (20 partitions, replication factor 1)
- **Auto-create topics**: Enabled
- **Storage**: Container filesystem (data lost on container removal)

The Kafka topic is automatically created by the `kafka-init` service after Kafka is ready.

### ClickHouse

- **HTTP Port**: `8123`
- **Native Port**: `9000`
- **User/Password**: `default/default` (password set during initialization)
- **Database**: `shortlink_stats` (auto-created)
- **Kafka Engine**: Configured to consume from `kafka:9092` topic `shortlink-stats-events`
- **Storage**: Container filesystem (data lost on container removal)

ClickHouse initialization (via `clickhouse-init` service):
1. Waits for ClickHouse to be healthy
2. Sets password for `default` user
3. Creates the `shortlink_stats` database
4. Creates all tables and materialized views
5. Sets up Kafka engine table to consume from Kafka topic

**Note**: All services use container filesystem storage. Data is ephemeral and will be lost when containers are removed. This is intentional for local development.

## Application Configuration

Your Spring Boot application should use these connection strings:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/shortlink
    username: admin
    password: admin
  
  data:
    redis:
      host: localhost
      port: 6379
      password: StrongRedisPass123!
  
  kafka:
    bootstrap-servers: localhost:9092

clickhouse:
  url: jdbc:clickhouse://localhost:8123
  username: default
  password: default
  database: shortlink_stats
  kafka-broker-list: localhost:9092
```

## Troubleshooting

### Kafka topic not created

If the topic creation fails, you can manually create it:

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
   docker-compose exec clickhouse clickhouse-client --query "SHOW TABLES FROM shortlink_stats"
   ```

3. Check Kafka engine status:
   ```bash
   docker-compose exec clickhouse clickhouse-client --query "SELECT * FROM system.kafka_consumers"
   ```

### Reset everything

To start fresh (removes all containers and data):

```bash
docker-compose down
docker-compose up -d
```

**Note**: Since we're using container filesystem storage (no volumes), data is automatically cleared when containers are removed.

## Health Checks

All services have health checks configured. Check status:

```bash
docker-compose ps
```

All services should show `(healthy)` status after startup.

## Network

All services are on the `shortlink-network` bridge network and can communicate using service names:
- `postgres:5432`
- `redis:6379`
- `kafka:9092`
- `clickhouse:8123`

For local application connections, use `localhost` with the mapped ports.
