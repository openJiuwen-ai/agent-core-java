/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gate tests for dimension I-B: bounded module executors must cap platform thread growth under burst load.
 */
@Tag("gate")
@DisplayName("Bounded module executor gate")
class BoundedModuleExecutorGateTest {
    private static final String GATE_POOL_MAX_PROPERTY = "openjiuwen.executor.gate-burst-test.max-size";
    private static final String GATE_POOL_QUEUE_PROPERTY = "openjiuwen.executor.gate-burst-test.queue-size";
    private static final String PREGEL_MAX_PROPERTY = "openjiuwen.executor.pregel-task.max-size";

    private final List<ExecutorService> executorsToClose = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (ExecutorService executor : executorsToClose) {
            executor.shutdownNow();
        }
        executorsToClose.clear();
        System.clearProperty(GATE_POOL_MAX_PROPERTY);
        System.clearProperty(GATE_POOL_QUEUE_PROPERTY);
        System.clearProperty(PREGEL_MAX_PROPERTY);
    }

    @Test
    @Timeout(20)
    @DisplayName("有界模块池在 burst 提交下不超过配置 max-size")
    void boundedModulePoolCapsThreadCountUnderBurst() throws Exception {
        System.setProperty(GATE_POOL_MAX_PROPERTY, "4");
        System.setProperty(GATE_POOL_QUEUE_PROPERTY, "256");
        ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool("gate-burst-test", false);
        executorsToClose.add(executor);
        assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
        ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;

        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(64);
        AtomicLong observedPoolSize = new AtomicLong();

        for (int i = 0; i < 64; i++) {
            executor.submit(() -> {
                try {
                    observedPoolSize.updateAndGet(prev -> Math.max(prev, pool.getPoolSize()));
                    awaitQuietly(release);
                } finally {
                    finished.countDown();
                }
            });
        }

        // Wait until the pool is saturated, then sample pool size without getAllStackTraces().
        long deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (pool.getActiveCount() < 4 && System.nanoTime() < deadlineNs) {
            Thread.sleep(10L);
        }
        for (int i = 0; i < 20; i++) {
            observedPoolSize.updateAndGet(prev -> Math.max(prev, pool.getPoolSize()));
            Thread.sleep(25L);
        }

        assertThat(pool.getMaximumPoolSize()).isEqualTo(4);
        assertThat(pool.getLargestPoolSize())
                .as("bounded pool must not grow past configured max-size")
                .isLessThanOrEqualTo(4);
        assertThat(observedPoolSize.get())
                .as("bounded pool must not spawn unbounded platform threads")
                .isLessThanOrEqualTo(4);
        assertThat(liveThreadsWithPrefix("gate-burst-test")).isLessThanOrEqualTo(4);

        release.countDown();
        assertThat(finished.await(10, TimeUnit.SECONDS))
                .as("all burst tasks must complete after release")
                .isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @Timeout(10)
    @DisplayName("pregel-task 模块池默认上限为 32（I-B 整改）")
    void pregelTaskModulePoolHasBoundedDefaultMaxSize() throws Exception {
        ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool("pregel-task", false);
        executorsToClose.add(executor);
        assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
        assertThat(((ThreadPoolExecutor) executor).getMaximumPoolSize()).isEqualTo(32);
    }

    @Test
    @Timeout(10)
    @DisplayName("pregel-task max-size 可通过系统属性覆盖")
    void pregelTaskModulePoolMaxSizeIsConfigurable() throws Exception {
        System.setProperty(PREGEL_MAX_PROPERTY, "6");
        ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool("pregel-task", false);
        executorsToClose.add(executor);
        assertThat(((ThreadPoolExecutor) executor).getMaximumPoolSize()).isEqualTo(6);
    }

    @Test
    @Timeout(20)
    @DisplayName("burst 负载下堆使用不应因无界线程膨胀而失控")
    void burstLoadDoesNotAllocateUnboundedThreadStacks() throws Exception {
        System.setProperty(GATE_POOL_MAX_PROPERTY, "4");
        System.setProperty(GATE_POOL_QUEUE_PROPERTY, "256");
        ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool("gate-burst-test", false);
        executorsToClose.add(executor);

        assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
        ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
        long heapBefore = usedHeapBytes();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(64);
        for (int i = 0; i < 64; i++) {
            executor.submit(() -> {
                try {
                    byte[] scratch = new byte[16 * 1024];
                    scratch[0] = 1;
                    awaitQuietly(release);
                } finally {
                    finished.countDown();
                }
            });
        }
        long deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (pool.getActiveCount() < 4 && System.nanoTime() < deadlineNs) {
            Thread.sleep(10L);
        }
        Thread.sleep(200L);
        long heapDuring = usedHeapBytes();
        assertThat(pool.getLargestPoolSize()).isLessThanOrEqualTo(4);
        assertThat(liveThreadsWithPrefix("gate-burst-test")).isLessThanOrEqualTo(4);

        release.countDown();
        assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        long heapDeltaMb = (heapDuring - heapBefore) / (1024 * 1024);
        assertThat(heapDeltaMb)
                .as("heap growth during bounded burst should stay modest (proxy for OOM risk)")
                .isLessThan(128);
    }

    private static long liveThreadsWithPrefix(String prefix) {
        ThreadMXBean threadMx = ManagementFactory.getThreadMXBean();
        long count = 0;
        for (long id : threadMx.getAllThreadIds()) {
            Thread thread = findThread(id);
            if (thread != null && thread.isAlive() && thread.getName().startsWith(prefix + "-")) {
                count++;
            }
        }
        return count;
    }

    private static Thread findThread(long id) {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getId() == id) {
                return thread;
            }
        }
        return null;
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
