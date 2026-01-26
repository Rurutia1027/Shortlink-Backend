package org.tus.common.domain.redis.performance;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.tus.common.domain.redis.CacheService;
import org.tus.common.domain.redis.impl.RedisServiceImpl;
import org.tus.common.domain.redis.integration.CacheServiceIT;

import java.util.concurrent.TimeUnit;

/**
 * JMH micro-benchmark for CacheService operations.
 *
 * <p>Measures single-operation latency distribution for GET/SET operations.</p>
 *
 * <p>Run with: java -jar target/benchmarks.jar CacheServiceBenchmark</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class CacheServiceBenchmark {
    private CacheService cacheService;
    private RedissonClient redissonClient;
    private String key;
    private CacheServiceIT.SimplePojo value;

    @Setup
    public void setup() {
        // Connect to Redis (assumes Redis is running on localhost:6379)
        // For CI, this would use Testcontainers
        String redisAddress = System.getProperty("redis.address", "redis://localhost:6379");
        Config config = new Config();
        config.useSingleServer()
                .setAddress(redisAddress)
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(5);

        redissonClient = Redisson.create(config);
        cacheService = new RedisServiceImpl(redissonClient).getCacheService();

        // Pre-populate test data
        key = "benchmark:key";
        value = new CacheServiceIT.SimplePojo("id1", "name1");
        cacheService.set(key, value);
    }

    @TearDown
    public void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Benchmark
    public void benchmarkGet() {
        cacheService.get(key, CacheServiceIT.SimplePojo.class);
    }

    @Benchmark
    public void benchmarkSet() {
        cacheService.set(key, value);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(CacheServiceBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
