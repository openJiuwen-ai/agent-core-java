/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deepagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.openjiuwen.core.common.logging.LogCaptureFixture;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.IsolatedActions;
import com.openjiuwen.core.foundation.tool.NoopTool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.multitenant.TmpFileCleaner;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.resourcemanager.GlobalRegistrySnapshot;
import com.openjiuwen.core.singleagent.AgentCallbackManager;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import ch.qos.logback.classic.Level;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Destroy-chain isolation tests:
 * any single rail, tool-card, or callback cleanup failure during destroy
 * must not abort the batch or later destroy steps; failures surface as
 * aggregated ERROR logs while every cleanable registration is released.
 */
@DisplayName("DeepAgent destroy chain exception isolation")
class DeepAgentDestroyIsolationTest {
    private static final String WORKSPACE = "./target/destroy-isolation-test";

    /**
     * Rail that counts uninit invocations and registers one callback, the
     * exact shape business rails (EdpaTodoRail, ...) use. The callback
     * captures the rail instance so every rail registers a distinct
     * callback object (non-capturing lambdas are JVM-cached per call site
     * and would collide across rails in the wrappedCallbacks map).
     */
    static final class TrackingRail extends AgentRail {
        final AtomicInteger uninitCount = new AtomicInteger();

        @Override
        public Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> getCallbacks() {
            Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks =
                new EnumMap<>(AgentCallbackEvent.class);
            callbacks.put(AgentCallbackEvent.BEFORE_INVOKE, ctx -> uninitCount.get());
            return callbacks;
        }

        @Override
        public void uninit(Object agent) {
            uninitCount.incrementAndGet();
        }
    }

    /**
     * Plain AgentRail whose uninit throws, injecting the "rail uninit user
     * code fails" point of the ACM batch path.
     */
    static final class ThrowingUninitRail extends AgentRail {
        final AtomicInteger fires = new AtomicInteger();

        @Override
        public Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> getCallbacks() {
            Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks =
                new EnumMap<>(AgentCallbackEvent.class);
            callbacks.put(AgentCallbackEvent.BEFORE_INVOKE, ctx -> fires.incrementAndGet());
            return callbacks;
        }

        @Override
        public void uninit(Object agent) {
            throw new IllegalStateException("boom-uninit-rail");
        }
    }

    /**
     * DeepAgentRail whose uninit throws, injecting the same failure into the
     * destroyRails direct loop over registeredRails.
     */
    static final class ThrowingUninitDeepRail extends DeepAgentRail {
        final AtomicInteger fires = new AtomicInteger();

        @Override
        public Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> getCallbacks() {
            Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks =
                new EnumMap<>(AgentCallbackEvent.class);
            callbacks.put(AgentCallbackEvent.BEFORE_INVOKE, ctx -> fires.incrementAndGet());
            return callbacks;
        }

        @Override
        public void uninit(Object agent) {
            throw new IllegalStateException("boom-uninit-deep-rail");
        }
    }

    /**
     * Callback whose hashCode/equals throw once armed: registration path
     * stays clean, the unregister path (map remove by callback key) fails.
     */
    static final class ArmedCallback implements Consumer<AgentCallbackContext> {
        private volatile boolean isArmed;

        void arm() {
            isArmed = true;
        }

        @Override
        public void accept(AgentCallbackContext ctx) {
            // no-op
        }

        @Override
        public int hashCode() {
            if (isArmed) {
                throw new IllegalStateException("boom-callback-key");
            }
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object obj) {
            if (isArmed) {
                throw new IllegalStateException("boom-callback-key");
            }
            return obj == this;
        }
    }

    /**
     * Rail with one armed callback plus one normal callback: the armed one
     * fails the callback-unregister step while the normal one must still be
     * cleaned, proving per-registration continuation inside that step.
     */
    static final class ArmedCallbackRail extends AgentRail {
        final ArmedCallback armed = new ArmedCallback();
        final AtomicInteger uninitCount = new AtomicInteger();

