/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deepagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.IsolatedActions;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.resourcemanager.GlobalRegistrySnapshot;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Execution-destroy crossing: two
 * instances sharing one equivalent registration (the EDPA same-cardId
 * form) — while B resolves and executes the shared entry, A destroys.
 * The crossing must not affect B: its in-flight invocation completes
 * correctly, the entry survives through B's ownership (only A's claim is
 * released), B can resolve and execute again after A's destroy, and the
 * entry is removed only when the last owner (B) destroys. Execution is a
 * direct tool invocation, so the whole scenario stays LLM-free.
 *
 * @since 0.1.16
 */
@DisplayName("S5 shared registration cross destroy during execution")
@Timeout(60)
class DeepAgentSharedRegistrationCrossTest {
    private static final String TOOL_ID = "s5-cross-gated-tool";

    @Test
    @DisplayName("A destroy during B's in-flight execution: B unaffected, entry survives for B")
    void crossDestroyDuringExecution_keepsSurvivingOwnerFullyFunctional() throws Exception {
        AgentCard sharedCard = AgentCard.builder()
                .id("s5-cross-agent")
                .name("s5-cross-agent")
                .description("S5 shared card cross destroy agent")
                .build();
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();

        runCrossDestroyScenario(sharedCard);

        assertThat(Runner.resourceMgr().getTool(TOOL_ID)).as("entry removed after the last owner destroys").isNull();
        GlobalRegistrySnapshot.take().assertDeltaZero(before);
    }

    private static void runCrossDestroyScenario(AgentCard sharedCard) throws Exception {
        GatedSharedTool toolA = new GatedSharedTool("s5-owner-a");
        GatedSharedTool toolB = new GatedSharedTool("s5-owner-b");
        DeepAgent agentA = createInitialized(sharedCard, toolA);
        DeepAgent agentB = createInitialized(sharedCard, toolB);
        try {
            assertThat(Runner.resourceMgr().getTool(TOOL_ID))
                    .as("shared entry resolvable with both owners").isNotNull();

            String inFlightResult = runExecutionWhileDestroying(agentA, agentB);

            assertThat(inFlightResult).as("B's in-flight invocation completes with the expected output")
                    .isEqualTo("cross-ok");
            assertThat(Runner.resourceMgr().getTool(TOOL_ID))
                    .as("entry survives A's destroy through B's ownership").isNotNull();
            assertThat(Runner.resourceMgr().getTool(TOOL_ID, null, TagMatchStrategy.ALL))
                    .as("entry still resolves by tag after A's destroy").isNotNull();

            Object resolvedAfterCross = Runner.resourceMgr().getTool(TOOL_ID);
            assertThat(resolvedAfterCross).as("B can re-resolve the entry after A's destroy").isNotNull();
            if (!(resolvedAfterCross instanceof Tool toolAfterCross)) {
                throw new IllegalStateException("shared entry is not a Tool: " + resolvedAfterCross);
            }
            assertThat(toolAfterCross.invoke(Map.of(), Map.of()))
                    .as("B can execute the entry again after A's destroy")
                    .isEqualTo("cross-ok");

            GlobalRegistrySnapshot afterADestroy = GlobalRegistrySnapshot.take();
            assertThat(afterADestroy.hasOwner(agentA.getOwnerToken()))
                    .as("A's ownership released everywhere").isFalse();
            assertThat(afterADestroy.hasOwner(agentB.getOwnerToken()))
                    .as("B's ownership still claimed").isTrue();
        } finally {
            agentB.destroy();
            agentA.destroy();
        }
    }

    private static DeepAgent createInitialized(AgentCard card, GatedSharedTool tool) {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath("./target/s5-cross-repo")
                .tools(List.of(tool))
                .build();
        DeepAgent agent = HarnessFactory.createDeepAgent(card, config, null);
        agent.ensureInitialized();
        return agent;
    }

