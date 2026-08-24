/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests that each Pregel execution releases its node-task executor.
 */
@DisplayName("Pregel executor lifecycle")
class PregelExecutorLifecycleTest {
    private static final String NODE_NAME = "worker";
    private static final String WORKER_THREAD_PREFIX = "pregel-task-";
    private static final int REPEAT_COUNT = 10;
    private static final long TERMINATION_TIMEOUT_MILLIS = 2000L;

    @Test
    @Timeout(10)
    @DisplayName("successful graph runs release worker threads")
    void successfulRunsReleaseWorkerThreads() throws Exception {
        Set<Thread> baselineThreads = currentPregelThreads();
        AtomicInteger invocationCount = new AtomicInteger();
        Pregel graph = newSingleNodeGraph(invocationCount::incrementAndGet);

        for (int index = 0; index < REPEAT_COUNT; index++) {
            PregelConfig config = new PregelConfig("lifecycle-success-" + index, "lifecycle-success", 10);
            graph.run(config);
        }

        assertEquals(REPEAT_COUNT, invocationCount.get());
        assertTrue(awaitPregelThreadBaseline(baselineThreads),
                "Successful Pregel runs must release their worker threads");
    }

    @Test
    @Timeout(10)
    @DisplayName("failed graph run releases worker threads")
    void failedRunReleasesWorkerThreads() throws InterruptedException {
        Set<Thread> baselineThreads = currentPregelThreads();
        Pregel graph = newSingleNodeGraph(() -> {
            throw new IllegalStateException("expected lifecycle test failure");
        });
        PregelConfig config = new PregelConfig("lifecycle-failure", "lifecycle-failure", 10);

        assertThrows(IllegalStateException.class, () -> graph.run(config));

        assertTrue(awaitPregelThreadBaseline(baselineThreads),
                "Failed Pregel run must release its worker threads");
    }

    private static Pregel newSingleNodeGraph(Runnable nodeTask) {
        PregelNode node = new PregelNode(NODE_NAME, nodeTask, List.of());
        Map<String, PregelNode> nodes = Map.of(NODE_NAME, node);
        List<Channel> channels = List.of(new TriggerChannel(NODE_NAME));
        return new Pregel(nodes, channels, NODE_NAME, null, null);
    }

    private static boolean awaitPregelThreadBaseline(Set<Thread> baselineThreads) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TERMINATION_TIMEOUT_MILLIS);
        while (hasAdditionalPregelThreads(baselineThreads)) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            TimeUnit.MILLISECONDS.sleep(10L);
        }
        return true;
    }

    private static boolean hasAdditionalPregelThreads(Set<Thread> baselineThreads) {
        for (Thread thread : currentPregelThreads()) {
            if (!baselineThreads.contains(thread)) {
                return true;
            }
        }
        return false;
    }

    private static Set<Thread> currentPregelThreads() {
        Set<Thread> threads = new HashSet<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().startsWith(WORKER_THREAD_PREFIX)) {
                threads.add(thread);
            }
        }
        return threads;
    }
}
