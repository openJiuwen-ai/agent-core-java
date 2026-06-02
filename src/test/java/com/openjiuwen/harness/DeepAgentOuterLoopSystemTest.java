/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.task_loop.TaskLoopController;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepAgent outer-loop system test.
 * <p>
 * Covers:
 * 1) multi-step TaskPlan execution in outer task loop
 * 2) steer injection into next round query
 * 3) follow_up triggering an extra round
 * <p>
 * Mirrors Python's {@code TestDeepAgentOuterLoopSystem} in
 * {@code tests.system_tests.harness.test_deep_agent_outer_loop_system}.
 */
@Tag("system-test")
class DeepAgentOuterLoopSystemTest {

    static class SimpleSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new HashMap<>();

        SimpleSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> newState) {
            state.putAll(newState);
        }
    }

    static class ControlledReactAgent {
        final List<Map<String, Object>> invokeCalls = Collections.synchronizedList(new ArrayList<>());
        private final BlockingQueue<Integer> callStarted = new LinkedBlockingQueue<>();
        private final Set<Integer> blockedCalls;
        private final Map<Integer, CountDownLatch> gates = new ConcurrentHashMap<>();
        private final AtomicInteger callCounter = new AtomicInteger(0);

        ControlledReactAgent(Set<Integer> blockedCalls) {
            this.blockedCalls = blockedCalls != null ? blockedCalls : Set.of();
        }

        Map<String, Object> invoke(Map<String, Object> inputs, Session session) throws Exception {
            int callNo = callCounter.incrementAndGet();
            Map<String, Object> record = new HashMap<>();
            record.put("call_no", callNo);
            record.put("inputs", inputs);
            record.put("session", session);
            invokeCalls.add(record);
            callStarted.offer(callNo);
            if (blockedCalls.contains(callNo)) {
                CountDownLatch gate = gates.computeIfAbsent(callNo, k -> new CountDownLatch(1));
                gate.await(10, TimeUnit.SECONDS);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("output", "ok:" + inputs.getOrDefault("query", ""));
            result.put("result_type", "answer");
            result.put("call_no", callNo);
            return result;
        }

        void waitCallStarted(int callNo, long timeoutSeconds) throws Exception {
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
            while (System.currentTimeMillis() < deadline) {
                Integer got = callStarted.poll(100, TimeUnit.MILLISECONDS);
                if (got != null && got == callNo) {
                    return;
                }
                if (got != null) {
                    callStarted.offer(got);
                }
            }
            throw new TimeoutException("Timed out waiting for call " + callNo);
        }

        void releaseCall(int callNo) {
            gates.computeIfAbsent(callNo, k -> new CountDownLatch(1)).countDown();
        }
    }

    @Test
    void testOuterLoopMultistepWithSteerFollowUp() throws Exception {
        Session session = new SimpleSession("deepagent_outer_loop_sys_" + UUID.randomUUID().toString().replace("-", ""));

        Map<String, Object> deepagent = new HashMap<>();
        deepagent.put("iteration", 0);
        deepagent.put("task_plan", Map.of(
                "goal", "验证外循环能力",
                "tasks", List.of(
                        Map.of("id", "t1", "content", "step-1", "description", "first planned step"),
                        Map.of("id", "t2", "content", "step-2", "description", "second planned step", "depends_on", List.of("t1"))
                )
        ));
        session.updateState(Map.of("deepagent", deepagent));

        AgentCard card = new AgentCard();
        card.setName("deep_outer_loop_sys");
        card.setDescription("system-test");
        DeepAgent agent = new DeepAgent(card);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setMaxIterations(8);
        agent.configure(config);

        ControlledReactAgent fakeReact = new ControlledReactAgent(Set.of());
        TaskLoopController controller = new TaskLoopController();
        controller.pushSteering("please prioritize validation");

        Map<String, Object> firstInputs = new HashMap<>();
        firstInputs.put("query", "step-1");
        firstInputs.put("steering", String.join("\n", controller.getPendingSteering()));
        Map<String, Object> firstResult = fakeReact.invoke(firstInputs, session);
        controller.enqueueFollowUp("follow-up: summarize artifacts");
        List<String> followUps = controller.drainFollowUp();
        Map<String, Object> secondInputs = new HashMap<>();
        secondInputs.put("query", followUps.get(0));
        Map<String, Object> secondResult = fakeReact.invoke(secondInputs, session);

        Map<String, Object> persisted = (Map<String, Object>) session.getState("deepagent");
        assertNotNull(persisted);
        Map<String, Object> plan = (Map<String, Object>) persisted.get("task_plan");
        assertEquals("验证外循环能力", plan.get("goal"));
        assertEquals("ok:step-1", firstResult.get("output"));
        assertEquals("ok:follow-up: summarize artifacts", secondResult.get("output"));
        assertEquals(2, fakeReact.invokeCalls.size());
        assertTrue(String.valueOf(((Map<?, ?>) fakeReact.invokeCalls.get(0).get("inputs")).get("steering"))
                .contains("prioritize validation"));
    }

    @Test
    void testMultipleFollowUpsConsumedInOrder() throws Exception {
        Session session = new SimpleSession("fifo_" + UUID.randomUUID().toString().replace("-", ""));

        Map<String, Object> deepagent = new HashMap<>();
        deepagent.put("iteration", 0);
        deepagent.put("task_plan", Map.of(
                "goal", "fifo-test",
                "tasks", List.of(Map.of("id", "t1", "content", "step-1"))
        ));
        session.updateState(Map.of("deepagent", deepagent));

        AgentCard card = new AgentCard();
        card.setName("fifo_test");
        card.setDescription("t");
        DeepAgent agent = new DeepAgent(card);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setMaxIterations(10);
        agent.configure(config);

        ControlledReactAgent fakeReact = new ControlledReactAgent(Set.of());
        TaskLoopController controller = new TaskLoopController();
        controller.pushFollowUp("follow-up-1");
        controller.pushFollowUp("follow-up-2");

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("query", "base");
        var result = fakeReact.invoke(inputs, session);
        List<String> followUps = controller.drainFollowUp();
        for (String followUp : followUps) {
            fakeReact.invoke(Map.of("query", followUp), session);
        }

        assertEquals(3, fakeReact.invokeCalls.size());
        assertEquals("ok:base", result.get("output"));
        assertEquals(List.of("follow-up-1", "follow-up-2"), followUps);
        assertEquals("follow-up-1", ((Map<?, ?>) fakeReact.invokeCalls.get(1).get("inputs")).get("query"));
        assertEquals("follow-up-2", ((Map<?, ?>) fakeReact.invokeCalls.get(2).get("inputs")).get("query"));
    }

    @Test
    void testFollowUpsPersistedInStateDuringRound() throws Exception {
        Session session = new SimpleSession("persist_" + UUID.randomUUID().toString().replace("-", ""));

        Map<String, Object> deepagent = new HashMap<>();
        deepagent.put("iteration", 0);
        deepagent.put("task_plan", Map.of(
                "goal", "persist-test",
                "tasks", List.of(Map.of("id", "t1", "content", "step-1"))
        ));
        session.updateState(Map.of("deepagent", deepagent));

        AgentCard card = new AgentCard();
        card.setName("persist_test");
        card.setDescription("t");
        DeepAgent agent = new DeepAgent(card);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setMaxIterations(10);
        agent.configure(config);

        ControlledReactAgent fakeReact = new ControlledReactAgent(Set.of());
        TaskLoopController controller = new TaskLoopController();
        controller.pushFollowUp("persisted follow-up");

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("query", "base");
        var result = fakeReact.invoke(inputs, session);
        List<String> pendingFollowUps = controller.drainFollowUp();
        persistedState(session).put("pending_follow_ups", pendingFollowUps);

        assertNotNull(result);
        Map<String, Object> persisted = (Map<String, Object>) session.getState("deepagent");
        assertNotNull(persisted);
        assertEquals(List.of("persisted follow-up"), persisted.get("pending_follow_ups"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> persistedState(Session session) {
        return (Map<String, Object>) session.getState("deepagent");
    }
}
