/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deepagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.IsolatedActions;
import com.openjiuwen.core.foundation.tool.NoopTool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.HangingListToolsClient;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.resourcemanager.GlobalRegistrySnapshot;
import com.openjiuwen.core.runner.resourcemanager.ToolMgr;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.BlockingInitRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Red-phase lifecycle state machine tests. Every case compiles against the baseline DeepAgent and is
 * expected to fail there; the state machine implementation turns them
 * green. Exactly-once registration is measured through the global
 * callback framework ({@code agentId_BEFORE_INVOKE}), not rail init
 * counters: baseline DeepAgent invokes DeepAgentRail.init twice per
 * initialization round, which is tolerated baseline behavior.
 */
@DisplayName("DeepAgent lifecycle state machine")
@Timeout(60)
class DeepAgentLifecycleStateMachineTest {
    private static final long WAIT_PROBE_MILLIS = 2000L;

    private static final int CONCURRENT_FIRST_CALLS = 8;

    /**
     * Plain AgentRail with an init counter and exactly one BEFORE_INVOKE
     * callback, so both rail execution count and framework registration
     * count are observable. SecurityRail (added by HarnessFactory) only
     * registers BEFORE_TOOL_CALL, so it never interferes with the
     * BEFORE_INVOKE count.
     */
    static final class InitCountingRail extends AgentRail {
        private final AtomicInteger initEntries = new AtomicInteger();

        @Override
        public void init(Object agent) {
            initEntries.incrementAndGet();
        }

        @Override
        public Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> getCallbacks() {
            return Map.of(AgentCallbackEvent.BEFORE_INVOKE, ctx -> {
            });
        }

        int initEntries() {
            return initEntries.get();
        }
    }

    /**
     * the transition matrix of the lifecycle state machine.
     * Baseline red channels: after destroy the agent
     * silently accepts re-initialization (INITIALIZED-&gt;destroy-&gt;init
     * must reject; NEW-&gt;destroy-&gt;init must reject).
     */
    @Test
    void ensureInitialized_transitionMatrix_matchesDeclaredTransitions() {
        InitCountingRail rail = new InitCountingRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(card("sm-ut01-agent"),
                config(List.of(rail), List.of(noopTool("sm-ut01-tool"))), null);
        try {
            // NEW -> INITIALIZING -> INITIALIZED: one full registration round.
            assertThat(agent.isInitialized()).isFalse();
            agent.ensureInitialized();
            assertThat(agent.isInitialized()).isTrue();
            assertThat(rail.initEntries()).isEqualTo(1);
            assertThat(callbackCount("sm-ut01-agent")).isEqualTo(1);

            // INITIALIZED -> init: direct return, zero new side effects.
            agent.ensureInitialized();
            assertThat(rail.initEntries()).isEqualTo(1);
            assertThat(callbackCount("sm-ut01-agent")).isEqualTo(1);

            // INITIALIZED -> destroy: terminal state reached.
            agent.destroy();

            // DESTROYED -> init: rejected (lazy-revival block).
            assertThatThrownBy(agent::ensureInitialized).isInstanceOf(IllegalStateException.class);

            // DESTROYED -> destroy: silent idempotent return.
            agent.destroy();
        } finally {
            agent.destroy();
        }

        // NEW -> destroy without prior init (V2 failure-fallback path).
        DeepAgent freshAgent = HarnessFactory.createDeepAgent(card("sm-ut01-fresh"),
                config(List.of(), List.of()), null);
        try {
            freshAgent.destroy();
            assertThatThrownBy(freshAgent::ensureInitialized).isInstanceOf(IllegalStateException.class);
        } finally {
            freshAgent.destroy();
        }
    }

