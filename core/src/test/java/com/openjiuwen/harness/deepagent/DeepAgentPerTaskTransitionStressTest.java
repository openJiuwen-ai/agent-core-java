/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deepagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.IsolatedActions;
import com.openjiuwen.core.foundation.tool.NoopTool;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
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
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-Task transition stress: the V1
 * factory shape — creation serialized under one factory lock — combined
 * with unlocked execution and destroy crossing. Each worker round creates
 * and initializes an agent under the shared lock (the registration path
 * V1 held inside {@code creationLock}), then outside the lock resolves and
 * directly invokes the shared tool (no LLM), and finally destroys its
 * agent while other workers are still executing. Invariants: zero
 * exceptions, zero residue, zero loss (every live owner resolves), and the
 * shared entry survives while any owner is alive.
 *
 * @since 0.1.16
 */
@DisplayName("S2 per-Task transition stress (V1 factory-lock form)")
@Timeout(120)
class DeepAgentPerTaskTransitionStressTest {
    private static final int THREADS = 16;

    private static final int ROUNDS = 100;

    private static final String TOOL_ID = "s2-transition-noop-tool";

    private static final long THREAD_SETTLE_MILLIS = 5000L;

    @Test
    @DisplayName("factory-locked create + unlocked execute/destroy crossing: zero exception/residue/loss")
    void transitionStress_zeroExceptionZeroResidueZeroLoss() throws Exception {
        NoopTool sharedTool = new NoopTool(ToolCard.builder()
                .id(TOOL_ID)
                .name(TOOL_ID)
                .description("S2 shared noop tool")
                .build());
        DeepAgentConfig sharedConfig = DeepAgentConfig.builder()
                .workspacePath("./target/s2-transition-stress-repo")
                .tools(List.of(sharedTool))
                .build();
        AgentCard sharedCard = buildSharedCard();
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        int threadsBefore = countLifecycleThreads();

        StressResult result = runTransitionStress(sharedCard, sharedConfig);
        assertStressInvariants(result, before, threadsBefore);
    }

    private static AgentCard buildSharedCard() {
        return AgentCard.builder()
                .id("s2-transition-stress-agent")
                .name("s2-transition-stress-agent")
                .description("S2 per-Task transition stress agent")
                .build();
    }

    private static StressResult runTransitionStress(AgentCard sharedCard, DeepAgentConfig sharedConfig)
            throws Exception {
        ReentrantLock factoryLock = new ReentrantLock();
        CyclicBarrier allAliveBarrier = new CyclicBarrier(THREADS, () ->
                assertThat(Runner.resourceMgr().getTool(TOOL_ID))
                        .as("shared entry must be present while every worker holds a live agent")
                        .isNotNull());
        AtomicInteger executed = new AtomicInteger();
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = newPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int r = 0; r < ROUNDS; r++) {
                        final boolean isFirstRound = r == 0;
                        IsolatedActions.IsolatedOutcome<Boolean> outcome = IsolatedActions.callIsolated(() -> {
                            DeepAgent agent = createUnderFactoryLock(factoryLock, sharedCard, sharedConfig);
                            if (isFirstRound) {
                                awaitAllOwnersAlive(allAliveBarrier);
                            }
                            resolveAndInvokeSharedTool();
                            agent.destroy();
                            return Boolean.TRUE;
                        });
                        if (outcome.hasFailure()) {
                            failures.add(outcome.failure());
                            return null;
                        }
                        executed.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(90L, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return new StressResult(executed, failures);
    }

    private static void assertStressInvariants(StressResult result, GlobalRegistrySnapshot before,
            int threadsBefore) throws InterruptedException {
        assertThat(result.failures()).as("unexpected exceptions under transition stress").isEmpty();
        assertThat(result.executed().get()).as("resolved+invoked rounds").isEqualTo(THREADS * ROUNDS);

        GlobalRegistrySnapshot after = GlobalRegistrySnapshot.take();
        after.assertDeltaZero(before);
        assertThat(countLifecycleThreadsAfterSettle(threadsBefore))
                .as("lifecycle threads must fall back after all instances are destroyed")
                .isLessThanOrEqualTo(threadsBefore + 1);
    }

    private static DeepAgent createUnderFactoryLock(ReentrantLock factoryLock, AgentCard card,
            DeepAgentConfig config) {
        factoryLock.lock();
        try {
            DeepAgent agent = HarnessFactory.createDeepAgent(card, config, null);
            agent.ensureInitialized();
            return agent;
        } finally {
            factoryLock.unlock();
        }
    }

    private static void awaitAllOwnersAlive(CyclicBarrier barrier) {
        try {
            barrier.await(30L, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            // G.CON.10: no interrupt-flag restore; surface the abnormal
            // interrupt as the captured failure with the cause preserved.
            throw new IllegalStateException("barrier await interrupted", ex);
        } catch (BrokenBarrierException | TimeoutException ex) {
            throw new IllegalStateException("barrier broken while waiting for live owners", ex);
        }
    }

    private static void resolveAndInvokeSharedTool() throws Exception {
        Object resolved = Runner.resourceMgr().getTool(TOOL_ID);
        assertThat(resolved).as("shared tool must resolve while this worker owns it").isNotNull();
        if (!(resolved instanceof Tool tool)) {
            throw new IllegalStateException("shared entry is not a Tool: " + resolved);
        }
        Object output = tool.invoke(Map.of(), Map.of());
        assertThat(output).isEqualTo("ok");
    }

    private static ExecutorService newPool(int threads) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(threads * 2), runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("s2-stress-worker-" + thread.getId());
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((failedThread, error) ->
                        Loggers.COMMON.error("Uncaught exception in {}: {}",
                            failedThread.getName(), error.getMessage()));
                    return thread;
                });
    }

    private record StressResult(AtomicInteger executed, List<Throwable> failures) {
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
}
