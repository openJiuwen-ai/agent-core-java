/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deepagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.IsolatedActions;
import com.openjiuwen.core.foundation.tool.NoopTool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.resourcemanager.GlobalRegistrySnapshot;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Full-chain lifecycle stress: the cctest
 * experiment promoted to an in-repo regression. Sixteen threads run 300
 * create-init-destroy rounds each against one shared card/config/tool, the
 * exact shared-input shape production per-Task agents use. The concurrency
 * hardening must keep the three zero invariants: zero
 * exceptions (no CME or half-state surface), zero residue (global registry
 * fingerprint returns to the pre-test state), zero loss (every round
 * completes the full registration cycle), plus no lifecycle thread
 * accumulation.
 *
 * @since 0.1.16
 */
@DisplayName("S1 DeepAgent concurrent lifecycle stress (cctest promoted)")
@Timeout(300)
class DeepAgentConcurrentLifecycleStressTest {
    private static final int THREADS = 16;

    private static final int ROUNDS = 300;

    private static final long THREAD_SETTLE_MILLIS = 5000L;

    @Test
    @DisplayName("16x300 concurrent create-init-destroy: ok==4800, zero residue, threads fall back")
    void concurrentLifecycle_zeroExceptionZeroResidueZeroLoss() throws Exception {
        NoopTool sharedTool = new NoopTool(ToolCard.builder()
                .id("s1-stress-noop-tool")
                .name("s1-stress-noop-tool")
                .description("S1 shared noop tool")
                .build());
        DeepAgentConfig sharedConfig = DeepAgentConfig.builder()
                .workspacePath("./target/s1-lifecycle-stress-repo")
                .tools(List.of(sharedTool))
                .build();
        AgentCard sharedCard = AgentCard.builder()
                .id("s1-lifecycle-stress-agent")
                .name("s1-lifecycle-stress-agent")
                .description("S1 shared-input lifecycle stress agent")
                .build();
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        int threadsBefore = countLifecycleThreads();

        runSerialSanity(sharedCard, sharedConfig, 3);
        StressResult result = runConcurrentRounds(sharedCard, sharedConfig);
        assertStressInvariants(result, before, threadsBefore);
    }

    private static void assertStressInvariants(StressResult result, GlobalRegistrySnapshot before, int threadsBefore)
            throws InterruptedException {
        assertThat(result.ok().get()).as("successful create-init-destroy rounds").isEqualTo(THREADS * ROUNDS);
        assertThat(result.failures()).as("unexpected exceptions under stress").isEmpty();

        GlobalRegistrySnapshot after = GlobalRegistrySnapshot.take();
        after.assertDeltaZero(before);
        assertThat(countLifecycleThreadsAfterSettle(threadsBefore))
                .as("lifecycle threads must fall back after all instances are destroyed")
                .isLessThanOrEqualTo(threadsBefore + 1);
    }

    private static void runSerialSanity(AgentCard card, DeepAgentConfig config, int rounds) {
        for (int i = 0; i < rounds; i++) {
            DeepAgent agent = HarnessFactory.createDeepAgent(card, config, null);
            agent.ensureInitialized();
            agent.destroy();
        }
    }

    private static StressResult runConcurrentRounds(AgentCard card, DeepAgentConfig config) throws Exception {
        AtomicInteger ok = new AtomicInteger();
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = newPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int r = 0; r < ROUNDS; r++) {
                        IsolatedActions.IsolatedOutcome<Boolean> outcome = IsolatedActions.callIsolated(() -> {
                            DeepAgent agent = HarnessFactory.createDeepAgent(card, config, null);
                            agent.ensureInitialized();
                            agent.destroy();
                            return Boolean.TRUE;
                        });
                        if (outcome.hasFailure()) {
                            failures.add(outcome.failure());
                            return null;
                        }
                        ok.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(240L, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return new StressResult(ok, failures);
    }

    private static ExecutorService newPool(int threads) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(threads * 2), runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("s1-stress-worker-" + thread.getId());
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((failedThread, error) ->
                        Loggers.COMMON.error("Uncaught exception in {}: {}",
                            failedThread.getName(), error.getMessage()));
                    return thread;
                });
    }

    private static int countLifecycleThreads() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        int count = 0;
        for (ThreadInfo info : threads.dumpAllThreads(false, false)) {
            if (info != null && info.getThreadName() != null
                    && (info.getThreadName().contains("task-scheduler")
                    || info.getThreadName().contains("deep-agent"))) {
                count++;
            }
        }
        return count;
    }

    private static int countLifecycleThreadsAfterSettle(int before) throws InterruptedException {
        long deadline = System.currentTimeMillis() + THREAD_SETTLE_MILLIS;
        int after = countLifecycleThreads();
        while (after > before + 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(200L);
            after = countLifecycleThreads();
        }
        return after;
    }

    private record StressResult(AtomicInteger ok, List<Throwable> failures) {
    }
}