        @Override
        public Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> getCallbacks() {
            Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks =
                new EnumMap<>(AgentCallbackEvent.class);
            callbacks.put(AgentCallbackEvent.BEFORE_INVOKE, armed);
            callbacks.put(AgentCallbackEvent.AFTER_INVOKE, ctx -> uninitCount.get());
            return callbacks;
        }

        @Override
        public void uninit(Object agent) {
            uninitCount.incrementAndGet();
        }
    }

    /**
     * ToolCard whose getName() throws once armed: rail-tool registration
     * (AbilityManager.add) stays clean, the tool-card removal step fails.
     */
    static final class ArmedToolCard extends ToolCard {
        private volatile boolean isArmed;

        ArmedToolCard() {
            setId("armed-tool-card");
            setName("armed-tool-card");
            setDescription("card that fails name reads once armed");
        }

        void arm() {
            isArmed = true;
        }

        @Override
        public String getName() {
            if (isArmed) {
                throw new IllegalStateException("boom-tool-card-name");
            }
            return super.getName();
        }
    }

    /**
     * Rail carrying one armed tool card: the tool-card removal step of
     * unregisterRail fails while its callbacks and uninit still run.
     */
    static final class ArmedToolCardRail extends AgentRail {
        final ArmedToolCard card;
        final AtomicInteger uninitCount = new AtomicInteger();

        ArmedToolCardRail() {
            this(new ArmedToolCard());
        }

        private ArmedToolCardRail(ArmedToolCard card) {
            super(List.of(card));
            this.card = card;
        }

        @Override
        public Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> getCallbacks() {
            Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks =
                new EnumMap<>(AgentCallbackEvent.class);
            callbacks.put(AgentCallbackEvent.BEFORE_INVOKE, ctx -> uninitCount.get());
            return callbacks;
        }

        @Override
        public void uninit(Object agent) {
            uninitCount.incrementAndGet();
        }
    }

    /**
     * TmpFileCleaner stub whose stop() throws, injecting a failure into one
     * destroy step (the per-step isolation layer of the destroy chain).
     */
    static final class ThrowingTmpFileCleaner extends TmpFileCleaner {
        ThrowingTmpFileCleaner() {
            super(java.time.Duration.ofSeconds(60L), java.time.Duration.ofSeconds(60L), "./target", null);
        }

        @Override
        public void stop() {
            throw new IllegalStateException("boom-tmp-cleaner");
        }
    }

    /**
     * Tool whose getCard() throws once armed: registration stays clean, the
     * destroyTools per-tool release step fails for this tool only.
     */
    static final class ArmedCardTool extends NoopTool {
        private volatile boolean isArmed;
        private final ToolCard card = ToolCard.builder().id("armed-card-tool").name("armed-card-tool")
                .description("tool whose card read fails once armed").build();

        void arm() {
            isArmed = true;
        }

        @Override
        public ToolCard getCard() {
            if (isArmed) {
                throw new IllegalStateException("boom-tool-card");
            }
            return card;
        }
    }

    /**
     * one rail's uninit throwing inside unregisterAllRails must
     * not abort the batch: every rail is cleaned (registrations drained,
     * global callbacks empty) and the failure is visible as an ERROR log.
     *
     * @throws Exception when rail registration or the log capture fails
     */
    @Test
    void ut21UnregisterAllRailsIsolatesPerRailFailure() throws Exception {
        AgentCallbackManager manager = new AgentCallbackManager("ut21-agent");
        Object agentStub = new Object();
        TrackingRail first = new TrackingRail();
        ThrowingUninitRail bad = new ThrowingUninitRail();
        TrackingRail last = new TrackingRail();
        manager.registerRail(first, agentStub);
        manager.registerRail(bad, agentStub);
        manager.registerRail(last, agentStub);
        String agentEvent = "ut21-agent_" + AgentCallbackEvent.BEFORE_INVOKE.getValue();
        try {
            assertThat(Runner.callbackFramework().listCallbacks(agentEvent)).hasSize(3);
            try (LogCaptureFixture logs = LogCaptureFixture.attach("agent")) {
                assertThatCode(() -> manager.unregisterAllRails(agentStub))
                        .as("a single failing rail uninit must not abort the batch")
                        .doesNotThrowAnyException();
                assertThat(logs.count(Level.ERROR, "ThrowingUninitRail")).isGreaterThanOrEqualTo(1);
            }
            assertThat(first.uninitCount).as("first rail must still be uninitialized").hasValue(1);
            assertThat(last.uninitCount).as("last rail must still be uninitialized").hasValue(1);
            assertThat(Runner.callbackFramework().listCallbacks(agentEvent))
                    .as("all rail callbacks must be released from the global framework")
                    .isEmpty();
            assertThat(railRegistrationCount(manager)).as("rail registration table must be drained").isZero();
        } finally {
            cleanupFrameworkResidue("ut21-agent");
        }
    }

