/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.IsolatedActions;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.SessionContextHolder;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Multi-instance concurrent execution: one instance per deep-agent-stream pool slot, each running a
 * multi-round tool-call task through the real DeepAgent stream/task-loop
 * chain with the deterministic {@link SequencedToolCallingClient} as the
 * shared model. Invariants: 100 percent result correctness (each instance
 * sees only its own tool observation flowing back), zero cross-instance
 * state leakage (per-instance tool counters and session ids), single
 * instance failure (fake OutOfMemoryError or controlled pseudo-deadlock)
 * contained to its own lifecycle, and pool health afterwards.
 *
 * <p>Timeout is 180s instead of the 30s ST precedent because the instance
 * count equals the pool capacity (CPU-formula derived, 128 on a 16-core
 * host) and each instance drives the full task-loop event machinery.</p>
 *
 * @since 0.1.16
 */
@Tag("system-test")
@DisplayName("DeepAgent multi-instance execution")
@Timeout(180)
class DeepAgentMultiInstanceExecutionSystemTest {
    private static final String PROVIDER = "s3-multi-instance-provider";

    private static final String CARD_ID = "s3-multi-instance-agent";

    private static final String TOOL_PREFIX = "s3-exec-tool-";

    private static final String FAULT_MARKER = "s3a-fault";

    @BeforeEach
    void resetTenantContext() {
        TenantContextHolder.clearCurrentTenant();
    }

    @AfterEach
    void cleanupFaultAgent() {
        Object faultTool = Runner.resourceMgr().getTool(TOOL_PREFIX + FAULT_MARKER);
        if (faultTool instanceof Tool tool) {
            Runner.resourceMgr().removeTool(tool.getCard().getId(), null,
                    TagMatchStrategy.ALL, true);
        }
    }

