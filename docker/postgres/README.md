# PostgreSQL Initialization Scripts

This directory contains SQL and shell scripts that are automatically executed when PostgreSQL container starts for the first time.

## Execution Order

Scripts in `/docker-entrypoint-initdb.d` are executed in alphabetical order:

1. **`01_create_databases.sql`** - Creates `admin` and `shortlink` databases
2. **`02_init_admin_tables.sh`** - Creates tables in `admin` database
   - `t_user`
   - `t_group`
   - `t_group_unique`
3. **`03_init_shortlink_tables.sh`** - Creates tables in `shortlink` database
   - `t_link`
   - `t_link_goto`

## Source

These scripts are converted from Flyway migration files:
- `flyway/src/main/resources/migration/db/admin/V1__init_tables.sql`
- `flyway/src/main/resources/migration/db/shortlink/V1__init_tables.sql`

## Verification

After PostgreSQL starts, verify tables are created:

```bash
# Check admin database tables
docker-compose exec postgres psql -U admin -d admin -c "\dt"

# Check shortlink database tables
docker-compose exec postgres psql -U admin -d shortlink -c "\dt"
```

## Notes

- Scripts are only executed on first container start (when data volume is empty)
- To re-run initialization, remove the volume: `docker-compose down -v`
- Shell scripts (`.sh`) are automatically made executable by PostgreSQL container
