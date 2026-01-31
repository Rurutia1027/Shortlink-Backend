# ClickHouse Connection and Data Verification Guide

Use this guide to confirm:

1) whether the ClickHouse database and tables were initialized successfully;
2) whether the local shortlink app is configured correctly and connected to ClickHouse.

---

## 1. Viewing / Connecting to ClickHouse Data and Schema

Once port 8123 is port-forwarded to your machine, you can query ClickHouse via the **HTTP interface** or *
*clickhouse-client**.

### 1.1 HTTP interface (curl, no client install)

```bash
# Health check
curl -s http://localhost:8123/ping
# Expected: Ok.

# List all databases
curl -s "http://localhost:8123/?query=SHOW+DATABASES"

# List tables in shortlink_stats
curl -s "http://localhost:8123/?query=SHOW+TABLES+FROM+shortlink_stats"

# Simple query (confirm DB and table exist)
curl -s "http://localhost:8123/?database=shortlink_stats" --data "SELECT count() FROM link_stats_events"
# Returns 0 when empty; errors if the table does not exist.
```

If `SHOW DATABASES` includes `shortlink_stats` and `SHOW TABLES FROM shortlink_stats` shows `link_stats_events`,
`link_stats_daily`, `link_stats_hourly`, and the `*_mv` views, the init Job has successfully created the database and
tables.

### 1.2 clickhouse-client (optional)

```bash
# If you have clickhouse-client installed locally
clickhouse-client --host localhost --port 9000  # or 8123 for HTTP
# Then run:
# SHOW DATABASES;
# USE shortlink_stats;
# SHOW TABLES;
# SELECT * FROM link_stats_events LIMIT 1;
```

If you only port-forward 8123, using curl over HTTP is enough; port 9000 is the native protocol and would need a
separate forward.

---

## 2. ClickHouse Configuration for the Local Shortlink App

The app connects to ClickHouse **only** when `clickhouse.url` is set (`ClickHouseConfig` uses
`@ConditionalOnExpression`).

### 2.1 Where the config comes from

- **`application.yml`**
    - `clickhouse.url` = env var `CLICKHOUSE_URL`, default **empty**
    - `clickhouse.database` = `CLICKHOUSE_DATABASE`, default `shortlink_stats` (informational; the actual database is
      determined by the **database in the JDBC URL**)

- **JDBC URL format (official)**
    - `jdbc:clickhouse:http://<host>:<port>/<database>`
    - With local port-forward on 8123, use:
      **`jdbc:clickhouse:http://localhost:8123/shortlink_stats`**

### 2.2 How to configure locally

Use any one of these:

**Option A: Environment variable (recommended for local dev)**

```bash
export CLICKHOUSE_URL="jdbc:clickhouse:http://localhost:8123/shortlink_stats"
# Then start the app
mvn spring-boot:run -pl shortlink
```

**Option B: `application-local.yml` (if present)**

```yaml
clickhouse:
  url: jdbc:clickhouse:http://localhost:8123/shortlink_stats
  database: shortlink_stats
```

**Option C: Command-line argument**

```bash
mvn spring-boot:run -pl shortlink -- -Dclickhouse.url=jdbc:clickhouse:http://localhost:8123/shortlink_stats
```

If `CLICKHOUSE_URL` is not set (or `clickhouse.url` is empty), the app **does not** create ClickHouse beans and the
stats API uses the “no ClickHouse” path (returns empty/zero). That is expected.

---

## 3. Confirming the App Is Using the Config to Connect to ClickHouse

### 3.1 Startup log

When a valid `clickhouse.url` is configured, startup will log (from `ClickHouseConfig`):

```text
ClickHouse datasource configured: url=jdbc:clickhouse:http://localhost:8123/shortlink_stats
```

If this line **does not** appear, `clickhouse.url` is empty or not applied and ClickHouse is not enabled.

### 3.2 Calling the stats API

- **ClickHouse enabled and tables empty**
    - `GET /api/shortlink/v1/stats?fullShortUrl=xxx&gid=xxx&startDate=2025-01-01&endDate=2025-01-31`
    - Returns pv/uv/uip etc. as 0 with a valid response shape and **no** ClickHouse errors.

- **ClickHouse not enabled**
    - Same 0 and empty lists, but no real ClickHouse queries.

- **ClickHouse enabled but connection/query failure**
    - You will see `ClickHouse queryXXX failed: ...` in the logs; stats still returns 0 (graceful degradation).

So:

- **“ClickHouse datasource configured”** in logs + stats API returns 0 with no errors → the app **is** using the config
  to connect to ClickHouse; there is just no data yet.
- **“ClickHouse queryXXX failed”** in logs → connected but a query failed (e.g. table/column mismatch or network); use
  the log message to debug.

---

## 4. Self-Check Checklist

| Step                          | Command / action                                                          | Expected                                                     |
|-------------------------------|---------------------------------------------------------------------------|--------------------------------------------------------------|
| 1. CK reachable               | `curl -s http://localhost:8123/ping`                                      | Returns `Ok.`                                                |
| 2. DB created                 | `curl -s "http://localhost:8123/?query=SHOW+DATABASES"`                   | Includes `shortlink_stats`                                   |
| 3. Tables created             | `curl -s "http://localhost:8123/?query=SHOW+TABLES+FROM+shortlink_stats"` | Includes `link_stats_events`, `link_stats_daily`, etc.       |
| 4. App has CK enabled         | Startup log                                                               | Line: “ClickHouse datasource configured: url=...”            |
| 5. App uses config to connect | Call stats API + check logs                                               | No “queryXXX failed” means connection and config are correct |

---

## 5. Common Issues

- **No “ClickHouse datasource configured”**
    - `CLICKHOUSE_URL` is unset or empty. Check env vars or `clickhouse.url` in `application-*.yml`.

- **Config set but still no such log**
    - Confirm active profile and config load order (e.g. whether `application-local.yml` is active).

- **SHOW TABLES is empty or shortlink_stats missing**
    - The Argo CD clickhouse-init Job may not have run or may have failed. Check Job/Pod logs in the cluster or re-run
      the init.

- **Stats API returns 500 or ClickHouse errors**
    - Look for “ClickHouse queryXXX failed” in app logs. Often the table/column layout does not match the SQL in code;
      compare `ClickHouseStatsServiceImpl` with the init DDL.