    /**
     * Runs B's shared-entry invocation on a worker thread that first
     * blocks inside the tool (the in-flight window), destroys A from the
     * test thread while the invocation is blocked, then releases the tool
     * and joins the worker.
     *
     * @param agentA the destroying instance
     * @param agentB the executing (surviving) instance
     * @return the output of B's in-flight invocation
     * @throws Exception if the in-flight window, the destroy, or the join fails
     */
    private static String runExecutionWhileDestroying(DeepAgent agentA, DeepAgent agentB) throws Exception {
        CountDownLatch toolEntered = new CountDownLatch(1);
        CountDownLatch releaseGate = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService pool = newExecutionWorkerPool();
        try {
            Future<?> future = pool.submit(() -> {
                IsolatedActions.IsolatedOutcome<String> outcome = IsolatedActions.callIsolated(
                        () -> executeGatedTool(toolEntered, releaseGate));
                if (outcome.hasFailure()) {
                    failure.set(outcome.failure());
                } else {
                    result.set(outcome.value());
                }
                return null;
            });
            assertThat(toolEntered.await(10L, TimeUnit.SECONDS))
                    .as("B's invocation must reach the in-flight window inside the tool").isTrue();
            agentA.destroy();
            releaseGate.countDown();
            future.get(20L, TimeUnit.SECONDS);
        } finally {
            releaseGate.countDown();
            pool.shutdownNow();
        }
        if (failure.get() != null) {
            throw new AssertionError("B's in-flight invocation failed while A destroyed", failure.get());
        }
        return result.get();
    }

    /**
     * Creates the single-thread daemon pool that runs B's in-flight
     * invocation.
     *
     * @return a one-thread daemon pool with a named worker
     */
    private static ExecutorService newExecutionWorkerPool() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(2), runnable -> {
                    Thread thread = new Thread(runnable, "s5-execution-worker");
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((failedThread, error) ->
                        Loggers.COMMON.error("Uncaught exception in {}: {}",
                            failedThread.getName(), error.getMessage()));
                    return thread;
                });
    }

    private static String executeGatedTool(CountDownLatch toolEntered, CountDownLatch releaseGate)
            throws InterruptedException {
        Object resolved = Runner.resourceMgr().getTool(TOOL_ID);
        assertThat(resolved).as("shared tool resolves at the start of B's invocation").isNotNull();
        if (!(resolved instanceof Tool tool)) {
            throw new IllegalStateException("shared entry is not a Tool: " + resolved);
        }
        try {
            Object output = tool.invoke(Map.of("tool_entered", toolEntered, "release_gate", releaseGate,
                    "expected_owner", "s5-owner-b"), Map.of());
            return String.valueOf(output);
        } catch (InterruptedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("gated shared tool invocation failed", ex);
        }
    }

    /**
     * Shared noop tool whose invocation signals its start and then blocks
     * on a release gate passed through the invocation inputs, so the test
     * holds the in-flight window open. Both instances register equivalent
     * definitions; the first registrant's instance serves the entry.
     */
    private static final class GatedSharedTool extends Tool {
        private final String marker;

        GatedSharedTool(String marker) {
            super(ToolCard.builder()
                    .id(TOOL_ID)
                    .name(TOOL_ID)
                    .description("S5 gated shared tool")
                    .build());
            this.marker = marker;
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            Object rawToolEntered = inputs.get("tool_entered");
            Object rawReleaseGate = inputs.get("release_gate");
            if (rawToolEntered == null && rawReleaseGate == null) {
                return "cross-ok";
            }
            if (!(rawToolEntered instanceof CountDownLatch toolEntered)
                    || !(rawReleaseGate instanceof CountDownLatch releaseGate)) {
                throw new IllegalStateException("gated tool inputs have the wrong shape for " + marker);
            }
            toolEntered.countDown();
            if (!releaseGate.await(15L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("gated tool release timed out for " + marker);
            }
            return "cross-ok";
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of((Object) "cross-ok").iterator();
        }
    }
}