    /**
     * concurrent first calls must run the registration sequence
     * exactly once. Baseline red channel: eight threads all execute the
     * unsynchronized sequence, so rail executions and framework
     * registrations reach eight.
     *
     * @throws Exception if any concurrent first call fails or exceeds its wait bound
     */
    @Test
    void ensureInitialized_concurrentFirstCalls_registerExactlyOnce() throws Exception {
        InitCountingRail rail = new InitCountingRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(card("sm-ut02-agent"),
                config(List.of(rail), List.of(noopTool("sm-ut02-tool"))), null);
        ExecutorService pool = newPool(CONCURRENT_FIRST_CALLS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_FIRST_CALLS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    agent.ensureInitialized();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10L, TimeUnit.SECONDS);
            }
            assertThat(agent.isInitialized()).isTrue();
            assertThat(rail.initEntries()).as("registration sequence executions").isEqualTo(1);
            assertThat(callbackCount("sm-ut02-agent")).as("global callback registrations").isEqualTo(1);
        } finally {
            pool.shutdownNow();
            agent.destroy();
        }
    }

    /**
     * a failed first initialization rolls the state back to NEW
     * and a concurrent second caller retries instead of racing. Baseline
     * red channels: the second caller runs the sequence concurrently
     * (enters the rail init and completes while the first call is still
     * blocked).
     *
     * @throws Exception if any lifecycle call fails or exceeds its wait bound
     */
    @Test
    void ensureInitialized_failedFirstCall_rollsBackAndRetrySucceeds() throws Exception {
        CountDownLatch releaseGate = new CountDownLatch(1);
        BlockingInitRail rail = new BlockingInitRail(releaseGate, true);
        // Direct construction: no HarnessFactory pre-registration, so the
        // snapshot window below observes exactly what init added and
        // destroy removed.
        DeepAgent agent = new DeepAgent(card("sm-ut03-agent"),
                config(List.of(rail), List.of(noopTool("sm-ut03-tool"))), null);
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        ExecutorService pool = newPool(2);
        try {
            Future<?> first = pool.submit(agent::ensureInitialized);
            assertThat(rail.awaitInitEntered(5L, TimeUnit.SECONDS)).isTrue();

            Future<?> retry = pool.submit(agent::ensureInitialized);
            // The second caller must wait for the in-flight first call:
            // no concurrent rail entry, no completed registration round.
            long deadline = System.currentTimeMillis() + WAIT_PROBE_MILLIS;
            while (!retry.isDone() && rail.initCount() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20L);
            }
            assertThat(rail.initCount()).as("rail init entries while first call in flight").isEqualTo(1);
            assertThat(retry.isDone()).as("second caller must not finish while first call in flight").isFalse();

            releaseGate.countDown();

            // First call propagates the original registration failure.
            assertThatThrownBy(first::get).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            // Rollback to NEW lets the waiting caller retry and succeed.
            retry.get(10L, TimeUnit.SECONDS);
            assertThat(agent.isInitialized()).isTrue();
            assertThat(callbackCount("sm-ut03-agent")).as("registrations after retry").isEqualTo(1);

            agent.destroy();
            GlobalRegistrySnapshot after = GlobalRegistrySnapshot.take();
            after.assertDeltaZero(before);
        } finally {
            releaseGate.countDown();
            pool.shutdownNow();
            agent.destroy();
        }
    }

    /**
     * Success round: destroy during an in-flight initialization
     * occupies DESTROYED and waits for the commit, the commit does not
     * override the terminal state, and the destroy sequence then removes
     * everything the initialization registered. Baseline red channels:
     * destroy completes while init is still blocked and the committed
     * state accepts re-initialization.
     *
     * @throws Exception if any lifecycle call fails or exceeds its wait bound
     */
    @Test
    void destroy_whileInitInFlight_successRound_blocksThenCleansUp() throws Exception {
        CountDownLatch releaseGate = new CountDownLatch(1);
        BlockingInitRail rail = new BlockingInitRail(releaseGate, false);
        // Direct construction: the snapshot window must observe exactly
        // the init-time registrations and the destroy-time cleanup.
        DeepAgent agent = new DeepAgent(card("sm-ut04s-agent"),
                config(List.of(rail), List.of(noopTool("sm-ut04s-tool"))), null);
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        ExecutorService pool = newPool(2);
        try {
            Future<?> initFuture = pool.submit(agent::ensureInitialized);
            assertThat(rail.awaitInitEntered(5L, TimeUnit.SECONDS)).isTrue();

            Future<?> destroyFuture = pool.submit(agent::destroy);
            assertThatThrownBy(() -> destroyFuture.get(500L, TimeUnit.MILLISECONDS))
                    .as("destroy must block until the in-flight initialization commits")
                    .isInstanceOf(TimeoutException.class);

            releaseGate.countDown();
            initFuture.get(10L, TimeUnit.SECONDS);
            destroyFuture.get(10L, TimeUnit.SECONDS);

            assertThatThrownBy(agent::ensureInitialized)
                    .as("terminal DESTROYED must reject re-initialization")
                    .isInstanceOf(IllegalStateException.class);
            GlobalRegistrySnapshot after = GlobalRegistrySnapshot.take();
            after.assertDeltaZero(before);
        } finally {
            releaseGate.countDown();
            pool.shutdownNow();
            agent.destroy();
        }
    }

    /**
     * Failure round: destroy during a failing in-flight
     * initialization waits for the rollback, keeps DESTROYED (the
     * rollback does not restore NEW), and the destroy sequence cleans
     * the partially registered residue.
     *
     * @throws Exception if any lifecycle call fails or exceeds its wait bound
     */
    @Test
    void destroy_whileInitInFlight_failureRound_cleansResidue() throws Exception {
        CountDownLatch releaseGate = new CountDownLatch(1);
        BlockingInitRail rail = new BlockingInitRail(releaseGate, true);
        DeepAgent agent = new DeepAgent(card("sm-ut04f-agent"),
                config(List.of(rail), List.of(noopTool("sm-ut04f-tool"))), null);
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        ExecutorService pool = newPool(2);
        try {
            Future<?> initFuture = pool.submit(agent::ensureInitialized);
            assertThat(rail.awaitInitEntered(5L, TimeUnit.SECONDS)).isTrue();

            Future<?> destroyFuture = pool.submit(agent::destroy);
            assertThatThrownBy(() -> destroyFuture.get(500L, TimeUnit.MILLISECONDS))
                    .as("destroy must block until the in-flight initialization rolls back")
                    .isInstanceOf(TimeoutException.class);

            releaseGate.countDown();
            assertThatThrownBy(initFuture::get).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            destroyFuture.get(10L, TimeUnit.SECONDS);

            assertThatThrownBy(agent::ensureInitialized)
                    .as("terminal DESTROYED must survive the failed init rollback")
                    .isInstanceOf(IllegalStateException.class);
            GlobalRegistrySnapshot after = GlobalRegistrySnapshot.take();
            after.assertDeltaZero(before);
        } finally {
            releaseGate.countDown();
            pool.shutdownNow();
            agent.destroy();
        }
    }

    /**
     * an interrupt while destroy waits for an in-flight
     * initialization rolls the DESTROYED occupation back to the
     * pre-destroy state, so the committing initialization is unaffected
     * and a later destroy() still owns and runs the cleanup sequence.
     * Baseline red channel: DESTROYED stays set after the interrupt and
     * every later destroy() returns early, leaving rails and tools
     * registered in the global registries forever.
     *
     * @throws Exception if any lifecycle call fails or exceeds its wait bound
     */
    @Test
    void destroy_interruptedDuringInFlightInit_rollsBackForLaterDestroy() throws Exception {
        CountDownLatch releaseGate = new CountDownLatch(1);
        BlockingInitRail rail = new BlockingInitRail(releaseGate, false);
        DeepAgent agent = new DeepAgent(card("sm-ut04i-agent"),
                config(List.of(rail), List.of(noopTool("sm-ut04i-tool"))), null);
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        ExecutorService pool = newPool(2);
        try {
            Future<?> initFuture = pool.submit(agent::ensureInitialized);
            assertThat(rail.awaitInitEntered(5L, TimeUnit.SECONDS)).isTrue();

            Throwable destroyFailure = interruptDestroyInFlight(agent, pool);
            assertThat(destroyFailure).as("interrupted destroy fails visibly")
                    .isInstanceOf(IllegalStateException.class);
            assertThat(agent.isDestroyed()).as("occupation rolled back after the interrupt").isFalse();

            releaseGate.countDown();
            initFuture.get(10L, TimeUnit.SECONDS);
            assertThat(agent.isInitialized()).as("in-flight initialization commits after the rollback").isTrue();

            agent.destroy();
            assertThat(agent.isDestroyed()).as("later destroy owns the cleanup").isTrue();
            GlobalRegistrySnapshot.take().assertDeltaZero(before);
        } finally {
            releaseGate.countDown();
            pool.shutdownNow();
            agent.destroy();
        }
    }

    /**
     * Starts destroy on a pool worker, waits until it occupies DESTROYED
     * behind the in-flight initialization, then interrupts it through
     * future cancellation and returns the failure the destroy call threw.
     *
     * @param agent the agent whose destroy sequence is interrupted
     * @param pool the pool that runs the destroying worker
     * @return the failure the interrupted destroy call threw, or null when it completed
     * @throws Exception if the destroy occupation wait or the join fails
     */
    private static Throwable interruptDestroyInFlight(DeepAgent agent, ExecutorService pool) throws Exception {
        CountDownLatch destroyFinished = new CountDownLatch(1);
        AtomicReference<Throwable> destroyFailure = new AtomicReference<>();
        Future<?> destroyer = pool.submit(() -> {
            IsolatedActions.runIsolated(agent::destroy).ifPresent(destroyFailure::set);
            destroyFinished.countDown();
            return null;
        });
        long deadline = System.currentTimeMillis() + WAIT_PROBE_MILLIS;
        while (!agent.isDestroyed() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
        assertThat(agent.isDestroyed()).as("destroy occupied DESTROYED while waiting").isTrue();

        // G.CON.10/G.CON.12: the interrupt reaches the destroy call through
        // future cancellation, not a direct thread interrupt.
        destroyer.cancel(true);
        assertThat(destroyFinished.await(5000L, TimeUnit.MILLISECONDS))
                .as("interrupted destroy finishes promptly").isTrue();
        return destroyFailure.get();
    }

    /**
     * after destroy every entry point (invoke, stream, run,
     * ensureInitialized) is rejected with IllegalStateException and no
     * revival side effect reaches the global registries. Baseline red
     * channel: ensureInitialized silently returns instead of throwing
     * (the entry-point calls proceed into a destroyed agent).
     */
    @Test
    void destroyedAgent_entryPoints_rejectedWithoutRevival() {
        InitCountingRail rail = new InitCountingRail();
        // Direct construction: construction performs no global registration,
        // so init adds and destroy removes exactly the tool/rail entries
        // visible in the window.
        DeepAgent agent = new DeepAgent(card("sm-ut06-agent"),
                config(List.of(rail), List.of(noopTool("sm-ut06-tool"))), null);
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        agent.ensureInitialized();
        agent.destroy();
        GlobalRegistrySnapshot afterDestroy = GlobalRegistrySnapshot.take();
        afterDestroy.assertDeltaZero(before);

        Map<String, Object> inputs = Map.of("query", "hello", "conversation_id", "sm-ut06-session");
        assertThatThrownBy(() -> agent.invoke(inputs)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> agent.stream(inputs)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> agent.run(inputs)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(agent::ensureInitialized).isInstanceOf(IllegalStateException.class);

        GlobalRegistrySnapshot afterAttempts = GlobalRegistrySnapshot.take();
        afterAttempts.assertDeltaZero(before);
    }

    /**
     * Reflection half: the legacy Lombok-generated
     * {@code getDestroyed()} must disappear when the state machine
     * replaces the destroyed flag (zero callers repo-wide). The
     * {@code isDestroyed()} half joins with the implementation commit.
     *
     * @throws Exception if the reflective method lookup fails unexpectedly
     */
    @Test
    void lifecycleGetterSurface_legacyGetDestroyedRemoved() throws Exception {
        assertThatThrownBy(() -> DeepAgent.class.getDeclaredMethod("getDestroyed"))
                .isInstanceOf(NoSuchMethodException.class);
    }

    /**
     * Getter half: isInitialized() is true only in INITIALIZED,
     * isDestroyed() only in DESTROYED, across the full lifecycle.
     */
    @Test
    void lifecycleGetters_fullCycle_trackDeclaredStates() {
        InitCountingRail rail = new InitCountingRail();
        DeepAgent agent = new DeepAgent(card("sm-ut07-agent"),
                config(List.of(rail), List.of(noopTool("sm-ut07-tool"))), null);
        try {
            assertThat(agent.isInitialized()).isFalse();
            assertThat(agent.isDestroyed()).isFalse();
            agent.ensureInitialized();
            assertThat(agent.isInitialized()).isTrue();
            assertThat(agent.isDestroyed()).isFalse();
            agent.destroy();
            assertThat(agent.isInitialized()).isFalse();
            assertThat(agent.isDestroyed()).isTrue();
        } finally {
            agent.destroy();
        }
    }

    /**
     * while the destroy sequence is stalled inside a rail
     * uninit, isDestroyed() reads stay fast (volatile read, no
     * lifecycleLock acquisition) and already report the terminal state;
     * destroy still progresses to completion after the stall.
     *
     * @throws Exception if the destroy sequence or the stalled-read probe fails
     */
    @Test
    void isDestroyed_whileDestroySequenceStalled_readsAreFastAndTrue() throws Exception {
        CountDownLatch releaseGate = new CountDownLatch(1);
        BlockingUninitRail rail = new BlockingUninitRail(releaseGate);
        DeepAgent agent = new DeepAgent(card("sm-ut08-agent"),
                config(List.of(rail), List.of(noopTool("sm-ut08-tool"))), null);
        agent.ensureInitialized();
        ExecutorService pool = newPool(1);
        try {
            Future<?> destroyFuture = pool.submit(agent::destroy);
            assertThat(rail.awaitUninitEntered(5L, TimeUnit.SECONDS)).isTrue();

            assertFastTerminalReadsWhileStalled(agent);

            releaseGate.countDown();
            destroyFuture.get(10L, TimeUnit.SECONDS);
            assertThat(agent.isDestroyed()).isTrue();
            assertThat(agent.isInitialized()).isFalse();
        } finally {
            releaseGate.countDown();
            pool.shutdownNow();
            agent.destroy();
        }
    }

    private static void assertFastTerminalReadsWhileStalled(DeepAgent agent) {
        long worstReadNanos = 0L;
        for (int i = 0; i < 100; i++) {
            long start = System.nanoTime();
            boolean isAgentDestroyed = agent.isDestroyed();
            long elapsed = System.nanoTime() - start;
            assertThat(isAgentDestroyed).as("destroy already entered while the sequence is stalled").isTrue();
            worstReadNanos = Math.max(worstReadNanos, elapsed);
        }
        assertThat(worstReadNanos).as("worst single isDestroyed() read during the stalled destroy")
                .isLessThan(10_000_000L);
    }

    /**
     * Rail whose uninit blocks on a caller-held gate, so tests can stall
     * the destroy sequence at the rail-unregistration step.
     * destroy invokes DeepAgentRail.uninit explicitly and once more via
     * unregisterAllRails; both calls pass once the gate is open.
     */
    static final class BlockingUninitRail extends com.openjiuwen.harness.rails.DeepAgentRail {
        private final CountDownLatch uninitEntered = new CountDownLatch(1);

        private final CountDownLatch releaseGate;

        BlockingUninitRail(CountDownLatch releaseGate) {
            this.releaseGate = releaseGate;
        }

        @Override
        public void uninit(Object agent) {
            uninitEntered.countDown();
            try {
                releaseGate.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException("blocking rail uninit interrupted", e);
            }
        }

        boolean awaitUninitEntered(long timeout, TimeUnit unit) throws InterruptedException {
            return uninitEntered.await(timeout, unit);
        }
    }

    /**
     * destroy() behind a hanging tool discovery stays bounded.
     * The injected client's listTools honors the resolved discovery
     * timeout (callTimeoutSeconds=1 passes through the discovery
     * resolver) and then fails the initialization; destroy, which waits
     * for the in-flight initialization to roll back, therefore completes
     * within a bounded window instead of hanging forever. The frozen
     * red channel was verified before delivery by temporarily reverting
     * the fixture to an unbounded await, under which destroy never
     * returns.
     *
     * @throws Exception if the probe setup, the bounded destroy, or the residue assertion fails
     */
    @Test
    @DisplayName("Destroy behind hanging tool discovery stays bounded")
    void destroy_whileInitInFlight_hangingToolDiscovery_isBounded() throws Exception {
        CountDownLatch releaseGate = new CountDownLatch(1);
        HangingDiscoveryToolMgr hangingToolMgr = new HangingDiscoveryToolMgr(releaseGate);
        Object registry = fieldValue(Runner.resourceMgr(), "resourceRegistry");
        ToolMgr originalToolMgr = toolMgrOf(registry);
        setFieldValue(registry, "toolMgr", hangingToolMgr);
        DeepAgent agent = hangingDiscoveryAgent();
        ExecutorService pool = newPool(2);
        try {
            assertBoundedDestroyBehindHangingDiscovery(hangingToolMgr, pool, agent);
        } finally {
            releaseHangDiscoveryProbe(releaseGate, pool, originalToolMgr, agent, "sm-ut05-hang-lib");
        }
    }

    private static DeepAgent hangingDiscoveryAgent() {
        return new DeepAgent(card("sm-ut05-agent"),
                DeepAgentConfig.builder()
                        .workspacePath("./target/lifecycle-sm-test-repo")
                        .mcps(List.of(McpServerConfig.builder()
                                .serverName("sm-ut05-hang-lib")
                                .serverPath("http://127.0.0.1:9/mcp")
                                .callTimeoutSeconds(1.0)
                                .build()))
                        .build(),
                null);
    }

    private static void assertBoundedDestroyBehindHangingDiscovery(HangingDiscoveryToolMgr hangingToolMgr,
            ExecutorService pool, DeepAgent agent) throws Exception {
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        runBoundedDestroyProbe(hangingToolMgr, pool, agent);
        GlobalRegistrySnapshot.take().assertDeltaZero(before);
    }

    private static void runBoundedDestroyProbe(HangingDiscoveryToolMgr hangingToolMgr, ExecutorService pool,
            DeepAgent agent) throws Exception {
        Future<?> initFuture = pool.submit(agent::ensureInitialized);
        assertThat(hangingToolMgr.awaitListToolsEntered(5L, TimeUnit.SECONDS))
                .as("the in-flight initialization must enter the hanging tool discovery")
                .isTrue();

        long destroyStart = System.nanoTime();
        agent.destroy();
        long destroyWaitMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - destroyStart);

        assertThat(destroyWaitMillis).as("destroy waits bounded behind the hanging discovery")
                .isGreaterThan(50L)
                .isLessThan(10_000L);
        assertThatThrownBy(initFuture::get)
                .as("the hanging discovery fails the initialization after the bounded timeout")
                .isInstanceOf(ExecutionException.class);
        assertThatThrownBy(agent::ensureInitialized)
                .as("terminal DESTROYED must survive the failed init rollback")
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Reads the ToolMgr of the resource registry through reflection,
     * failing visibly when the field no longer holds a ToolMgr.
     *
     * @param registry the resource registry whose toolMgr field is read
     * @return the ToolMgr held by the registry
     * @throws Exception if the reflective field access fails unexpectedly
     */
    private static ToolMgr toolMgrOf(Object registry) throws Exception {
        if (!(fieldValue(registry, "toolMgr") instanceof ToolMgr toolMgr)) {
            throw new IllegalStateException("resourceRegistry.toolMgr is not a ToolMgr");
        }
        return toolMgr;
    }

    /**
     * Best-effort teardown of the Probe: releases the hanging
     * discovery, shuts the pool down, restores the swapped ToolMgr, and
     * cleans the partially registered MCP server.
     *
     * @param releaseGate the gate the hanging discovery awaits
     * @param pool the probe pool to shut down
     * @param originalToolMgr the ToolMgr to restore into the registry
     * @param agent the probe agent to destroy
     * @param serverId the MCP server id to remove
     * @throws Exception if releasing the gate or restoring the ToolMgr fails
     */
    private static void releaseHangDiscoveryProbe(CountDownLatch releaseGate, ExecutorService pool,
            ToolMgr originalToolMgr, DeepAgent agent, String serverId) throws Exception {
        releaseGate.countDown();
        pool.shutdownNow();
        setFieldValue(fieldValue(Runner.resourceMgr(), "resourceRegistry"), "toolMgr", originalToolMgr);
        // Best-effort teardown: destroy is one-shot and tolerant, so a
        // failure here is intentionally contained and dropped.
        IsolatedActions.runIsolated(agent::destroy);
        // Best-effort cleanup of the partially registered server.
        IsolatedActions.runIsolated(() -> Runner.resourceMgr()
                .removeMcpServer(serverId, null, null, TagMatchStrategy.ALL, true));
    }

    private static AgentCard card(String id) {
        return AgentCard.builder().id(id).name(id).description("lifecycle state machine test agent").build();
    }

    private static DeepAgentConfig config(List<Object> rails, List<Object> tools) {
        return DeepAgentConfig.builder()
                .workspacePath("./target/lifecycle-sm-test-repo")
                .rails(rails)
                .tools(tools)
                .build();
    }

    private static NoopTool noopTool(String id) {
        return new NoopTool(ToolCard.builder().id(id).name(id).description("lifecycle sm test tool").build());
    }

    private static int callbackCount(String agentId) {
        String agentEvent = agentId + "_" + AgentCallbackEvent.BEFORE_INVOKE.getValue();
        return Runner.callbackFramework().listCallbacks(agentEvent).size();
    }

    private static ExecutorService newPool(int threads) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(threads * 2), runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("sm-test-worker-" + thread.getId());
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((failedThread, error) ->
                        Loggers.COMMON.error("Uncaught exception in {}: {}",
                            failedThread.getName(), error.getMessage()));
                    return thread;
                });
    }

    private static Object fieldValue(Object target, String field) throws Exception {
        Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        return declared.get(target);
    }

    private static void setFieldValue(Object target, String field, Object value) throws Exception {
        Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }

    /**
     * ToolMgr double that hands out the hanging listTools client, so a
     * test-controlled MCP discovery can block an in-flight initialization.
     * Mirrors the CountingToolMgr swap pattern of
     * {@code DeepAgentMcpConcurrentRegistrationTest}.
     */
    static final class HangingDiscoveryToolMgr extends ToolMgr {
        private final HangingListToolsClient client;

        HangingDiscoveryToolMgr(CountDownLatch releaseGate) {
            this.client = new HangingListToolsClient(releaseGate);
        }

        @Override
        protected McpClient createClient(McpServerConfig config) {
            return client;
        }

        boolean awaitListToolsEntered(long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (client.listToolsEntries() < 1) {
                if (System.nanoTime() >= deadline) {
                    return false;
                }
                Thread.sleep(5);
            }
            return true;
        }
    }
}
