package org.tus.shortlink.sharding.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.tus.shortlink.sharding.ShardContextHolder;
import org.tus.shortlink.sharding.integration.config.TestConfig;
import org.tus.shortlink.sharding.integration.svc.ShardingTestService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(classes = TestConfig.class)
public class ShardingAspectTest {
    @Autowired
    private ShardingTestService testService;

    @Test
    void should_set_shard_before_method_execute() {
        String shard = testService.execute("gid-123");

        // hashCOde % 2 = 1 (value 1 is expected)
        assertEquals("ds_1", shard);
    }

    @Test
    void should_clear_shard_after_method_execution() {
        testService.execute("gid-456");
        assertNull(ShardContextHolder.getShard());
    }
}