    @Test
    @DisplayName("S3: pool-capacity instances execute concurrently, 100% correct, zero cross-talk")
    void poolCapacityInstances_allCorrect_zeroCrossTalk() throws Exception {
        int instanceCount = deepAgentStreamCapacity();

        List<InstanceOutcome> outcomes = runInstances(instanceCount, "s3", FaultMode.NONE);

        assertThat(outcomes).hasSize(instanceCount);
        long correct = outcomes.stream().filter(InstanceOutcome::isCorrect).count();
        assertThat(correct).as("result correctness 100 percent across %d instances", instanceCount)
                .isEqualTo(instanceCount);
        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.failure()).as("no instance failed without the fault mode").isNull();
            assertThat(outcome.toolInvocations()).as("each instance's tool called exactly twice").isEqualTo(2);
            assertThat(outcome.sessionIds()).as("tool only saw its own conversation")
                    .containsExactly(outcome.conversationId());
        });
    }

    @Test
    @DisplayName("S3a: one instance throwing fake OutOfMemoryError stays contained, pool healthy")
    void fakeOutOfMemoryErrorInstance_containedOthersCorrect() throws Exception {
        int instanceCount = Math.min(deepAgentStreamCapacity(), 16);
        List<InstanceOutcome> outcomes = runInstances(instanceCount, "s3a-oom", FaultMode.OUT_OF_MEMORY);

        assertThat(outcomes.stream().filter(InstanceOutcome::isCorrect).count())
                .as("healthy instances still 100 percent correct").isEqualTo(instanceCount - 1L);
        InstanceOutcome fault = outcomeByMarker(outcomes, FAULT_MARKER);
        assertThat(fault.terminatedWithinBound()).as("fault instance terminates within the bound").isTrue();

        assertThat(runSingleInstance("s3a-oom-probe", FaultMode.NONE).isCorrect())
                .as("pool stays healthy after the fault (fresh instance completes)");
    }

    @Test
    @DisplayName("S3a: one instance with controlled pseudo-deadlock stays contained, pool healthy")
    void pseudoDeadlockInstance_containedOthersCorrect() throws Exception {
        int instanceCount = Math.min(deepAgentStreamCapacity(), 16);
        List<InstanceOutcome> outcomes = runInstances(instanceCount, "s3a-ddl", FaultMode.PSEUDO_DEADLOCK);

        assertThat(outcomes.stream().filter(InstanceOutcome::isCorrect).count())
                .as("healthy instances still 100 percent correct").isEqualTo(instanceCount - 1L);
        InstanceOutcome fault = outcomeByMarker(outcomes, FAULT_MARKER);
        assertThat(fault.terminatedWithinBound()).as("fault instance terminates within the bound").isTrue();

        assertThat(runSingleInstance("s3a-ddl-probe", FaultMode.NONE).isCorrect())
                .as("pool stays healthy after the fault (fresh instance completes)");
    }

    private static InstanceOutcome outcomeByMarker(List<InstanceOutcome> outcomes, String marker) {
        return outcomes.stream().filter(outcome -> outcome.marker().equals(marker)).findFirst()
                .orElseThrow(() -> new AssertionError("fault instance outcome missing"));
    }

    /**
     * Creates, executes (stream + drain), and destroys one agent per
     * instance slot, all concurrently on the caller-provided instances'
     * own threads. The fault marker instance injects the configured fault
     * mode through its tool.
     *
     * @param instanceCount the number of concurrent instance slots
     * @param runTag the run-scoped tag for tools and model ids
     * @param faultMode the fault injected by the marker instance
     * @return one outcome per instance slot, in arbitrary order
     * @throws Exception if any instance worker fails or exceeds its wait bound
     */
    private static List<InstanceOutcome> runInstances(int instanceCount, String runTag, FaultMode faultMode)
            throws Exception {
        ExecutorService pool = new ThreadPoolExecutor(instanceCount, instanceCount, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(instanceCount * 2), runnable -> {
                    Thread thread = new Thread(runnable, runTag + "-worker");
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((failedThread, error) ->
                        Loggers.COMMON.error("Uncaught exception in {}: {}",
                            failedThread.getName(), error.getMessage()));
                    return thread;
                });
        List<InstanceOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> futures = new ArrayList<>();
            SequencedToolCallingClient modelClient = SequencedToolCallingClient.withRequestDerivedTool(
                    2, TOOL_PREFIX, runTag + "-call-", PROVIDER + "-" + runTag);
            var model = modelClient.registerSharedModel();
            AgentCard sharedCard = buildSharedCard();
            for (int i = 0; i < instanceCount; i++) {
                String marker = i == 0 && faultMode != FaultMode.NONE ? FAULT_MARKER : runTag + "-" + i;
                FaultMode mode = i == 0 ? faultMode : FaultMode.NONE;
                futures.add(pool.submit(() -> {
                    outcomes.add(executeSingleInstance(sharedCard, model, marker, mode));
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(150L, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return List.copyOf(outcomes);
    }

    private static AgentCard buildSharedCard() {
        return AgentCard.builder()
                .id(CARD_ID)
                .name(CARD_ID)
                .description("S3 multi-instance execution agent")
                .build();
    }

    private static InstanceOutcome runSingleInstance(String marker, FaultMode faultMode) throws Exception {
        SequencedToolCallingClient modelClient = SequencedToolCallingClient.withRequestDerivedTool(
                2, TOOL_PREFIX, marker + "-call-", PROVIDER + "-" + marker);
        var model = modelClient.registerSharedModel();
        return executeSingleInstance(buildSharedCard(), model, marker, faultMode);
    }

    private static InstanceOutcome executeSingleInstance(AgentCard sharedCard, Object model, String marker,
            FaultMode faultMode) {
        CountingSessionTool tool = new CountingSessionTool(TOOL_PREFIX + marker, marker, faultMode);
        String conversationId = "s3-conv-" + marker;
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath("./target/s3-multi-instance-repo")
                .enableTaskLoop(true)
                .maxIterations(5)
                .completionTimeout(60.0)
                .tools(List.of(tool))
                .model(model)
                .build();
        DeepAgent agent = HarnessFactory.createDeepAgent(sharedCard, config, null);
        try {
            IsolatedActions.IsolatedOutcome<InstanceOutcome> outcome = IsolatedActions.callIsolated(
                    () -> runAgentStream(agent, conversationId, marker, tool));
            if (outcome.hasFailure()) {
                return new InstanceOutcome(marker, conversationId, List.of(), tool, outcome.failure());
            }
            return outcome.value();
        } finally {
            agent.destroy();
        }
    }

    private static InstanceOutcome runAgentStream(DeepAgent agent, String conversationId, String marker,
            CountingSessionTool tool) throws Exception {
        agent.ensureInitialized();
        Iterator<Object> stream = agent.stream(Map.of("query", "s3 task " + marker,
                "conversation_id", conversationId), List.of(StreamMode.OUTPUT));
        List<Object> frames = new ArrayList<>();
        long drainDeadline = System.currentTimeMillis() + 90000L;
        while (stream.hasNext()) {
            frames.add(stream.next());
            if (System.currentTimeMillis() > drainDeadline) {
                throw new IllegalStateException("instance " + marker + " stream drain exceeded the bound");
            }
        }
        return new InstanceOutcome(marker, conversationId, frames, tool, null);
    }

    /**
     * The deep-agent-stream pool capacity on this runtime: the pool's
     * maximum thread count plus its bounded queue capacity, read through
     * reflection from the DeepAgent static executor (JDK 17 platform
     * pool). On the unbounded virtual-thread pool (JDK 21+) the canonical
     * DeepAgent task-concurrency default is used instead.
     *
     * @return the pool capacity in concurrently executable instances
     * @throws ReflectiveOperationException if the DeepAgent executor field is missing
     */
    private static int deepAgentStreamCapacity() throws ReflectiveOperationException {
        Field field = DeepAgent.class.getDeclaredField("STREAM_EXECUTOR");
        field.setAccessible(true);
        Object executor = field.get(null);
        if (executor instanceof java.util.concurrent.ThreadPoolExecutor pool) {
            int queued = pool.getQueue().size() + pool.getQueue().remainingCapacity();
            return pool.getMaximumPoolSize() + queued;
        }
        return OpenJiuwenExecutors.defaultTaskConcurrency();
    }

    /**
     * Per-instance counting tool: counts invocations, records the session
     * id of every invocation (the cross-talk tripwire), and returns a
     * marker-tagged observation. Fault modes inject the S3a failure
     * semantics: a fake OutOfMemoryError thrown without consuming memory,
     * or a bounded latch wait that emulates a tool-level deadlock before
     * failing.
     */
    private static final class CountingSessionTool extends Tool {
        private final String marker;

        private final FaultMode faultMode;

        private final AtomicInteger invocations = new AtomicInteger();

        private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

        CountingSessionTool(String toolId, String marker, FaultMode faultMode) {
            super(ToolCard.builder()
                    .id(toolId)
                    .name(toolId)
                    .description("S3 counting tool for " + marker)
                    .inputParams(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                    .build());
            this.marker = marker;
            this.faultMode = faultMode;
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            invocations.incrementAndGet();
            Session session = SessionContextHolder.getCurrentSession();
            sessionIds.add(session != null ? session.getSessionId() : "missing");
            if (faultMode == FaultMode.OUT_OF_MEMORY) {
                throw new OutOfMemoryError("S3a simulated tool failure for " + marker);
            }
            if (faultMode == FaultMode.PSEUDO_DEADLOCK) {
                if (!new CountDownLatch(1).await(1500L, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("S3a pseudo-deadlock timeout for " + marker);
                }
            }
            return "s3-ok-" + marker;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of((Object) ("s3-ok-" + marker)).iterator();
        }

        int invocations() {
            return invocations.get();
        }

        Set<String> sessionIds() {
            return Set.copyOf(sessionIds);
        }
    }

    private enum FaultMode {
        NONE,
        OUT_OF_MEMORY,
        PSEUDO_DEADLOCK
    }

    private record InstanceOutcome(String marker, String conversationId, List<Object> frames,
            CountingSessionTool tool, Throwable failure) {

        boolean isCorrect() {
            if (failure != null || tool.invocations() != 2) {
                return false;
            }
            AtomicReference<String> flattened = new AtomicReference<>("");
            for (Object frame : frames) {
                if (frame instanceof OutputSchema schema && schema.getPayload() != null) {
                    flattened.set(flattened.get() + " " + schema.getPayload());
                }
            }
            return flattened.get().contains("s3-ok-" + marker);
        }

        boolean terminatedWithinBound() {
            return failure != null || !frames.isEmpty();
        }

        int toolInvocations() {
            return tool.invocations();
        }

        Set<String> sessionIds() {
            return tool.sessionIds();
        }
    }
}
