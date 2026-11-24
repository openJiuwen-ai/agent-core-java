/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Verifies that stream answer frames expose the agent termination reason.
 */
class ReActAgentStreamTerminationReasonTest {

    @Test
    void answerFrameKeepsLegacyKeySet() {
        ReActAgent agent = newAgent();
        agent.setLlm(textModel("done"));

        Map<String, Object> payload = answerPayload(drainStream(agent, "stream-text-termination"));

        assertThat(payload).containsEntry("result_type", "answer");
        assertThat(payload).doesNotContainKey("termination_reason");
    }

    @Test
    void maxIterationsErrorFrameCarriesTerminationReason() {
        ReActAgent agent = newAgent();
        agent.configure(ReActAgentConfig.builder().maxIterations(1).shouldFailTaskOnToolError(false).build());
        agent.setLlm(toolCallModel("missing-tool"));

        Map<String, Object> payload = answerPayload(drainStream(agent, "stream-max-iterations"));

        assertThat(payload).containsEntry("result_type", "error");
        assertThat(payload).containsEntry("termination_reason", "MAX_ITERATIONS");
    }

    @Test
    void toolFailureFrameCarriesOriginalCallAndError() {
        ReActAgent agent = newAgent();
        agent.setLlm(toolCallModel("missing-tool"));

        Map<String, Object> payload = answerPayload(drainStream(agent, "stream-tool-error"));

        assertThat(payload).containsEntry("result_type", "error");
        assertThat(payload.get("output")).isInstanceOf(String.class);
        assertThat(payload.get("tool_outcomes")).isEqualTo(List.of(Map.of(
                "tool_name", "missing-tool", "tool_call_id", "call-missing-tool",
                "status", "failed", "error", payload.get("output"))));
    }

    @Test
    void modelErrorFrameCarriesTerminationReason() {
        ReActAgent agent = newAgent();
        agent.setLlm(failingModel("model unavailable"));

        Map<String, Object> payload = answerPayload(drainStream(agent, "stream-model-error"));

        assertThat(payload).containsEntry("result_type", "error");
        assertThat(payload).containsEntry("termination_reason", "MODEL_ERROR");
        assertThat(payload).doesNotContainKey("tool_outcomes");
    }

    private static ReActAgent newAgent() {
        return new ReActAgent(AgentCard.builder().name("stream-termination-agent")
                .description("Stream termination reason agent").build());
    }

    private static List<Object> drainStream(ReActAgent agent, String sessionId) {
        List<Object> frames = new ArrayList<>();
        Iterator<Object> iterator = agent.stream(Map.of("query", "run"),
                new AgentSession(sessionId, null, null), List.of(StreamMode.OUTPUT));
        iterator.forEachRemaining(frames::add);
        return frames;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> answerPayload(List<Object> frames) {
        assertThat(frames).isNotEmpty();
        return frames.stream()
                .filter(frame -> frame instanceof OutputSchema output && "answer".equals(output.getType()))
                .map(frame -> (OutputSchema) frame)
                .reduce((first, second) -> second)
                .map(output -> (Map<String, Object>) output.getPayload())
                .orElseThrow(() -> new AssertionError("No answer frame in stream: " + frames));
    }

    private static Model textModel(String content) {
        return new Model(new Model.ModelClient() {
            @Override
            public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
                return CompletableFuture.completedFuture(new AssistantMessage(content));
            }

            @Override
            public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
                return List.of(AssistantMessageChunk.builder().content(content).build()).iterator();
            }
        });
    }

    private static Model toolCallModel(String toolName) {
        return new Model(new Model.ModelClient() {
            @Override
            public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
                return CompletableFuture.completedFuture(AssistantMessage.builder()
                        .toolCalls(List.of(ToolCall.builder().id("call-" + toolName)
                                .name(toolName).arguments("{}").build()))
                        .build());
            }

            @Override
            public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
                return List.<AssistantMessageChunk>of().iterator();
            }
        });
    }

    private static Model failingModel(String message) {
        return new Model(new Model.ModelClient() {
            @Override
            public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
                return CompletableFuture.failedFuture(new IllegalStateException(message));
            }

            @Override
            public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
                return List.<AssistantMessageChunk>of().iterator();
            }
        });
    }
}
