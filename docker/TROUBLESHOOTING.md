# Troubleshooting Guide

## ClickHouse Startup Issues

### Problem: ClickHouse container fails to start

**Symptoms:**
- Container status shows `Error` or `Unhealthy`
- `clickhouse-init` fails with "dependency failed to start"

**Solutions:**

1. **Check ClickHouse logs:**
   ```bash
   docker-compose logs clickhouse
   ```

2. **Common issues:**
   - **Config file errors**: If using custom config files, ensure XML is valid
   - **Permission issues**: Ensure volumes have correct permissions
   - **Port conflicts**: Ensure ports 8123, 9000, 9009 are not in use

3. **Reset ClickHouse (removes all data):**
   ```bash
   docker-compose down -v
   docker volume rm shortlink-platform_clickhouse-data shortlink-platform_clickhouse-logs
   docker-compose up -d clickhouse
   ```

4. **Verify ClickHouse is accessible:**
   ```bash
   # Without password (default user initially has no password)
   docker-compose exec clickhouse clickhouse-client --user default -q "SELECT 1"
   
   # With password (after initialization)
   docker-compose exec clickhouse clickhouse-client --user default --password default -q "SELECT 1"
   ```

### Problem: ClickHouse init fails

**Symptoms:**
- `clickhouse-init` container exits with error
- Tables/database not created

**Solutions:**

1. **Check init logs:**
   ```bash
   docker-compose logs clickhouse-init
   ```

2. **Manually run initialization:**
   ```bash
   docker-compose exec clickhouse-init bash
   # Inside container:
   clickhouse-client --host clickhouse --port 9000 --user default --password default -q "SELECT 1"
   ```

3. **Re-run initialization:**
   ```bash
   docker-compose rm -f clickhouse-init
   docker-compose up clickhouse-init
   ```

## Kafka Topic Creation Issues

### Problem: Topic not created

**Solutions:**

1. **Check kafka-init logs:**
   ```bash
   docker-compose logs kafka-init
   ```

2. **Manually create topic:**
   ```bash
   docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 \
     --create --if-not-exists \
     --topic shortlink-stats-events \
     --partitions 20 \
     --replication-factor 1
   ```

3. **Verify topic exists:**
   ```bash
   docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
   ```

## PostgreSQL Table Creation Issues

### Problem: Tables not created

**Solutions:**

1. **Check PostgreSQL logs:**
   ```bash
   docker-compose logs postgres
   ```

2. **Verify databases exist:**
   ```bash
   docker-compose exec postgres psql -U admin -d postgres -c "\l"
   ```

3. **Verify tables exist:**
   ```bash
   # Admin database
   docker-compose exec postgres psql -U admin -d admin -c "\dt"
   
   # Shortlink database
   docker-compose exec postgres psql -U admin -d shortlink -c "\dt"
   ```

4. **Re-run initialization:**
   ```bash
   docker-compose down -v
   docker-compose up -d postgres
   ```

## Network Connectivity Issues

### Problem: Services can't communicate

**Solutions:**

1. **Verify all services are on the same network:**
   ```bash
   docker network inspect shortlink-platform_shortlink-network
   ```

2. **Test connectivity from container:**
   ```bash
   # From ClickHouse container to Kafka
   docker-compose exec clickhouse ping -c 3 kafka
   
   # From Kafka container to ClickHouse
   docker-compose exec kafka ping -c 3 clickhouse
   ```

3. **Check service DNS resolution:**
   ```bash
   docker-compose exec clickhouse nslookup kafka
   ```

## General Debugging

### View all container status:
```bash
docker-compose ps
```

### View logs for all services:
```bash
docker-compose logs -f
```

### View logs for specific service:
```bash
docker-compose logs -f [service-name]
```

### Restart a specific service:
```bash
docker-compose restart [service-name]
```

### Remove and recreate a service:
```bash
docker-compose rm -f [service-name]
docker-compose up -d [service-name]
```

### Complete reset (removes all data):
```bash
docker-compose down -v
docker-compose up -d
```
