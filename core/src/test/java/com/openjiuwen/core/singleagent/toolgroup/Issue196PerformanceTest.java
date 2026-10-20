/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.toolgroup.ToolGroup;
import com.openjiuwen.core.singleagent.BaseAgent;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag("issue196-w11")
@Tag("TC-PRF-01")
@Tag("TC-PRF-02")
@Tag("TC-PRF-03")
class Issue196PerformanceTest {

    private static final int TOOL_COUNT = 50;

    @Test
    @Tag("TC-PRF-02")
    void highFrequencyToggle_maintainsStableState() {
        PerfAgent agent = new PerfAgent();
        PerfSession session = new PerfSession("perf-session");
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

        for (int round = 0; round < 100; round++) {
            agent.activateToolGroup("group_a", session);
            List<String> visibleA =
                    agent.listEffectiveToolInfo(session).stream().map(ToolInfo::getName).toList();
            assertThat(visibleA).hasSize(45).contains("tool_0", "tool_4");
            assertThat(visibleA).doesNotContain("tool_5");

            agent.activateToolGroup("group_b", session);
            List<String> visibleAB =
                    agent.listEffectiveToolInfo(session).stream().map(ToolInfo::getName).toList();
            assertThat(visibleAB).hasSize(50).contains("tool_0", "tool_9");

            agent.deactivateToolGroup("group_a", session);
            agent.deactivateToolGroup("group_b", session);
            List<String> visibleNone =
                    agent.listEffectiveToolInfo(session).stream().map(ToolInfo::getName).toList();
            assertThat(visibleNone).hasSize(40);
            assertThat(visibleNone).doesNotContain("tool_0", "tool_9");
        }
    }

    @Test
    @Tag("TC-PRF-03")
    void groupedFiltering_reducesToolSchemaCount() {
        PerfAgent unfilteredAgent = new PerfAgent();
        PerfSession fullSession = new PerfSession("full-session");
        for (int i = 0; i < TOOL_COUNT; i++) {
            registerGlobalTool(unfilteredAgent, "tool_" + i);
        }

        PerfAgent filteredAgent = new PerfAgent();
        PerfSession filteredSession = new PerfSession("filtered-session");
        for (int i = 0; i < TOOL_COUNT; i++) {
            registerGlobalTool(filteredAgent, "tool_" + i);
        }
        filteredAgent.registerToolGroup(
                new ToolGroup(
                        "focused",
                        "Focused tools",
                        List.of("tool_0", "tool_1", "tool_2", "tool_3", "tool_4")));

        int fullCount = unfilteredAgent.listEffectiveToolInfo(fullSession).size();
        int filteredCount = filteredAgent.listEffectiveToolInfo(filteredSession).size();
        filteredAgent.activateToolGroup("focused", filteredSession);
        int activeCount = filteredAgent.listEffectiveToolInfo(filteredSession).size();

        assertThat(fullCount).isEqualTo(TOOL_COUNT);
        assertThat(filteredCount).isEqualTo(45);
        assertThat(activeCount).isEqualTo(TOOL_COUNT);
        assertThat(filteredCount).isLessThan(fullCount);
    }

    @Test
    void groupedFiltering_reducesEstimatedTokenUsage() {
        PerfAgent unfilteredAgent = new PerfAgent();
        PerfSession fullSession = new PerfSession("full-token-session");
        for (int i = 0; i < TOOL_COUNT; i++) {
            registerGlobalTool(unfilteredAgent, "tool_" + i);
        }

        PerfAgent filteredAgent = new PerfAgent();
        PerfSession filteredSession = new PerfSession("filtered-token-session");
        for (int i = 0; i < TOOL_COUNT; i++) {
            registerGlobalTool(filteredAgent, "tool_" + i);
        }
        filteredAgent.registerToolGroup(
                new ToolGroup(
                        "focused",
                        "Focused tools",
                        List.of("tool_0", "tool_1", "tool_2", "tool_3", "tool_4")));

        int fullTokens = estimateTokens(unfilteredAgent.listEffectiveToolInfo(fullSession));
        int filteredTokens = estimateTokens(filteredAgent.listEffectiveToolInfo(filteredSession));

        assertThat(fullTokens).isGreaterThan(0);
        assertThat(filteredTokens).isLessThan(fullTokens);
        BigDecimal reduction =
                BigDecimal.valueOf(100)
                        .multiply(
                                BigDecimal.ONE.subtract(
                                        BigDecimal.valueOf(filteredTokens)
                                                .divide(
                                                        BigDecimal.valueOf(fullTokens),
                                                        6,
                                                        RoundingMode.HALF_UP)));
        System.out.printf(
                "[TC-PRF-03] Estimated tokens: full=%d filtered=%d reduction=%.0f%%%n",
                fullTokens, filteredTokens, reduction.doubleValue());
    }

    private static int estimateTokens(List<ToolInfo> tools) {
        int tokens = 0;
        for (ToolInfo tool : tools) {
            tokens +=
                    (tool.getName().length()
                                    + tool.getDescription().length()
                                    + String.valueOf(tool.getParameters()).length())
                            / 4;
        }
        return tokens;
    }

    @Test
    void noGroupsRegistered_noOverhead() {
        PerfAgent agent = new PerfAgent();
        PerfSession session = new PerfSession("bare-session");
        for (int i = 0; i < TOOL_COUNT; i++) {
            registerGlobalTool(agent, "tool_" + i);
        }

        long startNanos = System.nanoTime();
        List<ToolInfo> tools = agent.listEffectiveToolInfo(session);
        long elapsedMicros = (System.nanoTime() - startNanos) / 1000;

        assertThat(tools).hasSize(TOOL_COUNT);
        assertThat(elapsedMicros).isLessThan(50_000);
    }

    private static void registerGlobalTool(PerfAgent agent, String name) {
        LocalFunction tool =
                new LocalFunction(
                        ToolCard.builder()
                                .id(name)
                                .name(name)
                                .description("Tool " + name + " for perf testing.")
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

    private static final class PerfAgent extends BaseAgent {
        private PerfAgent() {
            super(new com.openjiuwen.core.singleagent.schema.AgentCard("perf", "perf", "test"));
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

    private static final class PerfSession implements com.openjiuwen.core.session.AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private PerfSession(String sessionId) {
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
