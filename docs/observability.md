# ShortLink Observability & Statistics 

## Current Status 
- Statistics APIs are defined at controller level 
- No concrete backend implementation yet 
- Metrics are NOT stored in relational database 

## Design Principles 
- API-first 
- Storage-agnostic
- Cloud-native observability preferred 


## Planned Backends 
- Prometheus / VictoriaMetrics (metrics)
- ClickHouse / Druid (access records)
- Grafana (visualization)

## Non-Goals 
- No RDB-based PV/UV accumulate 
- No tight coupling with ORM layer 