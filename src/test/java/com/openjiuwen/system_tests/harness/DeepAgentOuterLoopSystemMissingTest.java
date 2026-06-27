/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness;

import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests.system_tests.harness.test_deep_agent_outer_loop_system}
 * in {@code tests/system_tests/harness/test_deep_agent_outer_loop_system.py}.
 */
class DeepAgentOuterLoopSystemMissingTest {

    @Test
    void testOuterLoopMultistepWithSteerFollowUp() throws Exception {
        AgentSession session = new AgentSession("deepagent_outer_loop_sys_" + uuid(), null, null);
        TaskPlan seeded = seedMultiStepPlan(session);
        ControlledReactAgent reactAgent = new ControlledReactAgent(Set.of(1, 2));
        DeepAgent agent = agent("deep_outer_loop_sys", 8, reactAgent);

        CompletableFuture<Map<String, Object>> invokeTask = agent.invoke(Map.of("query", "base query"), session);

        reactAgent.waitCallStarted(1);
        agent.steer("please format as bullet points", session).join();
        reactAgent.releaseCall(1);

        reactAgent.waitCallStarted(2);
        agent.followUp("continue with one more check", session).join();
        reactAgent.releaseCall(2);

        reactAgent.waitCallStarted(3);
        Map<String, Object> result = invokeTask.get(10, TimeUnit.SECONDS);

        assertThat(result).containsEntry("result_type", "answer");
        assertThat(reactAgent.invokeCalls()).hasSize(3);

        Map<String, Object> secondInputs = reactAgent.invokeCalls().get(1).inputs();
        assertThat(secondInputs.get("query")).asString().doesNotContain("[STEERING]");
        assertThat(secondInputs.get("_steering_queue")).isInstanceOf(Queue.class);
        @SuppressWarnings("unchecked")
        Queue<String> steering = (Queue<String>) secondInputs.get("_steering_queue");
        assertThat(steering).contains("please format as bullet points");

        TaskPlan persistedPlan = persistedPlan(session);
        assertThat(persistedPlan.getGoal()).isEqualTo(seeded.getGoal());
        assertThat(persistedPlan.getTasks()).hasSize(2);
        assertThat(persistedPlan.getTasks().get(0).getStatus()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(persistedPlan.getTasks().get(1).getStatus()).isEqualTo(TodoStatus.COMPLETED);
    }

    @Test
    void testMultipleFollowUpsConsumedInOrder() throws Exception {
        AgentSession session = new AgentSession("fifo_" + uuid(), null, null);
        seedSingleStepPlan(session, "fifo-test");
        ControlledReactAgent reactAgent = new ControlledReactAgent(Set.of(1));
        DeepAgent agent = agent("fifo_test", 10, reactAgent);

        CompletableFuture<Map<String, Object>> invokeTask = agent.invoke(Map.of("query", "base"), session);

        reactAgent.waitCallStarted(1);
        agent.followUp("first_fu", session).join();
        agent.followUp("second_fu", session).join();
        agent.followUp("third_fu", session).join();
        reactAgent.releaseCall(1);

        invokeTask.get(10, TimeUnit.SECONDS);

        assertThat(reactAgent.invokeCalls()).hasSize(4);
        assertThat(reactAgent.invokeCalls().get(1).inputs()).containsEntry("query", "first_fu");
        assertThat(reactAgent.invokeCalls().get(2).inputs()).containsEntry("query", "second_fu");
        assertThat(reactAgent.invokeCalls().get(3).inputs()).containsEntry("query", "third_fu");
    }

    @Test
    void testFollowUpsPersistedInStateDuringRound() throws Exception {
        AgentSession session = new AgentSession("persist_" + uuid(), null, null);
        seedSingleStepPlan(session, "persist-test");
        ControlledReactAgent reactAgent = new ControlledReactAgent(Set.of(1, 2));
        DeepAgent agent = agent("persist_test", 10, reactAgent);

        CompletableFuture<Map<String, Object>> invokeTask = agent.invoke(Map.of("query", "base"), session);

        reactAgent.waitCallStarted(1);
        agent.followUp("fu_alpha", session).join();
        agent.followUp("fu_beta", session).join();
        reactAgent.releaseCall(1);

        reactAgent.waitCallStarted(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> persisted = (Map<String, Object>) session.getState("deepagent");
        assertThat(persisted.get("pending_follow_ups")).isEqualTo(List.of("fu_beta"));

        reactAgent.releaseCall(2);
        invokeTask.get(10, TimeUnit.SECONDS);
    }

    private static DeepAgent agent(String name, int maxIterations, ControlledReactAgent reactAgent) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setEnableTaskLoop(true);
        config.setMaxIterations(maxIterations);

        DeepAgent agent = new DeepAgent(new AgentCard(name, name, "system-test"));
        agent.configure(config);
        agent.setReactAgent(reactAgent, true);
        return agent;
    }

    private static TaskPlan seedMultiStepPlan(AgentSession session) {
        TaskPlan plan = new TaskPlan(
                "verify outer loop",
                List.of(
                        new TodoItem("t1", "step-1", "", "first planned step", TodoStatus.PENDING,
                                List.of(), null, null, null),
                        new TodoItem("t2", "step-2", "", "second planned step", TodoStatus.PENDING,
                                List.of("t1"), null, null, null)
                )
        );
        session.updateState(Map.of("deepagent", Map.of("iteration", 0, "task_plan", plan.toMap())));
        return plan;
    }

    private static void seedSingleStepPlan(AgentSession session, String goal) {
        TaskPlan plan = new TaskPlan(goal, List.of(new TodoItem("t1", "step-1")));
        session.updateState(Map.of("deepagent", Map.of("iteration", 0, "task_plan", plan.toMap())));
    }

    @SuppressWarnings("unchecked")
    private static TaskPlan persistedPlan(AgentSession session) {
        Map<String, Object> persisted = (Map<String, Object>) session.getState("deepagent");
        return TaskPlan.fromMap((Map<String, Object>) persisted.get("task_plan"));
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record Invocation(int callNo, Map<String, Object> inputs, AgentSessionApi session) {
    }

    private static final class ControlledReactAgent {
        private final Set<Integer> blockedCalls;
        private final Map<Integer, CountDownLatch> callStarted = new ConcurrentHashMap<>();
        private final Map<Integer, CountDownLatch> gates = new ConcurrentHashMap<>();
        private final CopyOnWriteArrayList<Invocation> invokeCalls = new CopyOnWriteArrayList<>();

        private ControlledReactAgent(Set<Integer> blockedCalls) {
            this.blockedCalls = blockedCalls == null ? Set.of() : Set.copyOf(blockedCalls);
        }

        public CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            int callNo = invokeCalls.size() + 1;
            invokeCalls.add(new Invocation(callNo, new LinkedHashMap<>(inputs), session));
            callStarted.computeIfAbsent(callNo, ignored -> new CountDownLatch(1)).countDown();

            if (blockedCalls.contains(callNo)) {
                try {
                    gates.computeIfAbsent(callNo, ignored -> new CountDownLatch(1)).await(10, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }

            return CompletableFuture.completedFuture(Map.of(
                    "output", "ok:" + inputs.getOrDefault("query", ""),
                    "result_type", "answer",
                    "call_no", callNo
            ));
        }

        private void waitCallStarted(int callNo) throws InterruptedException {
            CountDownLatch latch = callStarted.computeIfAbsent(callNo, ignored -> new CountDownLatch(1));
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        }

        private void releaseCall(int callNo) {
            gates.computeIfAbsent(callNo, ignored -> new CountDownLatch(1)).countDown();
        }

        private List<Invocation> invokeCalls() {
            return invokeCalls;
        }
    }
}
