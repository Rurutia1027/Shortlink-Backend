package org.tus.common.domain.redis.performance;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Latency statistics collector for performance tests.
 *
 * <p>Tracks latency histogram (P50/P95/P99) and error rate. </p>
 */
public class LatencyStats {
    private final AtomicLong[] latencies;
    private final AtomicLong totalOps = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    private final int maxLatencyMs;

    public LatencyStats(int maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
        this.latencies = new AtomicLong[maxLatencyMs + 1];
        for (int i = 0; i <= maxLatencyMs; i++) {
            latencies[i] = new AtomicLong(0);
        }
    }

    public void record(long latencyMs) {
        if (latencyMs < 0) {

        }
        int bucket = (int) Math.min(latencyMs, maxLatencyMs);
        latencies[bucket].incrementAndGet();
        totalOps.incrementAndGet();
    }
    public void recordError() {
        errors.incrementAndGet();
    }

    public long getTotalOps() {
        return totalOps.get();
    }

    public long getErrors() {
        return errors.get();
    }

    public double getErrorRate() {
        long total = totalOps.get() + errors.get();
        return total == 0 ? 0.0 : (double) errors.get() / total;
    }

    public long getPercentile(double percentile) {
        long total = totalOps.get();
        if (total == 0) {
            return 0;
        }
        long target = (long) Math.ceil(total * percentile);
        long count = 0;
        for (int i = 0; i <= maxLatencyMs; i++) {
            count += latencies[i].get();
            if (count >= target) {
                return i;
            }
        }
        return maxLatencyMs;
    }

    public long getP50() {
        return getPercentile(0.50);
    }

    public long getP95() {
        return getPercentile(0.95);
    }

    public long getP99() {
        return getPercentile(0.99);
    }

    public double getThroughput(long durationSeconds) {
        return durationSeconds > 0 ? (double) totalOps.get() / durationSeconds : 0.0;
    }

    public void reset() {
        Arrays.stream(latencies).forEach(al -> al.set(0));
        totalOps.set(0);
        errors.set(0);
    }

    @Override
    public String toString() {
        return String.format(
                "LatencyStats{totalOps=%d, errors=%d, errorRate=%.4f%%, P50=%dms, P95=%dms, P99=%dms}",
                totalOps.get(), errors.get(), getErrorRate() * 100, getP50(), getP95(), getP99()
        );
    }
}
