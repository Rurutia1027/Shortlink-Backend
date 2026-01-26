package org.tus.common.domain.redis.performance;

import org.tus.common.domain.redis.BloomFilterService;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Load test for BloomFilterService operations.
 * <p>
 * - Bloom contains/add: P95 ≤ 2 ms, P99 ≤ 5 ms</p>
 */
public class BloomFilterLoadTest extends PerformanceTestBase {

    private static final String FILTER_NAME = "perf:bloom";
    private BloomFilterService bloomFilterService;
    private final String[] testElements = new String[10000];

    @Override
    public void init(String redisAddress) {
        super.init(redisAddress);
        this.bloomFilterService = redisService.getBloomFilterService();
        // Initialize filter
        bloomFilterService.initFilter(FILTER_NAME, 100_000, 0.01);
        // Pre-populate test elements
        for (int i = 0; i < testElements.length; i++) {
            testElements[i] = "element-" + i;
        }
        // Pre-add some elements
        for (int i = 0; i < 1000; i++) {
            bloomFilterService.add(FILTER_NAME, testElements[i]);
        }
    }

    @Override
    protected void executeOperation() throws Exception {
        int operation = ThreadLocalRandom.current().nextInt(100);
        int elementIndex = ThreadLocalRandom.current().nextInt(testElements.length);
        String element = testElements[elementIndex];

        if (operation < 70) {
            // 70% contains operations
            bloomFilterService.contains(FILTER_NAME, element);
        } else {
            // 30% add operations
            bloomFilterService.add(FILTER_NAME, element);
        }
    }

    /**
     * Test Bloom contains operations.
     */
    public PerformanceTestResult testContains(int threadCount, Duration duration) throws InterruptedException {
        BloomContainsTest test = new BloomContainsTest(bloomFilterService, FILTER_NAME, testElements);
        test.init(redisAddress);
        return test.runLoadTest(threadCount, duration, 100);
    }

    /**
     * Test Bloom add operations.
     */
    public PerformanceTestResult testAdd(int threadCount, Duration duration) throws InterruptedException {
        BloomAddTest test = new BloomAddTest(bloomFilterService, FILTER_NAME, testElements);
        test.init(redisAddress);
        return test.runLoadTest(threadCount, duration, 100);
    }

    /**
     * Test mixed contains/add operations.
     */
    public PerformanceTestResult testMixed(int threadCount, Duration duration) throws InterruptedException {
        return runLoadTest(threadCount, duration, 100);
    }

    private class BloomContainsTest extends PerformanceTestBase {
        private final BloomFilterService bloomFilterServiceRef;
        private final String filterName;
        private final String[] testElementsRef;

        BloomContainsTest(BloomFilterService bloomFilterService, String filterName, String[] testElements) {
            this.bloomFilterServiceRef = bloomFilterService;
            this.filterName = filterName;
            this.testElementsRef = testElements;
        }

        @Override
        protected void executeOperation() throws Exception {
            int elementIndex = ThreadLocalRandom.current().nextInt(testElementsRef.length);
            bloomFilterServiceRef.contains(filterName, testElementsRef[elementIndex]);
        }
    }

    private class BloomAddTest extends PerformanceTestBase {
        private final BloomFilterService bloomFilterServiceRef;
        private final String filterName;
        private final String[] testElementsRef;

        BloomAddTest(BloomFilterService bloomFilterService, String filterName, String[] testElements) {
            this.bloomFilterServiceRef = bloomFilterService;
            this.filterName = filterName;
            this.testElementsRef = testElements;
        }

        @Override
        protected void executeOperation() throws Exception {
            int elementIndex = ThreadLocalRandom.current().nextInt(testElementsRef.length);
            bloomFilterServiceRef.add(filterName, testElementsRef[elementIndex]);
        }
    }
}
