/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.ModelRetryEvent;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

class ReActAgentModelRetryOutputTest {

    @Test
    void streamEmitsHttpRetryBeforeLlmOutputAndAnswerWithSharedIndexes() {
        RetryingModelClient client = new RetryingModelClient(new ModelRetryEvent(
                2,
                4,
                429,
                null,
                Duration.ofMillis(1250),
                "retry_after"
        ));
        ReActAgent agent = agentWith(client);

        List<OutputSchema> outputs = collectOutput(agent.stream(
                Map.of("query", "hello"),
                new MemorySession("http-retry-session"),
                List.of()
        ));

        assertThat(outputs).extracting(OutputSchema::getType)
                .containsExactly("model_retry", "llm_output", "answer");
        assertThat(outputs).extracting(OutputSchema::getIndex).containsExactly(0, 1, 2);
        assertThat(payload(outputs.get(0))).containsExactly(
                Map.entry("retry_count", 2),
                Map.entry("max_retries", 4),
                Map.entry("delay_ms", 1250L),
                Map.entry("delay_source", "retry_after"),
                Map.entry("message", "模型请求失败，正在进行第 2/4 次重试"),
                Map.entry("status_code", 429)
        );
        assertThat(payload(outputs.get(0)))
                .doesNotContainKeys("exception_type", "exception_message", "request", "response", "prompt",
                        "credential");
        assertThat(payload(outputs.get(1))).containsEntry("content", "ok");
        assertThat(payload(outputs.get(2))).containsEntry("output", "ok");
    }

    @Test
    void streamEmitsIoRetryWithExceptionTypeButWithoutHttpStatus() {
        RetryingModelClient client = new RetryingModelClient(new ModelRetryEvent(
                1,
                3,
                null,
                "java.io.IOException",
                Duration.ofMillis(80),
                "backoff"
        ));
        ReActAgent agent = agentWith(client);

        List<OutputSchema> outputs = collectOutput(agent.stream(
                Map.of("query", "hello"),
                new MemorySession("io-retry-session"),
                List.of()
        ));

        Map<String, Object> retryPayload = payload(singleOutput(outputs, "model_retry"));
        assertThat(retryPayload)
                .containsEntry("exception_type", "java.io.IOException")
                .doesNotContainKey("status_code");
        assertThat(retryPayload.keySet()).containsExactly(
                "retry_count", "max_retries", "delay_ms", "delay_source", "message", "exception_type");
        assertThat(singleOutput(outputs, "answer").getPayload().toString())
                .contains("ok")
                .doesNotContain("IOException", "模型请求失败");
    }

    @Test
    void invokeDoesNotInstallRetryListenerOrEmitRetryOutput() {
        RetryingModelClient client = new RetryingModelClient(null);
        ReActAgent agent = agentWith(client);
        MemorySession session = new MemorySession("invoke-session");

        Object result = agent.invoke(Map.of("query", "hello"), session).toCompletableFuture().join();

        assertThat(client.invokeRetryListener).isNull();
        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("output")).isEqualTo("ok");
        assertThat(session.stream).noneMatch(item -> item instanceof OutputSchema output
                && Objects.equals(output.getType(), "model_retry"));
    }

    private static ReActAgent agentWith(RetryingModelClient client) {
        ReActAgent agent = new ReActAgent(new AgentCard(
                "retry-agent-" + System.nanoTime(),
                "retry-agent",
                "test agent"
        ));
        agent.setLlm(new Model(client));
        return agent;
    }

    private static List<OutputSchema> collectOutput(Iterator<Object> iterator) {
        List<OutputSchema> outputs = new ArrayList<>();
        while (iterator.hasNext()) {
            Object item = iterator.next();
            if (item instanceof OutputSchema outputSchema) {
                outputs.add(outputSchema);
            }
        }
        return outputs;
    }

    private static OutputSchema singleOutput(List<OutputSchema> outputs, String type) {
        List<OutputSchema> matches = outputs.stream()
                .filter(output -> Objects.equals(output.getType(), type))
                .toList();
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(OutputSchema output) {
        assertThat(output.getPayload()).isInstanceOf(Map.class);
        return (Map<String, Object>) output.getPayload();
    }

    private static final class RetryingModelClient implements Model.ModelClient {
        private final ModelRetryEvent retryEvent;
        private Object invokeRetryListener;

        private RetryingModelClient(ModelRetryEvent retryEvent) {
            this.retryEvent = retryEvent;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeRetryListener = options.getRetryListener();
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            assertThat(options.getRetryListener()).isNotNull();
            options.getRetryListener().onRetry(retryEvent);
            return List.of(AssistantMessageChunk.builder()
                    .content("ok")
                    .finishReason("stop")
                    .build()).iterator();
        }
    }

    private static final class MemorySession implements AgentSessionApi, ContextEngine.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        private MemorySession(String sessionId) {
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
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }
}
