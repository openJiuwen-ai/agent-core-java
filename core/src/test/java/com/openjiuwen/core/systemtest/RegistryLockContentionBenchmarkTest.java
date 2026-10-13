/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.NoopTool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry lock contention benchmark: quantifies the registry-lock cost of
 * the concurrency work on the two paths it touches, so the current
 * tree can be compared against the baseline commit with the same
 * asset.
 *
 * <ul>
 *   <li>Fullchain: 16 threads x 300 rounds of DeepAgent create,
 *       initialize, destroy — the sys-operation claim/release and tool
 *       release sequences that now run under the structure lock.</li>
 *   <li>Micro: 16 threads x 2000 rounds of addTool, getTool, removeTool
 *       on one shared local ResourceMgr — the pure registry critical
 *       section cost.</li>
 * </ul>
 *
 * <p>Assertions are sanity-only (every round succeeds, every resolution
 * hits); the latency and throughput numbers are printed with the
 * {@code [registry-bench]} prefix and compared against the baseline run manually.
 * Percentiles come from per-round nanosecond samples; the serial warmup
 * rounds before the timed region are excluded. Timeout is 600s for the
 * 4800 agent lifecycles plus 96000 timed registry operations.</p>
 *
 * @since 0.1.16
 */
@Tag("system-test")
@DisplayName("Registry lock contention benchmark")
@Timeout(600)
class RegistryLockContentionBenchmarkTest {
    private static final int FULLCHAIN_THREADS = 16;

    private static final int FULLCHAIN_ROUNDS = 300;

    private static final int MICRO_THREADS = 16;

    private static final int MICRO_ROUNDS = 2000;

    private static final String MICRO_TAG = "p1-micro-tag";

