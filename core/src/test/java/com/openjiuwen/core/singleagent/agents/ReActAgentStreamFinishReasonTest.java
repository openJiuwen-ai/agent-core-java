/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Verifies the streaming aggregation rebuild keeps {@code finish_reason},
 * mirroring Python's {@code react_agent.py} final message construction from
 * the accumulated chunk.
 */
class ReActAgentStreamFinishReasonTest {
    @Test
    void streamToolRoundAndFinalRoundCarryFinishReason() throws Exception {
        ReActAgent agent = newAgent("stream-fr-tool-round");
        agent.getAbilityManager().add(toolCard());

        List<AssistantMessage> modelResponses = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext context) {
                if (context.getInputs() instanceof ModelCallInputs inputs
                        && inputs.getResponse() instanceof AssistantMessage message) {
                    modelResponses.add(message);
                }
            }
        }).toCompletableFuture().join();

        Model model = mock(Model.class);
        when(model.stream(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(List.<AssistantMessageChunk>of(
                        AssistantMessageChunk.builder()
                                .toolCalls(List.of(ToolCall.builder()
                                        .id("call-1").name("add").arguments("{\"a\":1,\"b\":2}").build()))
                                .build(),
                        AssistantMessageChunk.builder().finishReason("tool_calls").build()).iterator())
                .thenReturn(List.<AssistantMessageChunk>of(
                        AssistantMessageChunk.builder().content("done").build(),
                        AssistantMessageChunk.builder().finishReason("stop").build()).iterator());
        agent.setLlm(model);

        AgentSessionApi session = new AgentSession("stream-fr-tool-round", null, agent.getCard());
        List<Object> collected = new ArrayList<>();
        var iterator = agent.stream(Map.of("query", "1+2"), session, List.of(StreamMode.OUTPUT));
        while (iterator.hasNext()) {
            collected.add(iterator.next());
        }

        assertThat(modelResponses).hasSize(2);
        AssistantMessage toolRound = modelResponses.get(0);
        assertThat(toolRound.getToolCalls()).isNotEmpty();
        assertThat(toolRound.getFinishReason()).isEqualTo("tool_calls");
        AssistantMessage finalRound = modelResponses.get(1);
        assertThat(String.valueOf(finalRound.getContent())).contains("done");
        assertThat(finalRound.getFinishReason()).isEqualTo("stop");
    }

    @Test
    void streamSingleChunkCarriesFinishReason() throws Exception {
        ReActAgent agent = newAgent("stream-fr-single");
        List<AssistantMessage> modelResponses = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext context) {
                if (context.getInputs() instanceof ModelCallInputs inputs
                        && inputs.getResponse() instanceof AssistantMessage message) {
                    modelResponses.add(message);
                }
            }
        }).toCompletableFuture().join();

        Model model = mock(Model.class);
        when(model.stream(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(List.<AssistantMessageChunk>of(
                        AssistantMessageChunk.builder()
                                .content("answer")
                                .finishReason("stop")
                                .build()).iterator());
        agent.setLlm(model);

        AgentSessionApi session = new AgentSession("stream-fr-single", null, agent.getCard());
        List<Object> collected = new ArrayList<>();
        var iterator = agent.stream(Map.of("query", "hi"), session, List.of(StreamMode.OUTPUT));
        while (iterator.hasNext()) {
            collected.add(iterator.next());
        }

        assertThat(modelResponses).hasSize(1);
        AssistantMessage message = modelResponses.get(0);
        assertThat(message.getFinishReason()).isEqualTo("stop");
    }

    private static ReActAgent newAgent(String id) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(id)
                .name(id)
                .description(id)
                .build());
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(3)
                .shouldFailTaskOnToolError(false)
                .streamRetryDelayMs(0)
                .build());
        return agent;
    }

    private static ToolCard toolCard() {
        return new ToolCard("add_id", "add", "Addition", Map.of(
                "type", "object",
                "properties", Map.of(
                        "a", Map.of("type", "number"),
                        "b", Map.of("type", "number")),
                "required", List.of("a", "b")));
    }
}
