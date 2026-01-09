package org.tus.shortlink.sharding;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class ShardRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    public Object determineCurrentLookupKey() {
        return ShardContextHolder.getShard();
    }
}