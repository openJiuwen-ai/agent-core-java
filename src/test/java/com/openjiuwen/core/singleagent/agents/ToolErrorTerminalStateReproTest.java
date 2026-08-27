/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies the tool-exception terminal-state contract: a tool that throws a
 * runtime exception fails the task by default, while the recoverable path
 * remains available when explicitly disabled.
 */
class ToolErrorTerminalStateReproTest {

    private static final class TestSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new HashMap<>();

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
        public void updateState(Map<String, Object> updates) {
            this.state.putAll(updates);
        }
    }

    private static LocalFunction newFailingTool(String toolName) {
        ToolCard card = ToolCard.builder()
                .id(toolName)
                .name(toolName)
                .description("always throws")
                .inputParams(Map.of("type", "object", "properties", Map.of()))
                .build();
        return new LocalFunction(card, inputs -> {
            throw new IllegalStateException("downstream service unavailable");
        });
    }

    private static void registerTool(LocalFunction tool) {
        Runner.resourceMgr().addTool(tool, null);
    }

    private static void removeTool(String toolId) {
        Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
    }

    private static ReActAgent newAgent(String tag) {
        return new ReActAgent(
                AgentCard.builder().id(tag).name(tag).description("repro").build());
    }

    @Test
    void toolExceptionFailsTaskByDefault() throws Exception {
        String toolName = "failing_tool_default";
        LocalFunction tool = newFailingTool(toolName);
        registerTool(tool);
        ReActAgent agent = newAgent("repro-default");
        agent.getAbilityManager().add(tool.getCard());
        TestSession session = new TestSession("repro-default-session");
        try {
            agent.configure(ReActAgentConfig.builder().maxIterations(2).build());
            Model model = mock(Model.class);
            ToolCall toolCall =
                    ToolCall.builder().id("call-default").name(toolName).arguments("{}").index(0).build();
            when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(
                            AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build());
            agent.setLlm(model);

            Object result = agent.invoke(Map.of("query", "call the failing tool"), session);

            assertThat(result).isInstanceOf(Map.class);
            Map<?, ?> resultMap = (Map<?, ?>) result;
            assertThat(resultMap.get("result_type")).isEqualTo("error");
            assertThat(resultMap.get("output").toString()).contains("downstream service unavailable");
            verify(model, times(1)).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        } finally {
            agent.getAgentCallbackManager().clear(null);
            removeTool(toolName);
        }
    }

    @Test
    void toolExceptionTreatedAsRecoverableWhenFailTaskOnToolErrorDisabled() throws Exception {
        String toolName = "failing_tool_optout";
        LocalFunction tool = newFailingTool(toolName);
        registerTool(tool);
        ReActAgent agent = newAgent("repro-optout");
        agent.getAbilityManager().add(tool.getCard());
        TestSession session = new TestSession("repro-optout-session");
        try {
            agent.configure(ReActAgentConfig.builder()
                    .maxIterations(2)
                    .shouldFailTaskOnToolError(false)
                    .build());
            Model model = mock(Model.class);
            ToolCall toolCall =
                    ToolCall.builder().id("call-optout").name(toolName).arguments("{}").index(0).build();
            when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(
                            AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build(),
                            AssistantMessage.builder().content("All done, task completed.").build());
            agent.setLlm(model);

            Object result = agent.invoke(Map.of("query", "call the failing tool"), session);

            assertThat(result).isInstanceOf(Map.class);
            Map<?, ?> resultMap = (Map<?, ?>) result;
            assertThat(resultMap.get("result_type")).isEqualTo("answer");
            assertThat(resultMap.get("output").toString()).contains("task completed");
        } finally {
            agent.getAgentCallbackManager().clear(null);
            removeTool(toolName);
        }
    }
}
