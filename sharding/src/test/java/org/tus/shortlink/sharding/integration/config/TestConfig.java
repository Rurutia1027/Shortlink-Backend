package org.tus.shortlink.sharding.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.tus.shortlink.sharding.ShardKeyStrategy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan(basePackages = {
        "org.tus.shortlink.sharding",          // Aspect, ContextHolder
        "org.tus.shortlink.sharding.unit.svc"  // Test Service
})
public class TestConfig {
    @Bean
    public ShardKeyStrategy shardKeyStrategy() {
        return gid -> "ds_" + (Math.abs(gid.hashCode()) % 2);
    }
}