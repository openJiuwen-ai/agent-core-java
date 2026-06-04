/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.agent_evolving.trajectory.InMemoryTrajectoryStore;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.evolution.EvolutionRail;
import com.openjiuwen.harness.rails.evolution.EvolutionTriggerPoint;
import com.openjiuwen.harness.rails.evolution.TrajectoryRail;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_evolution_rail} in
 * {@code tests.unit_tests.harness.test_evolution_rail}.
 */
class TestEvolutionRail {

    @Test
    @Tag("level0")
    @DisplayName("trajectory collection records model and tool steps")
    void testTrajectoryCollectionBasic() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        EvolutionRail rail = new EvolutionRail(store, false);

        rail.beforeInvoke(ctx(AgentCallbackEvent.BEFORE_INVOKE, InvokeInputs.builder()
                .query("test query")
                .conversationId("conv_123")
                .build()));
        rail.afterModelCall(ctx(AgentCallbackEvent.AFTER_MODEL_CALL, ModelCallInputs.builder()
                .messages(List.of(Map.of("role", "user", "content", "hello")))
                .response(Map.of("role", "assistant", "content", "hi there"))
                .build()));
        rail.afterToolCall(ctx(AgentCallbackEvent.AFTER_TOOL_CALL, ToolCallInputs.builder()
                .toolName("read_file")
                .toolArgs(Map.of("file_path", "/tmp/test.txt"))
                .toolResult("file contents")
                .build()));
        rail.afterInvoke(ctx(AgentCallbackEvent.AFTER_INVOKE, InvokeInputs.builder()
                .query("test query")
                .conversationId("conv_123")
                .result(Map.of("status", "done"))
                .build()));

