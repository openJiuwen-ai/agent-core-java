/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.toolgroup.ToolGroup;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Tests ReAct tool-group exposure.
 *
 * @since 0.1.17
 */
class ReActGroupToolsCaptureTest {
    private static final int TOOL_COUNT = 8;

    @Test
    void reactRequestExcludesInactiveGroupsAndReducesToolPayload() {
        ReActAgent fullAgent = createAgent("react-full");
        ReActAgent filteredAgent = createAgent("react-filtered");
        registerTools(fullAgent, 0, TOOL_COUNT);
        registerTools(filteredAgent, 0, TOOL_COUNT / 2);

        filteredAgent.registerToolGroup(
                new ToolGroup(
                        "focused",
                        "Focused tools",
                        List.of("tool_0", "tool_1", "tool_2", "tool_3")));

        CapturingModelClient fullClient = new CapturingModelClient();
        CapturingModelClient filteredClient = new CapturingModelClient();
        ModelToolsCaptureRail fullRail = new ModelToolsCaptureRail();
        ModelToolsCaptureRail filteredRail = new ModelToolsCaptureRail();
        fullAgent.setLlm(new Model(fullClient));
        filteredAgent.setLlm(new Model(filteredClient));
        fullAgent.registerRail(fullRail).toCompletableFuture().join();
        filteredAgent.registerRail(filteredRail).toCompletableFuture().join();

        TestSession fullSession = new TestSession("full");
        TestSession filteredSession = new TestSession("filtered");
        filteredAgent.activateToolGroup("focused", filteredSession);
        assertThat(filteredAgent.getActiveToolGroupNames(filteredSession))
                .containsExactly("focused");

        fullAgent.invoke(Map.of("query", "hi"), fullSession);
        filteredAgent.invoke(Map.of("query", "hi"), filteredSession);

        List<String> fullNames = capturedToolNames(fullRail);
        List<String> filteredNames = capturedToolNames(filteredRail);
        int fullPayload = capturedPayloadTokens(fullClient);
        int filteredPayload = capturedPayloadTokens(filteredClient);

        assertThat(fullClient.invokeCount()).isEqualTo(1);
        assertThat(filteredClient.invokeCount()).isEqualTo(1);
        assertThat(fullNames).hasSize(TOOL_COUNT);
        assertThat(filteredNames)
                .hasSize(TOOL_COUNT / 2)
                .contains("tool_0", "tool_3")
                .doesNotContain("tool_4", "tool_7");
        assertThat(filteredPayload).isLessThan(fullPayload);
    }

    private static ReActAgent createAgent(String id) {
        ReActAgent agent = new ReActAgent(new AgentCard(id, id, "Issue 196 mock model test"));
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System {query}")));
        config.setMaxIterations(2);
        agent.configure(config);
        return agent;
    }

    private static void registerTools(ReActAgent agent, int fromInclusive, int toExclusive) {
        IntStream.range(fromInclusive, toExclusive)
                .forEach(
                        index -> {
                            String name = "tool_" + index;
                            LocalFunction tool =
                                    new LocalFunction(
                                            ToolCard.builder()
                                                    .id(name)
                                                    .name(name)
                                                    .description(
                                                            "Tool "
                                                                    + name
                                                                    + " for captured ReAct request"
                                                                    + " testing.")
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
                        });
    }

    private static List<String> capturedToolNames(ModelToolsCaptureRail rail) {
        return rail.lastCapturedToolList().stream()
                .map(ToolInfo.class::cast)
                .map(ToolInfo::getName)
                .toList();
    }

    private static int capturedPayloadTokens(CapturingModelClient client) {
        return String.valueOf(client.lastCapturedToolList()).length() / 4;
    }

    private static final class TestSession implements AgentSessionApi {
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