    @BeforeEach
    void resetTenantContext() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    @DisplayName("fullchain create-init-destroy under 16-thread contention")
    void fullchainLifecycleContention() throws Exception {
        for (int i = 0; i < 3; i++) {
            runFullchainRound();
        }
        long[][] latencies = new long[FULLCHAIN_THREADS][FULLCHAIN_ROUNDS];
        AtomicLong okCount = new AtomicLong();
        PoolTask task = threadIndex -> {
            for (int round = 0; round < FULLCHAIN_ROUNDS; round++) {
                long start = System.nanoTime();
                try {
                    runFullchainRound();
                    okCount.incrementAndGet();
                } finally {
                    latencies[threadIndex][round] = System.nanoTime() - start;
                }
            }
        };
        long wallStart = System.nanoTime();
        runOnPool(FULLCHAIN_THREADS, "p1-fullchain", task);
        long wallMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - wallStart);
        long failedRounds = (long) FULLCHAIN_THREADS * FULLCHAIN_ROUNDS - okCount.get();
        printMetric(new BenchMetric("fullchain", FULLCHAIN_THREADS, FULLCHAIN_ROUNDS, merge(latencies),
                okCount.get(), failedRounds, wallMillis));
        assertThat(okCount.get()).as("fullchain rounds succeeded").isEqualTo(FULLCHAIN_THREADS * FULLCHAIN_ROUNDS);
        assertThat(failedRounds).as("fullchain rounds failed").isZero();
    }

    @Test
    @DisplayName("micro addTool-getTool-removeTool on one shared ResourceMgr")
    void microRegistryOpsContention() throws Exception {
        ResourceMgr resourceMgr = new ResourceMgr();
        long[][] addNanos = new long[MICRO_THREADS][MICRO_ROUNDS];
        long[][] getNanos = new long[MICRO_THREADS][MICRO_ROUNDS];
        long[][] removeNanos = new long[MICRO_THREADS][MICRO_ROUNDS];
        AtomicLong missCount = new AtomicLong();
        PoolTask task = threadIndex -> {
            for (int round = 0; round < MICRO_ROUNDS; round++) {
                String toolId = "p1-micro-" + threadIndex + "-" + round;
                NoopTool tool = new NoopTool(ToolCard.builder()
                        .id(toolId)
                        .name(toolId)
                        .description("p1 micro tool")
                        .build());
                long addStart = System.nanoTime();
                resourceMgr.addTool(tool, MICRO_TAG);
                long getStart = System.nanoTime();
                Object resolved = resourceMgr.getTool(toolId);
                long removeStart = System.nanoTime();
                resourceMgr.removeTool(toolId, MICRO_TAG, TagMatchStrategy.ALL, true);
                long removeEnd = System.nanoTime();
                if (resolved == null) {
                    missCount.incrementAndGet();
                }
                addNanos[threadIndex][round] = getStart - addStart;
                getNanos[threadIndex][round] = removeStart - getStart;
                removeNanos[threadIndex][round] = removeEnd - removeStart;
            }
        };
        long wallStart = System.nanoTime();
        runOnPool(MICRO_THREADS, "p1-micro", task);
        long wallMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - wallStart);
        long operations = (long) MICRO_THREADS * MICRO_ROUNDS;
        printMetric(new BenchMetric("micro-addTool", MICRO_THREADS, MICRO_ROUNDS, merge(addNanos),
                operations, 0L, wallMillis));
        printMetric(new BenchMetric("micro-getTool", MICRO_THREADS, MICRO_ROUNDS, merge(getNanos),
                operations, 0L, wallMillis));
        printMetric(new BenchMetric("micro-removeTool", MICRO_THREADS, MICRO_ROUNDS, merge(removeNanos),
                operations, 0L, wallMillis));
        assertThat(missCount.get()).as("micro resolutions missed").isZero();
        assertThat(resourceMgr.getTool("p1-micro-0-0")).as("micro entries released")
                .isNull();
    }

    private static void runFullchainRound() {
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath("./target/p1-benchmark-repo")
                .build());
        try {
            agent.ensureInitialized();
        } finally {
            agent.destroy();
        }
    }

    /**
     * Body one pool thread executes for the whole benchmark run.
     */
    private interface PoolTask {
        /**
         * Runs the benchmark body for one thread.
         *
         * @param threadIndex the index of the executing thread
         * @throws Exception when the benchmark body fails
         */
        void run(int threadIndex) throws Exception;
    }

    private static void runOnPool(int threads, String poolName, PoolTask task) throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(threads * 2), runnable -> {
                    Thread thread = new Thread(runnable, poolName + "-worker");
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((failedThread, error) ->
                        Loggers.COMMON.error("Uncaught exception in {}: {}",
                            failedThread.getName(), error.getMessage()));
                    return thread;
                });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int threadIndex = 0; threadIndex < threads; threadIndex++) {
                int index = threadIndex;
                futures.add(pool.submit(() -> {
                    task.run(index);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static long[] merge(long[][] perThread) {
        int total = Arrays.stream(perThread).mapToInt(row -> row.length).sum();
        long[] merged = new long[total];
        int offset = 0;
        for (long[] row : perThread) {
            System.arraycopy(row, 0, merged, offset, row.length);
            offset += row.length;
        }
        return merged;
    }

    /**
     * One printed benchmark metric line, bundling the measured samples
     * with their run shape so the printer stays parameter-light.
     */
    private record BenchMetric(String label, int threads, int roundsPerThread, long[] nanos,
            long okCount, long failCount, long wallMillis) {
    }

    private static void printMetric(BenchMetric metric) {
        double throughput = throughputPerSecond(metric.okCount(), metric.wallMillis());
        System.out.printf(Locale.ROOT,
                "[registry-bench] %s threads=%d rounds=%d ok=%d fail=%d wall=%dms throughput=%.0f/s p50=%.3fms"
                        + " p99=%.3fms max=%.3fms%n",
                metric.label(), metric.threads(), metric.roundsPerThread(), metric.okCount(), metric.failCount(),
                metric.wallMillis(), throughput,
                nanosToMillis(percentileNanos(metric.nanos(), 0.50)),
                nanosToMillis(percentileNanos(metric.nanos(), 0.99)),
                nanosToMillis(percentileNanos(metric.nanos(), 1.00)));
    }

    private static double throughputPerSecond(long okCount, long wallMillis) {
        if (wallMillis <= 0L) {
            return (double) okCount;
        }
        return BigDecimal.valueOf(okCount)
                .multiply(BigDecimal.valueOf(1000L))
                .divide(BigDecimal.valueOf(wallMillis), MathContext.DECIMAL64)
                .doubleValue();
    }

    private static double nanosToMillis(long nanos) {
        return BigDecimal.valueOf(nanos)
                .divide(BigDecimal.valueOf(1_000_000L), MathContext.DECIMAL64)
                .doubleValue();
    }

    private static long percentileNanos(long[] samples, double quantile) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        int index = (int) Math.min(sorted.length - 1L, Math.round((sorted.length - 1) * quantile));
        return sorted[index];
    }
}