        List<Trajectory> trajectories = store.queryBySessionId("conv_123");
        assertEquals(1, trajectories.size());
        Trajectory trajectory = trajectories.get(0);
        assertEquals("conv_123", trajectory.getSessionId());
        assertEquals("online", trajectory.getSource());
        assertEquals(2, trajectory.getSteps().size());
        assertEquals("llm", trajectory.getSteps().get(0).getKind());
        assertNotNull(trajectory.getSteps().get(0).getDetail());
        assertEquals("tool", trajectory.getSteps().get(1).getKind());
        assertNotNull(trajectory.getSteps().get(1).getDetail());
    }

    @Test
    @Tag("level0")
    @DisplayName("extension points are called")
    void testExtensionPointsCalled() {
        List<String> callLog = new ArrayList<>();
        EvolutionRail rail = new EvolutionRail(new InMemoryTrajectoryStore(), null, false,
                EvolutionTriggerPoint.AFTER_INVOKE, false) {
            @Override
            protected void onAfterModelCall(AgentCallbackContext ctx) {
                callLog.add("model");
            }

            @Override
            protected void onAfterToolCall(AgentCallbackContext ctx) {
                callLog.add("tool");
            }

            @Override
            protected void runEvolution(Trajectory trajectory, AgentCallbackContext ctx, Map<String, Object> snapshot) {
                callLog.add("evolution");
            }
        };

        rail.beforeInvoke(ctx(AgentCallbackEvent.BEFORE_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_456").build()));
        rail.afterModelCall(ctx(AgentCallbackEvent.AFTER_MODEL_CALL, ModelCallInputs.builder()
                .messages(List.of(Map.of("role", "user", "content", "test")))
                .response(Map.of("role", "assistant", "content", "ok"))
                .build()));
        rail.afterToolCall(ctx(AgentCallbackEvent.AFTER_TOOL_CALL, ToolCallInputs.builder()
                .toolName("test_tool")
                .toolArgs(Map.of())
                .toolResult("done")
                .build()));
        rail.afterInvoke(ctx(AgentCallbackEvent.AFTER_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_456").build()));

        assertEquals(List.of("model", "tool", "evolution"), callLog);
    }

    @Test
    @Tag("level0")
    @DisplayName("hooks are no-op without builder")
    void testNoOpWithoutBuilder() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        EvolutionRail rail = new EvolutionRail(store, false);

        rail.afterModelCall(ctx(AgentCallbackEvent.AFTER_MODEL_CALL, ModelCallInputs.builder()
                .messages(List.of(Map.of("role", "user", "content", "test")))
                .response(Map.of("role", "assistant", "content", "ok"))
                .build()));

        assertTrue(store.query(null, Map.of()).isEmpty());
    }

    @Test
    @Tag("level0")
    @DisplayName("default accumulation mode is per round")
    void testShouldAccumulateTrajectoryDefault() {
        EvolutionRail rail = new EvolutionRail();

        assertFalse(readAccumulate(rail));
    }

    @Test
    @Tag("level0")
    @DisplayName("accumulation mode keeps builder across rounds")
    void testMultiRoundAccumulation() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        List<Trajectory> evolutionCalls = new ArrayList<>();
        EvolutionRail rail = new EvolutionRail(store, null, true, EvolutionTriggerPoint.NONE, false) {
            @Override
            protected void runEvolution(Trajectory trajectory, AgentCallbackContext ctx, Map<String, Object> snapshot) {
                evolutionCalls.add(trajectory);
            }
        };

        rail.beforeInvoke(ctx(AgentCallbackEvent.BEFORE_INVOKE,
                InvokeInputs.builder().query("q1").conversationId("conv_multi").build()));
        rail.afterModelCall(ctx(AgentCallbackEvent.AFTER_MODEL_CALL, ModelCallInputs.builder()
                .messages(List.of(Map.of("role", "user", "content", "q1")))
                .response(Map.of("role", "assistant", "content", "a1"))
                .build()));
        rail.afterInvoke(ctx(AgentCallbackEvent.AFTER_INVOKE,
                InvokeInputs.builder().query("q1").conversationId("conv_multi").build()));

        assertNotNull(rail.getBuilder());
        assertEquals(0, evolutionCalls.size());
        assertEquals(1, store.query(null, Map.of()).size());

        rail.beforeInvoke(ctx(AgentCallbackEvent.BEFORE_INVOKE,
                InvokeInputs.builder().query("q2").conversationId("conv_multi").build()));
        assertEquals(1, rail.getBuilder().getSteps().size());
        rail.afterModelCall(ctx(AgentCallbackEvent.AFTER_MODEL_CALL, ModelCallInputs.builder()
                .messages(List.of(Map.of("role", "user", "content", "q2")))
                .response(Map.of("role", "assistant", "content", "a2"))
                .build()));
        Trajectory trajectory = rail.buildTrajectory();
        rail.saveTrajectory(trajectory);
        rail.triggerEvolution(trajectory, ctx(AgentCallbackEvent.AFTER_TOOL_CALL,
                ToolCallInputs.builder().toolName("noop").build()));

        assertEquals(2, trajectory.getSteps().size());
        assertEquals(1, evolutionCalls.size());
        assertEquals(2, store.query(null, Map.of()).size());
    }

    @Test
    @Tag("level0")
    @DisplayName("per round mode resets builder after invoke")
    void testPerRoundModeResetsBuilder() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        List<Trajectory> evolutionCalls = new ArrayList<>();
        EvolutionRail rail = new EvolutionRail(store, null, false, EvolutionTriggerPoint.AFTER_INVOKE, false) {
            @Override
            protected void runEvolution(Trajectory trajectory, AgentCallbackContext ctx, Map<String, Object> snapshot) {
                evolutionCalls.add(trajectory);
            }
        };

        rail.beforeInvoke(ctx(AgentCallbackEvent.BEFORE_INVOKE,
                InvokeInputs.builder().query("q1").conversationId("conv_single").build()));
        rail.afterModelCall(ctx(AgentCallbackEvent.AFTER_MODEL_CALL, ModelCallInputs.builder()
                .messages(List.of(Map.of("role", "user", "content", "q1")))
                .response(Map.of("role", "assistant", "content", "a1"))
                .build()));
        rail.afterInvoke(ctx(AgentCallbackEvent.AFTER_INVOKE,
                InvokeInputs.builder().query("q1").conversationId("conv_single").build()));

        assertNull(rail.getBuilder());
        assertEquals(1, evolutionCalls.size());
        assertEquals(1, store.query(null, Map.of()).size());
    }

    @Test
    @Tag("level0")
    @DisplayName("trajectory rail collects only")
    void testTrajectoryRailCollectsOnly() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        TrajectoryRail rail = new TrajectoryRail(store);

        rail.beforeInvoke(ctx(AgentCallbackEvent.BEFORE_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_789").build()));
        rail.afterModelCall(ctx(AgentCallbackEvent.AFTER_MODEL_CALL, ModelCallInputs.builder()
                .messages(List.of(Map.of("role", "user", "content", "test")))
                .response(Map.of("role", "assistant", "content", "ok"))
                .build()));
        rail.afterInvoke(ctx(AgentCallbackEvent.AFTER_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_789").build()));

        List<Trajectory> trajectories = store.queryBySessionId("conv_789");
        assertEquals(1, trajectories.size());
        assertEquals("conv_789", trajectories.get(0).getSessionId());
    }

    @Test
    @Tag("level0")
    @DisplayName("trajectory rail priority is 10")
    void testPriority() {
        assertEquals(10, new TrajectoryRail().getPriority());
    }

    @Test
    @Tag("level0")
    @DisplayName("trajectory rail inherits evolution rail")
    void testInheritsEvolutionRail() {
        assertTrue(new TrajectoryRail() instanceof EvolutionRail);
    }

    @Test
    @Tag("level0")
    @DisplayName("custom evolution receives collected trajectory")
    void testCustomEvolutionReceivesTrajectory() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        List<Trajectory> calls = new ArrayList<>();
        EvolutionRail rail = new EvolutionRail(store, null, false, EvolutionTriggerPoint.AFTER_INVOKE, false) {
            @Override
            protected void runEvolution(Trajectory trajectory, AgentCallbackContext ctx, Map<String, Object> snapshot) {
                calls.add(trajectory);
            }
        };

        rail.beforeInvoke(ctx(AgentCallbackEvent.BEFORE_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_custom").build()));
        rail.afterModelCall(ctx(AgentCallbackEvent.AFTER_MODEL_CALL, ModelCallInputs.builder()
                .messages(List.of(Map.of("role", "user", "content", "evolve me")))
                .response(Map.of("role", "assistant", "content", "done"))
                .build()));
        rail.afterInvoke(ctx(AgentCallbackEvent.AFTER_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_custom").build()));

        assertEquals(1, calls.size());
        assertEquals("conv_custom", calls.get(0).getSessionId());
        assertEquals(1, calls.get(0).getSteps().size());
    }

    @Test
    @Tag("level0")
    @DisplayName("sync evolution passes active ctx")
    void testSyncEvolutionModePassesActiveCtx() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        List<Object[]> args = new ArrayList<>();
        EvolutionRail rail = new EvolutionRail(store, null, false, EvolutionTriggerPoint.AFTER_INVOKE, false) {
            @Override
            protected void runEvolution(Trajectory trajectory, AgentCallbackContext ctx, Map<String, Object> snapshot) {
                args.add(new Object[]{trajectory, ctx, snapshot});
            }
        };

        rail.beforeInvoke(ctx(AgentCallbackEvent.BEFORE_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_sync").build()));
        AgentCallbackContext endCtx = ctx(AgentCallbackEvent.AFTER_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_sync").build());
        rail.afterInvoke(endCtx);

        assertEquals(1, args.size());
        assertNotNull(args.get(0)[1]);
        assertNull(args.get(0)[2]);
    }

    @Test
    @Tag("level0")
    @DisplayName("async evolution passes snapshot and null ctx")
    void testAsyncEvolutionModePassesNoneCtxAndSnapshot() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        List<Object[]> args = new ArrayList<>();
        EvolutionRail rail = new EvolutionRail(store, null, false, EvolutionTriggerPoint.AFTER_INVOKE, true) {
            @Override
            protected void runEvolution(Trajectory trajectory, AgentCallbackContext ctx, Map<String, Object> snapshot) {
                args.add(new Object[]{trajectory, ctx, snapshot});
            }
        };

        rail.beforeInvoke(ctx(AgentCallbackEvent.BEFORE_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_async").build()));
        rail.afterInvoke(ctx(AgentCallbackEvent.AFTER_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_async").build()));
        rail.waitForBackgroundTasks(Duration.ofSeconds(5));

        assertEquals(1, args.size());
        assertNull(args.get(0)[1]);
        assertNotNull(args.get(0)[2]);
        assertTrue(((Map<?, ?>) args.get(0)[2]).containsKey("trajectory"));
    }

    @Test
    @Tag("level0")
    @DisplayName("snapshot contains parsed messages by default")
    void testSnapshotForEvolutionDefaultReturnsTrajectory() {
        class SnapshotRail extends EvolutionRail {
            Map<String, Object> exposeSnapshot(Trajectory trajectory, AgentCallbackContext ctx) {
                return snapshotForEvolution(trajectory, ctx);
            }
        }
        SnapshotRail rail = new SnapshotRail();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(new StubAgent("test_agent"))
                .event(AgentCallbackEvent.AFTER_INVOKE)
                .inputs(InvokeInputs.builder().query("test").conversationId("conv_snap").build())
                .context(new StubModelContext())
                .extra(new HashMap<>())
                .build();

        Map<String, Object> snapshot = rail.exposeSnapshot(
                new Trajectory("test", List.of(), "online", null, "test", null, null, null, null), ctx);

        assertNotNull(snapshot);
        assertTrue(snapshot.containsKey("trajectory"));
        assertEquals(List.of(Map.of("role", "user", "content", "improve the workflow")),
                snapshot.get("parsed_messages"));
    }

    @Test
    @Tag("level0")
    @DisplayName("safeRunEvolution catches exceptions")
    void testSafeRunEvolutionCatchesExceptions() {
        EvolutionRail rail = new EvolutionRail() {
            @Override
            protected void runEvolution(Trajectory trajectory, AgentCallbackContext ctx, Map<String, Object> snapshot) {
                throw new RuntimeException("evolution failed");
            }
        };

        assertDoesNotThrow(() -> rail.safeRunEvolution(Map.of(
                "trajectory", new Trajectory("test", List.of(), "online", null, "test", null, null, null, null))));
        assertEquals(List.of(Map.of("status", "failed", "message", "evolution failed")),
                rail.drainEvolutionOutcomes());
    }

    @Test
    @Tag("level0")
    @DisplayName("safeRunEvolution records failed outcome")
    void testSafeRunEvolutionRecordsFailureOutcome() {
        EvolutionRail rail = new EvolutionRail() {
            @Override
            protected void runEvolution(Trajectory trajectory, AgentCallbackContext ctx, Map<String, Object> snapshot) {
                throw new RuntimeException("evolution failed");
            }
        };

        rail.safeRunEvolution(Map.of(
                "trajectory", new Trajectory("test", List.of(), "online", null, "test", null, null, null, null)));

        assertEquals(List.of(Map.of("status", "failed", "message", "evolution failed")),
                rail.drainEvolutionOutcomes());
    }

    @Test
    @Tag("level0")
    @DisplayName("safeRunEvolution respects total timeout hook")
    void testSafeRunEvolutionRespectsTotalTimeoutHook() {
        EvolutionRail rail = new EvolutionRail() {
            private boolean completed;

            @Override
            protected Double getEvolutionTotalTimeoutSecs() {
                return 0.01;
            }

            @Override
            protected void runEvolution(Trajectory trajectory, AgentCallbackContext ctx, Map<String, Object> snapshot) {
                try {
                    Thread.sleep(50);
                    completed = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        rail.safeRunEvolution(Map.of(
                "trajectory", new Trajectory("test", List.of(), "online", null, "test", null, null, null, null)));

        assertEquals(List.of(Map.of("status", "timed_out",
                "message", "background evolution timed out after 0.01s")), rail.drainEvolutionOutcomes());
    }

    @Test
    @Tag("level0")
    @DisplayName("drainPendingApprovalEvents waits for background tasks")
    void testDrainWaitsForBackgroundTasks() {
        EvolutionRail rail = new EvolutionRail(new InMemoryTrajectoryStore(), null, false,
                EvolutionTriggerPoint.AFTER_INVOKE, true) {
            @Override
            protected void runEvolution(Trajectory trajectory, AgentCallbackContext ctx, Map<String, Object> snapshot) {
                getPendingApprovalEvents().add(new OutputSchema("test", 0, Map.of()));
            }
        };

        rail.beforeInvoke(ctx(AgentCallbackEvent.BEFORE_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_drain").build()));
        rail.afterInvoke(ctx(AgentCallbackEvent.AFTER_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_drain").build()));

        List<OutputSchema> events = rail.drainPendingApprovalEvents(true, Duration.ofSeconds(5));
        assertFalse(events.isEmpty());
    }

    @Test
    @Tag("level0")
    @DisplayName("drainPendingApprovalEvents defaults to empty list")
    void testDrainPendingApprovalEventsDefault() {
        assertEquals(List.of(), new EvolutionRail().drainPendingApprovalEvents());
    }

    @Test
    @Tag("level0")
    @DisplayName("cleanupBackgroundTasks prunes completed tasks")
    void testCleanupBackgroundTasks() {
        EvolutionRail rail = new EvolutionRail(new InMemoryTrajectoryStore(), null, false,
                EvolutionTriggerPoint.AFTER_INVOKE, true);

        rail.beforeInvoke(ctx(AgentCallbackEvent.BEFORE_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_cleanup").build()));
        rail.afterInvoke(ctx(AgentCallbackEvent.AFTER_INVOKE,
                InvokeInputs.builder().query("test").conversationId("conv_cleanup").build()));
        rail.waitForBackgroundTasks(Duration.ofSeconds(5));
        rail.cleanupBackgroundTasks();

        assertDoesNotThrow(() -> rail.cleanupBackgroundTasks());
    }

    @Test
    @Tag("level0")
    @DisplayName("successful evolution leaves no buffered outcomes")
    void testSafeRunEvolutionDoesNotBufferCompletedOutcomes() {
        EvolutionRail rail = new EvolutionRail() {
            @Override
            protected void runEvolution(Trajectory trajectory, AgentCallbackContext ctx, Map<String, Object> snapshot) {
            }
        };

        rail.safeRunEvolution(Map.of(
                "trajectory", new Trajectory("test", List.of(), "online", null, "test", null, null, null, null)));

        assertEquals(List.of(), rail.drainEvolutionOutcomes());
    }

    @Test
    @Tag("level0")
    @DisplayName("failure outcomes stay bounded")
    void testSafeRunEvolutionLimitsBufferedFailureOutcomes() {
        EvolutionRail rail = new EvolutionRail() {
            @Override
            protected void runEvolution(Trajectory trajectory, AgentCallbackContext ctx, Map<String, Object> snapshot) {
                throw new RuntimeException("failed-" + trajectory.getExecutionId());
            }
        };

        for (int index = 0; index < 37; index++) {
            rail.safeRunEvolution(Map.of(
                    "trajectory", new Trajectory(String.valueOf(index), List.of(), "online", null, "test",
                            null, null, null, null)));
        }

        List<Map<String, String>> outcomes = rail.drainEvolutionOutcomes();
        assertEquals(32, outcomes.size());
        assertEquals("failed-5", outcomes.get(0).get("message"));
        assertEquals("failed-36", outcomes.get(outcomes.size() - 1).get("message"));
    }

    private static boolean readAccumulate(EvolutionRail rail) {
        rail.beforeInvoke(ctx(AgentCallbackEvent.BEFORE_INVOKE,
                InvokeInputs.builder().query("probe").conversationId("probe").build()));
        boolean result = rail.getBuilder() != null;
        rail.afterInvoke(ctx(AgentCallbackEvent.AFTER_INVOKE,
                InvokeInputs.builder().query("probe").conversationId("probe").build()));
        return rail.getBuilder() != null && result;
    }

    private static AgentCallbackContext ctx(AgentCallbackEvent event, Object inputs) {
        return AgentCallbackContext.builder()
                .agent(new StubAgent("test_agent"))
                .event(event)
                .inputs((com.openjiuwen.core.singleagent.rail.EventInputs) inputs)
                .extra(new HashMap<>())
                .build();
    }

    static final class StubAgent {
        private final AgentCard card;

        StubAgent(String id) {
            this.card = AgentCard.builder().id(id).name(id).description("test").build();
        }

        public AgentCard getCard() {
            return card;
        }
    }

    static final class StubModelContext extends ModelContext {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
            return List.of(new UserMessage("improve the workflow"));
        }

        @Override
        public void setMessages(List<BaseMessage> messages, boolean withHistory) {
        }

        @Override
        public List<BaseMessage> popMessages(int size, boolean withHistory) {
            return List.of();
        }

        @Override
        public void clearMessages(boolean withHistory) {
        }

        @Override
        public List<BaseMessage> addMessages(List<BaseMessage> messages) {
            return messages;
        }

        @Override
        public ContextWindow getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools,
                                              Integer windowSize, Integer dialogueRound,
                                              Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public ContextStats statistic() {
            return null;
        }

        @Override
        public String sessionId() {
            return "session";
        }

        @Override
        public String contextId() {
            return "context";
        }

        @Override
        public TokenCounter tokenCounter() {
            return null;
        }

        @Override
        public Tool reloaderTool() {
            return null;
        }
    }
}
