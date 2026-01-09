package org.tus.shortlink.sharding.unit;

import org.junit.jupiter.api.Test;
import org.tus.shortlink.sharding.ShardContextHolder;
import org.tus.shortlink.sharding.ShardRoutingDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ShardRoutingDataSourceTest {

    @Test
    void should_return_current_shard_from_context() {
        ShardRoutingDataSource routingDataSource = new ShardRoutingDataSource();
        ShardContextHolder.setShard("ds_1");
        Object lookupKey = routingDataSource.determineCurrentLookupKey();
        assertEquals("ds_1", lookupKey);
        ShardContextHolder.clear();
    }

    @Test
    void should_return_null_if_no_shard_set() {
        ShardRoutingDataSource routingDataSource = new ShardRoutingDataSource();
        assertNull(routingDataSource.determineCurrentLookupKey());
    }
}
