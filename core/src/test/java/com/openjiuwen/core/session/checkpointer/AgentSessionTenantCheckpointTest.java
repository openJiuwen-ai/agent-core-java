/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.session.AgentSessionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression tests for the tenant-aware checkpoint key used by
 * {@link AgentSessionApi#preRun(Object)} / {@link AgentSessionApi#postRun()}.
 *
 * <p><b>Original counterexample</b>: {@link TenantCheckpointerIsolationTest} only
 * covers same-thread key derivation — it never runs {@code preRun} and
 * {@code postRun} on different threads. That is exactly what streaming execution
 * does: {@code preRun} runs on the caller thread (tenant bound) while
 * {@code postRun} runs on a pooled worker ({@code deep-agent-stream-N} /
 * {@code react-agent-stream-N}) that neither owns the request tenant nor is
 * guaranteed to see it. The store is then created under {@code "tenant:sid"} but
 * looked up under {@code "sid"} (or a stale inherited {@code "stale:sid"}), so
 * {@code postAgentExecute} throws {@code CHECKPOINTER_POST_AGENT_EXECUTION_ERROR:
 * agent store not found} and the checkpoint is lost.</p>
 *
 * <p><b>Legitimate controls</b>: {@code DeepAgentTenantTest#testInvoke_withSessionTenantContext}
 * exercises the synchronous {@code invoke} path, where set/invoke/clear happen on
 * one thread, so it must keep passing unchanged.</p>
 *
 * <p><b>Eviction</b>: every test here builds an {@link InMemoryCheckpointer} with
 * {@code (ttl=0, maxSessions=0)}, disabling both TTL and capacity eviction. A
 * failure therefore cannot be attributed to {@code maximumSize}/TTL eviction; it
 * isolates the tenant-key mismatch.</p>
 *
 * <p>The worker threads below are created explicitly per test (via a named,
 * daemon single-thread executor) so the inherited-thread-local state is fully
 * controlled and the test is deterministic rather than probabilistic.</p>
 *
 * @since 0.1.15
 */
@Timeout(30)
class AgentSessionTenantCheckpointTest {

    /** Eviction disabled: ttl=0 and maxSessions=0. */
    private InMemoryCheckpointer checkpointer;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        checkpointer = new InMemoryCheckpointer(0L, 0, System::currentTimeMillis);
        CheckpointerFactory.setDefaultCheckpointer(checkpointer);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
        CheckpointerFactory.setDefaultCheckpointer(null);
    }

    @Test
    @DisplayName("same thread + tenant: preRun and postRun resolve the same tenant-aware key")
    void testSameThreadWithTenant_preAndPostSucceed() {
        TenantContext ctx = TenantContext.builder().tenantId("tenant_a").build();
        AgentSessionApi session = new AgentSessionApi("ctrl-same-thread-tenant").withTenantContext(ctx);

        TenantContextHolder.setCurrentTenant(ctx);
        assertThatCode(() -> {
            session.preRun(Map.of("query", "hello"));
            session.postRun();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("same thread + no tenant: preRun and postRun resolve the same bare key")
    void testSameThreadWithoutTenant_preAndPostSucceed() {
        AgentSessionApi session = new AgentSessionApi("ctrl-same-thread-no-tenant");

        TenantContextHolder.clearCurrentTenant();
        assertThatCode(() -> {
            session.preRun(Map.of("query", "hello"));
            session.postRun();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("session tenant + postRun on a tenant-less worker: checkpoint still saved")
    void testExplicitSessionTenant_workerWithoutTenant_stillSaves() throws Exception {
        // Mirrors DeepAgent.streamTaskLoop / buildEffectiveStreamSession: the effective
        // session carries the tenant context, preRun runs on the caller thread, postRun
        // runs on a pooled worker that has no tenant bound.
        TenantContext ctx = TenantContext.builder().tenantId("tenant_a").build();
        AgentSessionApi session = new AgentSessionApi("red-session-tenant").withTenantContext(ctx);

        TenantContextHolder.setCurrentTenant(ctx);
        session.preRun(Map.of("query", "hello"));
        TenantContextHolder.clearCurrentTenant();

        // The worker is created now, so it inherits a null tenant.
        assertThatCode(() -> runPostRunOnFreshWorker(session))
                .as("postRun must not fail with 'agent store not found' on a tenant-less worker")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("tenant bound only on the caller thread: carried to the worker for postRun")
    void testTenantBoundOnlyOnCallerThread_isCarriedToWorker() throws Exception {
        // Mirrors ReActAgent.stream: the session itself may carry no tenant, the tenant
        // is only bound to the caller thread before preRun.
        AgentSessionApi session = new AgentSessionApi("red-threadlocal-only");

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_b").build());
        session.preRun(Map.of("query", "hello"));
        TenantContextHolder.clearCurrentTenant();

        assertThatCode(() -> runPostRunOnFreshWorker(session))
                .as("the tenant bound at preRun time must be reused by postRun")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("worker inheriting a stale tenant: preRun had none, postRun must still match the bare key")
    void testWorkerWithStaleInheritedTenant_doesNotCorruptCheckpoint() throws Exception {
        // Mirrors the tenant-less batch from the bug report: a pooled worker that was
        // created while an unrelated tenant was bound keeps that stale tenant forever
        // (InheritableThreadLocal is only captured at thread creation and never refreshed).
        AgentSessionApi session = new AgentSessionApi("red-no-tenant-stale-worker");

        TenantContextHolder.clearCurrentTenant();
        session.preRun(Map.of("query", "hello"));

        ExecutorService worker = newSingleWorker("bugfix-stale-worker");
        try {
            // Create the worker thread while an unrelated tenant is bound so it inherits it.
            TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("stale_tenant").build());
            try {
                worker.submit(() -> { }).get(10, TimeUnit.SECONDS);
            } finally {
                TenantContextHolder.clearCurrentTenant();
            }

            assertThatCode(() -> worker.submit(session::postRun).get(10, TimeUnit.SECONDS))
                    .as("postRun must ignore the worker's stale inherited tenant")
                    .doesNotThrowAnyException();
        } finally {
            worker.shutdownNow();
        }
    }

    private void runPostRunOnFreshWorker(AgentSessionApi session) throws Exception {
        ExecutorService worker = newSingleWorker("bugfix-fresh-worker");
        try {
            worker.submit(session::postRun).get(10, TimeUnit.SECONDS);
        } finally {
            worker.shutdownNow();
        }
    }

    private static ExecutorService newSingleWorker(String name) {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, error) -> {
                // Surface worker failures instead of swallowing them; assertions observe them.
            });
            return thread;
        };
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1), threadFactory, new ThreadPoolExecutor.AbortPolicy());
    }
}
