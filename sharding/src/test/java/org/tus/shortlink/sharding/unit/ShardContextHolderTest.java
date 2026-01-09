package org.tus.shortlink.sharding.unit;

import org.junit.jupiter.api.Test;
import org.tus.shortlink.sharding.ShardContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ShardContextHolderTest {
    @Test
    void should_set_and_get_shard_key() {
        ShardContextHolder.setShard("ds_0");
        assertEquals("ds_0", ShardContextHolder.getShard());
    }

    @Test
    void should_clear_shard_key() {
        ShardContextHolder.setShard("ds_1");
        ShardContextHolder.clear();
        assertNull(ShardContextHolder.getShard());
    }

    @Test
    void should_not_leak_between_calls() {
        ShardContextHolder.setShard("ds_0");
        ShardContextHolder.clear();
        assertNull(ShardContextHolder.getShard());
    }
}
