/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link DeepAgent#startTaskLoopRuntime} rejects concurrent
 * task loops sharing the same session ID, preventing the race condition where
 * one request's cleanup destroys another's runtime state.
 */
class DeepAgentConcurrentSessionGuardTest {

    @TempDir
    Path baseDir;

    @BeforeEach
    void resetContext() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @AfterEach
    void cleanupContext() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    private DeepAgent newTaskLoopAgent() {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTaskLoop(true)
                .maxIterations(1)
                .completionTimeout(2.0)
                .workspacePath(baseDir.toString())
                .build();
        AgentCard card = AgentCard.builder()
                .name("guard_test_agent")
                .description("test")
                .build();
        Workspace workspace = Workspace.builder()
                .rootPath(baseDir.toString())
                .language("cn")
                .build();
        return new DeepAgent(card, config, workspace);
    }

    @Test
    @DisplayName("concurrent invoke with same sessionId: task loops never overlap, duplicates rejected")
    @Timeout(30)
    void testConcurrentSameSessionId_rejectsDuplicate() throws Exception {
        DeepAgent agent = newTaskLoopAgent();
        String sessionId = "concurrent-guard-session";

        // Warm up with a throwaway session so ensureTaskLoopRuntime() has
        // already run before concurrency begins.
        try {
            agent.invoke(Map.of("query", "warmup", "conversation_id", "warmup-session"));
        } catch (Exception ex) {
            // Expected: no LLM configured, but runtime is now initialized.
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        // Per-thread wall-clock window inside invoke(): the real invariant under
        // test is that no two task loops for the same session overlap in time.
        // Fast serial hand-offs are legal; overlapping windows are not. A window
        // may overhang the guarded region by the post-release result-building
        // time, but a genuine guard breach overlaps by milliseconds, not the
        // microseconds of that tail.
        List<long[]> invocationWindows = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    long startNanos = System.nanoTime();
                    try {
                        agent.invoke(Map.of(
                                "query", "test",
                                "conversation_id", sessionId));
                    } catch (Throwable ex) {
                        errors.add(ex);
                    } finally {
                        invocationWindows.add(new long[] {startNanos, System.nanoTime()});
                    }
                }));
            }

            // Release all threads simultaneously
            startLatch.countDown();
            for (Future<?> f : futures) {
                f.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // Threads rejected by the guard
        long rejected = errors.stream()
                .filter(ex -> ex instanceof IllegalStateException)
                .filter(ex -> ex.getMessage() != null
                        && ex.getMessage().contains("Task loop already active"))
                .count();

        // Overlapping invocation windows: two threads inside invoke() for the
        // same session at the same instant means the guard failed to serialize
        // task loops. Windows measured around invoke() are a superset of the
        // guarded region, so zero overlap here implies zero overlap inside it.
        List<long[]> windows = new ArrayList<>(invocationWindows);
        windows.sort((a, b) -> Long.compare(a[0], b[0]));
        long overlaps = 0;
        for (int i = 1; i < windows.size(); i++) {
            if (windows.get(i)[0] < windows.get(i - 1)[1]) {
                overlaps++;
            }
        }

        assertThat(rejected)
                .as("at least one thread should be rejected by the session guard")
                .isGreaterThanOrEqualTo(1);
        assertThat(overlaps)
                .as("no two invocations for the same session may overlap in time")
                .isZero();
    }

    @Test
    @DisplayName("sequential invoke with same sessionId: second request not rejected after first completes")
    @Timeout(15)
    void testSequentialSameSessionId_allowsReuse() {
        DeepAgent agent = newTaskLoopAgent();
        String sessionId = "sequential-reuse-session";

        // First request — expected to fail (no LLM) but must clean up properly.
        try {
            agent.invoke(Map.of("query", "first", "conversation_id", sessionId));
        } catch (Exception ex) {
            // Expected
        }

        // Second request with the same sessionId must NOT be rejected by the guard.
        try {
            agent.invoke(Map.of("query", "second", "conversation_id", sessionId));
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Task loop already active")) {
                throw new AssertionError("Session was not released after the first request completed", ex);
            }
            // Other IllegalStateException (e.g. missing LLM) is acceptable.
        } catch (Exception ex) {
            // Other exceptions are acceptable — the point is the guard did not block reuse.
        }
    }
}
