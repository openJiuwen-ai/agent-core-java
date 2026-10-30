/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies ReActAgent behavior when the model output hits the length limit:
 * truncated empty answers, finish_reason propagation, and the streaming
 * reasoning-only fallback.
 */
class ReActAgentLengthTruncationTest {

    private static final String QUERY = "write a long report";

    /**
     * Simulates a provider whose response is fully consumed by reasoning:
     * empty content, no tool calls, finish_reason="length".
     */
    private static final class LengthTruncatedClient implements Model.ModelClient {
        final AtomicInteger invokeCalls = new AtomicInteger();
        final AtomicInteger streamCalls = new AtomicInteger();

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeCalls.incrementAndGet();
            AssistantMessage message = AssistantMessage.builder()
                    .content("")
                    .reasoningContent("thinking".repeat(50))
                    .finishReason("length")
                    .toolCalls(List.of())
                    .build();
            return CompletableFuture.completedFuture(message);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            streamCalls.incrementAndGet();
            AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                    .content("")
                    .reasoningContent("thinking".repeat(50))
                    .finishReason("length")
                    .build();
            return List.of(chunk).iterator();
        }
    }

    /**
     * Streams body content before truncation so the streaming result is non-empty
     * and the final message assembly runs without the reasoning-only fallback.
     */
    private static final class ContentTruncatedClient implements Model.ModelClient {
        final AtomicInteger invokeCalls = new AtomicInteger();
        final AtomicInteger streamCalls = new AtomicInteger();

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeCalls.incrementAndGet();
            AssistantMessage message = AssistantMessage.builder()
                    .content("partial body")
                    .finishReason("length")
                    .toolCalls(List.of())
                    .build();
            return CompletableFuture.completedFuture(message);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            streamCalls.incrementAndGet();
            AssistantMessageChunk first = AssistantMessageChunk.builder().content("partial ").build();
            AssistantMessageChunk last = AssistantMessageChunk.builder()
                    .content("body")
                    .finishReason("length")
                    .build();
            return List.of(first, last).iterator();
        }
    }

    private static ReActAgent newAgent(Model.ModelClient client, String id) {
        ReActAgent agent = new ReActAgent(new AgentCard(id, id, "length truncation verification"));
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(5)
                .promptTemplate(List.of(Map.of("role", "system", "content", "you are an assistant")))
                .build());
        agent.setLlm(new Model(client));
        return agent;
    }

    private static Map<?, ?> runInvoke(Model.ModelClient client, String id) {
        ReActAgent agent = newAgent(client, id);
        AgentSession session = new AgentSession(id, null, agent.getCard());
        Object result = agent.invoke(Map.of("query", QUERY, "conversation_id", id), session);
        return result instanceof Map<?, ?> map ? map : Map.of();
    }

    private static List<OutputSchema> runStream(Model.ModelClient client, String id) {
        ReActAgent agent = newAgent(client, id);
        AgentSession session = new AgentSession(id, null, agent.getCard());
        List<OutputSchema> outputs = new ArrayList<>();
        Iterator<Object> iterator = agent.stream(Map.of("query", QUERY, "conversation_id", id), session, List.of());
        while (iterator.hasNext()) {
            Object item = iterator.next();
            if (item instanceof OutputSchema schema) {
                outputs.add(schema);
            }
        }
        return outputs;
    }

    private static void dumpStream(List<OutputSchema> outputs, String tag) {
        for (OutputSchema schema : outputs) {
            System.out.println("[" + tag + "] type=" + schema.getType() + " payload=" + schema.getPayload());
        }
    }

    /**
     * Non-stream path reports a length-capped empty answer as an answer that
     * carries the provider finish_reason so callers can detect truncation.
     */
    @Test
    void invokeTruncatedEmptyAnswerIsReportedAsAnswer() {
        LengthTruncatedClient client = new LengthTruncatedClient();
        Map<?, ?> result = runInvoke(client, "trunc-invoke");

        assertThat(client.invokeCalls.get()).isEqualTo(1);
        assertThat(String.valueOf(result.get("result_type"))).isEqualTo("answer");
        assertThat(String.valueOf(result.get("output"))).isEmpty();
        assertThat(result.get("failure")).isNull();
        assertThat(result.get("finish_reason")).isEqualTo("length");
    }

    /**
     * Stream path keeps the provider finish_reason on the assembled final
     * message; the react_model_response event must carry finish_reason=length
     * for a non-empty truncated stream.
     */
    @Test
    void streamKeepsFinishReasonOnFinalMessage() {
        ContentTruncatedClient client = new ContentTruncatedClient();
        List<String> records = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.logging.Handler capture = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                records.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Loggers.LLM.addHandler(capture);
        try {
            List<OutputSchema> outputs = runStream(client, "trunc-stream");
            dumpStream(outputs, "trunc-stream");
        } finally {
            Loggers.LLM.removeHandler(capture);
        }

        assertThat(client.streamCalls.get()).isEqualTo(1);
        assertThat(client.invokeCalls.get()).isEqualTo(0);
        assertThat(records)
                .anyMatch(line -> line.contains("event=react_model_response")
                        && line.contains("finish_reason=length"));
    }

    /**
     * Stream path with a length-capped reasoning-only output returns the
     * truncated stream as-is instead of replaying the request non-stream.
     */
    @Test
    void streamLengthCappedReasoningOnlyResultIsKeptWithoutReplay() {
        LengthTruncatedClient client = new LengthTruncatedClient();
        List<OutputSchema> outputs = runStream(client, "trunc-reasoning");

        assertThat(client.streamCalls.get()).isEqualTo(1);
        assertThat(client.invokeCalls.get()).isEqualTo(0);
        boolean hasReasoning = outputs.stream().anyMatch(schema -> "llm_reasoning".equals(schema.getType()));
        assertThat(hasReasoning).isTrue();
    }
}