    /**
     * destroy with one throwing business rail must complete every
     * destroy step: surviving rails clean, no exception escapes, the global
     * registry fingerprint is unchanged (ownership fully released), and the
     * task-scheduler thread falls back.
     *
     * @throws Exception when the agent lifecycle or the log capture fails
     */
    @Test
    void ut22DestroyIsolatesThrowingRail() throws Exception {
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        int threadsBefore = countTaskSchedulerThreads();
        TrackingRail first = new TrackingRail();
        ThrowingUninitRail bad = new ThrowingUninitRail();
        TrackingRail last = new TrackingRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath(WORKSPACE).enableTaskLoop(true)
                .rails(List.of(first, bad, last)).build());
        String agentId = agent.getCard().getId();
        try {
            agent.ensureInitialized();
            try (LogCaptureFixture logs = LogCaptureFixture.attach("agent")) {
                assertThatCode(agent::destroy)
                        .as("a throwing rail must not abort the destroy sequence")
                        .doesNotThrowAnyException();
                assertThat(logs.count(Level.ERROR, "ThrowingUninitRail")).isGreaterThanOrEqualTo(1);
            }
            assertThat(first.uninitCount).as("first rail must be uninitialized").hasValue(1);
            assertThat(last.uninitCount).as("last rail must be uninitialized").hasValue(1);
            GlobalRegistrySnapshot.take().assertDeltaZero(before);
            assertThatTaskSchedulerThreadsFellBack(threadsBefore);
            assertThatFrameworkIsClean(agentId);
        } finally {
            cleanupFrameworkResidue(agentId);
        }
    }

    /**
     * Direct loop form: a throwing DeepAgentRail must not abort
     * the destroyRails loop over registeredRails; later cleanup continues.
     *
     * @throws Exception when the agent lifecycle or the log capture fails
     */
    @Test
    void ut22DestroyIsolatesThrowingDeepAgentRail() throws Exception {
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        ThrowingUninitDeepRail bad = new ThrowingUninitDeepRail();
        TrackingRail survivor = new TrackingRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath(WORKSPACE).enableTaskLoop(true)
                .rails(List.of(bad, survivor)).build());
        String agentId = agent.getCard().getId();
        try {
            agent.ensureInitialized();
            try (LogCaptureFixture logs = LogCaptureFixture.attach("agent")) {
                assertThatCode(agent::destroy)
                        .as("a throwing DeepAgentRail must not abort the destroy sequence")
                        .doesNotThrowAnyException();
                assertThat(logs.count(Level.ERROR, "deep rail")).isGreaterThanOrEqualTo(1);
            }
            assertThat(survivor.uninitCount).as("surviving rail must be uninitialized").hasValue(1);
            GlobalRegistrySnapshot.take().assertDeltaZero(before);
            assertThatFrameworkIsClean(agentId);
        } finally {
            cleanupFrameworkResidue(agentId);
        }
    }

    /**
     * Step layer: a failing destroy step (tmpFileCleaner) must
     * not abort the remaining steps: rails still unregister, the
     * sys-operation ownership is still released, the scheduler thread still
     * stops, and the failure surfaces in the aggregated residue report.
     *
     * @throws Exception when the agent lifecycle, the reflection swap, or the log capture fails
     */
    @Test
    void ut22DestroyIsolatesStepFailure() throws Exception {
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        int threadsBefore = countTaskSchedulerThreads();
        TrackingRail rail = new TrackingRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath(WORKSPACE).enableTaskLoop(true).rails(List.of(rail)).build());
        String agentId = agent.getCard().getId();
        try {
            agent.ensureInitialized();
            Field cleanerField = DeepAgent.class.getDeclaredField("tmpFileCleaner");
            cleanerField.setAccessible(true);
            cleanerField.set(agent, new ThrowingTmpFileCleaner());
            try (LogCaptureFixture logs = LogCaptureFixture.attach("agent")) {
                assertThatCode(agent::destroy)
                        .as("a failing destroy step must not abort the sequence")
                        .doesNotThrowAnyException();
                assertThat(logs.count(Level.ERROR, "step 'tmpFileCleaner'"))
                        .as("the failed step must be logged")
                        .isGreaterThanOrEqualTo(1);
                assertThat(logs.count(Level.ERROR, "residue may remain"))
                        .as("the aggregated residue report must be visible")
                        .isGreaterThanOrEqualTo(1);
            }
            assertThat(rail.uninitCount).as("later steps must still run: rails cleaned").hasValue(1);
            GlobalRegistrySnapshot.take().assertDeltaZero(before);
            assertThatTaskSchedulerThreadsFellBack(threadsBefore);
            assertThatFrameworkIsClean(agentId);
        } finally {
            cleanupFrameworkResidue(agentId);
        }
    }

    /**
     * Tool-card removal step: an armed tool card failing the
     * AbilityManager removal step must not stop that rail's uninit nor the
     * rest of the destroy sequence, and leaves no global residue.
     *
     * @throws Exception when the agent lifecycle or the log capture fails
     */
    @Test
    void ut23DestroyIsolatesToolCardRemovalFailure() throws Exception {
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        ArmedToolCardRail armedRail = new ArmedToolCardRail();
        TrackingRail survivor = new TrackingRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath(WORKSPACE).enableTaskLoop(true)
                .rails(List.of(armedRail, survivor)).build());
        String agentId = agent.getCard().getId();
        try {
            agent.ensureInitialized();
            armedRail.card.arm();
            try (LogCaptureFixture logs = LogCaptureFixture.attach("agent")) {
                assertThatCode(agent::destroy)
                        .as("a failing tool-card removal must not abort the destroy sequence")
                        .doesNotThrowAnyException();
                assertThat(logs.count(Level.ERROR, "tool-card")).isGreaterThanOrEqualTo(1);
            }
            assertThat(armedRail.uninitCount)
                    .as("rail uninit must still run after the tool-card step failed").hasValue(1);
            assertThat(survivor.uninitCount).as("surviving rail must be uninitialized").hasValue(1);
            GlobalRegistrySnapshot.take().assertDeltaZero(before);
            assertThatFrameworkIsClean(agentId);
        } finally {
            cleanupFrameworkResidue(agentId);
        }
    }

    /**
     * Callback-unregister step: an armed callback failing the
     * unregister step must not stop the sibling callback's cleanup, that
     * rail's uninit, or the destroy sequence; the unrecoverable residue of
     * the broken callback itself stays visible via the ERROR log (
     * unrecoverable failures are logged, not silently dropped).
     *
     * @throws Exception when the agent lifecycle or the log capture fails
     */
    @Test
    void ut23DestroyIsolatesCallbackUnregisterFailure() throws Exception {
        ArmedCallbackRail armedRail = new ArmedCallbackRail();
        TrackingRail survivor = new TrackingRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath(WORKSPACE).enableTaskLoop(true)
                .rails(List.of(armedRail, survivor)).build());
        String agentId = agent.getCard().getId();
        try {
            agent.ensureInitialized();
            armedRail.armed.arm();
            try (LogCaptureFixture logs = LogCaptureFixture.attach("agent")) {
                assertThatCode(agent::destroy)
                        .as("a failing callback unregister must not abort the destroy sequence")
                        .doesNotThrowAnyException();
                assertThat(logs.count(Level.ERROR, "callback")).isGreaterThanOrEqualTo(1);
            }
            String beforeInvoke = agentId + "_" + AgentCallbackEvent.BEFORE_INVOKE.getValue();
            String afterInvoke = agentId + "_" + AgentCallbackEvent.AFTER_INVOKE.getValue();
            assertThat(Runner.callbackFramework().listCallbacks(afterInvoke))
                    .as("the sibling callback must be cleaned despite the armed one failing")
                    .isEmpty();
            assertThat(armedRail.uninitCount)
                    .as("rail uninit must still run after the callback step failed").hasValue(1);
            assertThat(survivor.uninitCount).as("surviving rail must be uninitialized").hasValue(1);
        } finally {
            cleanupFrameworkResidue(agentId);
        }
    }

    /**
     * Harness-tool release layer: one registered harness tool
     * failing its release step must not abort the destroyTools loop: the
     * failing tool surfaces in the aggregated residue report while every
     * other registration is still released.
     *
     * @throws Exception when the agent lifecycle or the log capture fails
     */
    @Test
    void ut23DestroyIsolatesHarnessToolReleaseFailure() throws Exception {
        ArmedCardTool armedTool = new ArmedCardTool();
        TrackingRail rail = new TrackingRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath(WORKSPACE).enableTaskLoop(true).rails(List.of(rail))
                .tools(List.of(armedTool, new NoopTool())).build());
        String agentId = agent.getCard().getId();
        try {
            agent.ensureInitialized();
            armedTool.arm();
            try (LogCaptureFixture logs = LogCaptureFixture.attach("agent")) {
                assertThatCode(agent::destroy)
                        .as("a failing harness-tool release must not abort the destroy sequence")
                        .doesNotThrowAnyException();
                assertThat(logs.count(Level.ERROR, "release failed")).isGreaterThanOrEqualTo(1);
                assertThat(logs.count(Level.ERROR, "residue may remain")).isGreaterThanOrEqualTo(1);
            }
            assertThat(rail.uninitCount).as("rails must still be cleaned").hasValue(1);
            assertThat(Runner.resourceMgr().getTool("noop-tool", null, TagMatchStrategy.ALL))
                    .as("the sibling tool must still be released")
                    .isNull();
        } finally {
            cleanupFrameworkResidue(agentId);
        }
    }

    /**
     * two threads entering destroy concurrently: exactly one runs
     * the full destroy sequence (single uninit per rail), the other returns
     * silently, and neither produces a cleanup exception.
     *
     * @throws Exception when the agent lifecycle or a destroyer future fails
     */
    @Test
    void ut24ConcurrentDestroyRunsExactlyOnce() throws Exception {
        TrackingRail rail = new TrackingRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath(WORKSPACE).enableTaskLoop(true).rails(List.of(rail)).build());
        String agentId = agent.getCard().getId();
        try {
            agent.ensureInitialized();
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            ExecutorService pool = newDestroyPool();
            try {
                List<Future<?>> destroyers = new ArrayList<>(2);
                for (int i = 0; i < 2; i++) {
                    destroyers.add(pool.submit(() -> {
                        runDestroy(agent, barrier, failure);
                        return null;
                    }));
                }
                for (Future<?> destroyer : destroyers) {
                    // Bounded wait: a wedged destroy fails the test through
                    // the timeout instead of hanging it.
                    destroyer.get(10000L, TimeUnit.MILLISECONDS);
                }
                assertThat(failure.get()).as("neither destroy call may throw").isNull();
                assertThat(rail.uninitCount).as("the destroy sequence must run exactly once").hasValue(1);
                assertThat(agent.isDestroyed()).isTrue();
                assertThatFrameworkIsClean(agentId);
            } finally {
                pool.shutdownNow();
            }
        } finally {
            cleanupFrameworkResidue(agentId);
        }
    }

    /**
     * after a normal destroy the instance leaves zero global
     * residue: global callbacks drained, task-scheduler threads released,
     * and the sys-operation/tool ownership claimed at creation (owner
     * token) fully released, with no tag residue left behind.
     *
     * @throws Exception when the agent lifecycle or the residue assertions fail
     */
    @Test
    void ut25DestroyLeavesNoGlobalResidue() throws Exception {
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        int threadsBefore = countTaskSchedulerThreads();
        TrackingRail rail = new TrackingRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath(WORKSPACE).enableTaskLoop(true).rails(List.of(rail))
                .tools(List.of(new NoopTool())).build());
        String agentId = agent.getCard().getId();
        String ownerToken = agent.getOwnerToken();
        try {
            agent.ensureInitialized();
            String sysOpId = "deep_agent_" + agentId;
            assertThat(Runner.resourceMgr().getSysOperation(sysOpId, null, TagMatchStrategy.ALL))
                    .as("precondition: factory must have registered the sys operation")
                    .isNotNull();
            agent.destroy();
            GlobalRegistrySnapshot after = GlobalRegistrySnapshot.take();
            after.assertDeltaZero(before);
            assertThat(after.hasOwner(ownerToken))
                    .as("no global resource may keep the destroyed owner's claim")
                    .isFalse();
            assertThatTaskSchedulerThreadsFellBack(threadsBefore);
            assertThatFrameworkIsClean(agentId);
        } finally {
            cleanupFrameworkResidue(agentId);
        }
    }

    private static void runDestroy(DeepAgent agent, CyclicBarrier barrier, AtomicReference<Throwable> failure) {
        IsolatedActions.runIsolated(() -> {
            barrier.await(10000L, TimeUnit.MILLISECONDS);
            agent.destroy();
            return null;
        }).ifPresent(ex -> failure.compareAndSet(null, ex));
    }

    private static ExecutorService newDestroyPool() {
        AtomicInteger seq = new AtomicInteger();
        return new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(2), runnable -> {
                    Thread thread = new Thread(runnable, "ut24-destroy-" + seq.incrementAndGet());
                    thread.setUncaughtExceptionHandler((failedThread, error) ->
                        Loggers.COMMON.error("Uncaught exception in {}: {}",
                            failedThread.getName(), error.getMessage()));
                    return thread;
                });
    }

    private static int railRegistrationCount(AgentCallbackManager manager) throws Exception {
        Field field = AgentCallbackManager.class.getDeclaredField("railRegistrations");
        field.setAccessible(true);
        Map<?, ?> registrations = (Map<?, ?>) field.get(manager);
        return registrations.size();
    }

    private static void assertThatFrameworkIsClean(String agentId) {
        for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
            String agentEvent = agentId + "_" + event.getValue();
            assertThat(Runner.callbackFramework().listCallbacks(agentEvent))
                    .as("event %s must have no callbacks left after destroy", agentEvent)
                    .isEmpty();
        }
    }

    private static void cleanupFrameworkResidue(String agentId) {
        for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
            Runner.callbackFramework().unregisterEvent(agentId + "_" + event.getValue());
        }
    }

    private static int countTaskSchedulerThreads() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        int count = 0;
        for (ThreadInfo info : threads.dumpAllThreads(false, false)) {
            if (info != null && info.getThreadName() != null && info.getThreadName().contains("task-scheduler")) {
                count++;
            }
        }
        return count;
    }

    private static void assertThatTaskSchedulerThreadsFellBack(int threadsBefore) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        int after;
        do {
            Thread.sleep(200L);
            after = countTaskSchedulerThreads();
        } while (after > threadsBefore && System.currentTimeMillis() < deadline);
        assertThat(after).as("task-scheduler threads must fall back after destroy").isLessThanOrEqualTo(threadsBefore);
    }
}
