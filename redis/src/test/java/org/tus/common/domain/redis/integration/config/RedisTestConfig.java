package org.tus.common.domain.redis.integration.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.tus.common.domain.redis.RedisService;
import org.tus.common.domain.redis.impl.RedisServiceImpl;

/**
 * Test configuration for Redis integration tests using Testcontainers.
 *
 * <p>Per redis-common-module-painpoint-solution-testplan $3.2: uses TestContainers Redis
 * for repeatable CI runs with real RedissonClient connected to the container. </p>
 */
@Configuration
public class RedisTestConfig {
    @Bean(initMethod = "start", destroyMethod = "stop")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort());
    }

    @Bean
    public RedissonClient redissonClient(GenericContainer<?> redisContainer) {
        Config config = new Config();
        String address =
                "redis://" + redisContainer().getHost() + ":" + redisContainer.getMappedPort(6379);
        config.useSingleServer()
                .setAddress(address)
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(5);
        return Redisson.create(config);
    }

    @Bean
    public RedisService redisService(RedissonClient redissonClient) {
        return new RedisServiceImpl(redissonClient);
    }
}
