/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.toolgroup.ToolGroup;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.BaseAgent;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tests tool-group concurrency scaling.
 *
 * @since 0.1.17
 */
class TCPRF04ConcurrencyScalingTest {
    private static final int TOOL_COUNT = 50;

    @Test
    void c1_singleSession_baseline() throws Exception {
        runConcurrencyTest(1, 20);
    }

    @Test
    void c10_tenConcurrentSessions() throws Exception {
        runConcurrencyTest(10, 20);
    }

    @Test
    void c40_fortyConcurrentSessions() throws Exception {
        runConcurrencyTest(40, 10);
    }

    private void runConcurrencyTest(int concurrency, int roundsPerSession) throws Exception {
        TestAgent agent = new TestAgent();
        for (int i = 0; i < TOOL_COUNT; i++) {
            registerGlobalTool(agent, "tool_" + i);
        }
        agent.registerToolGroup(
                new ToolGroup(
                        "group_a",
                        "Group A",
                        List.of("tool_0", "tool_1", "tool_2", "tool_3", "tool_4")));
        agent.registerToolGroup(
                new ToolGroup(
                        "group_b",
                        "Group B",
                        List.of("tool_5", "tool_6", "tool_7", "tool_8", "tool_9")));
        AtomicInteger failures = new AtomicInteger();
        AtomicLong totalNanos = new AtomicLong();

        SessionConcurrencyHarness.runInParallel(
                concurrency,
                index -> new TestSession("session-" + index),
                (index, session) ->
                        runSessionRounds(
                                agent,
                                index,
                                session,
                                roundsPerSession,
                                failures,
                                totalNanos));

        long avgMicros = totalNanos.get() / (concurrency * roundsPerSession * 2) / 1000;
        System.out.printf(
                "[ConcScalability] c%d × %d rounds: avg %d µs per toggle+filter, failures=%d%n",
                concurrency, roundsPerSession, avgMicros, failures.get());
        assertThat(failures.get()).isZero();
    }

    private static void runSessionRounds(
            TestAgent agent,
            int index,
            AgentSessionApi session,
            int roundsPerSession,
            AtomicInteger failures,
            AtomicLong totalNanos) {
        boolean shouldActivateA = index % 2 == 0;
        for (int round = 0; round < roundsPerSession; round++) {
            long start = System.nanoTime();
            activateSessionGroup(agent, session, shouldActivateA);
            List<String> visible = visibleToolNames(agent, session);
            totalNanos.addAndGet(System.nanoTime() - start);
            if (hasUnexpectedVisibleTools(visible, shouldActivateA)) {
                failures.incrementAndGet();
            }
            deactivateSessionGroup(agent, session, shouldActivateA);
            if (visibleToolNames(agent, session).size() != 40) {
                failures.incrementAndGet();
            }
        }
    }

    private static void activateSessionGroup(
            TestAgent agent, AgentSessionApi session, boolean shouldActivateA) {
        agent.activateToolGroup(shouldActivateA ? "group_a" : "group_b", session);
    }

    private static List<String> visibleToolNames(TestAgent agent, AgentSessionApi session) {
        return agent.listEffectiveToolInfo(session).stream()
                .map(ToolInfo::getName)
                .toList();
    }

    private static boolean hasUnexpectedVisibleTools(
            List<String> visible, boolean shouldActivateA) {
        String activeTool = shouldActivateA ? "tool_0" : "tool_5";
        String inactiveTool = shouldActivateA ? "tool_5" : "tool_0";
        return visible.size() != 45
                || !visible.contains(activeTool)
                || visible.contains(inactiveTool);
    }

    private static void deactivateSessionGroup(
            TestAgent agent, AgentSessionApi session, boolean shouldActivateA) {
        agent.deactivateToolGroup(shouldActivateA ? "group_a" : "group_b", session);
    }

    private static void registerGlobalTool(TestAgent agent, String name) {
        LocalFunction tool =
                new LocalFunction(
                        ToolCard.builder()
                                .id(name)
                                .name(name)
                                .description(name)
                                .inputParams(
                                        Map.of(
                                                "type",
                                                "object",
                                                "properties",
                                                Map.of(),
                                                "required",
                                                List.of()))
                                .build(),
                        inputs -> Map.of());
        agent.getAbilityManager().addAbility(tool.getCard(), tool);
    }

    private static final class TestAgent extends BaseAgent {
        private TestAgent() {
            super(new com.openjiuwen.core.singleagent.schema.AgentCard("conc", "conc", "test"));
        }

        @Override
        public BaseAgent configure(Object config) {
            setConfig(config);
            return this;
        }

        @Override
        public Object invoke(Object inputs, com.openjiuwen.core.session.AgentSessionApi session) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Iterator<Object> stream(
                Object inputs,
                com.openjiuwen.core.session.AgentSessionApi session,
                List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
            return List.of().iterator();
        }
    }

    private static final class TestSession implements com.openjiuwen.core.session.AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private TestSession(String sessionId) {
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
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {}

        @Override
        public Iterator<Object> streamIterator() {
            return List.of().iterator();
        }
    }
}
