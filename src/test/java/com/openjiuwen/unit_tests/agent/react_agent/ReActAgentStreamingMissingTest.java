/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent.react_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestReActAgentStreaming} in
 * {@code tests/unit_tests/agent/react_agent/test_react_agent_streaming.py}.
 */
class ReActAgentStreamingMissingTest {

    @Test
    void testStreamingWritesLlmOutputChunksToSession() {
        RecordingModelClient modelClient = new RecordingModelClient(
                "Fallback answer.",
                "Hello from streaming!"
        );
        ReActAgent agent = agentWithModel("agent_stream_llm_output", modelClient);
        MemorySession session = new MemorySession("sess_stream_001");

        Object result = agent.invoke(Map.of("query", "hi"), session, Map.of("_streaming", true))
                .toCompletableFuture()
                .join();

        Map<String, Object> resultMap = assertMap(result);
        assertThat(resultMap).containsEntry("result_type", "answer");
        assertThat(resultMap.get("output")).asString().contains("Hello from streaming!");
        assertThat(modelClient.streamCalls).isEqualTo(1);
        assertThat(modelClient.invokeCalls).isZero();

        List<OutputSchema> llmOutputFrames = session.stream.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .filter(frame -> "llm_output".equals(frame.getType()))
                .toList();
        assertThat(llmOutputFrames).isNotEmpty();
        String streamedText = llmOutputFrames.stream()
                .map(OutputSchema::getPayload)
                .map(ReActAgentStreamingMissingTest::assertMap)
                .map(payload -> String.valueOf(payload.getOrDefault("content", "")))
                .reduce("", String::concat);
        assertThat(streamedText).contains("Hello from streaming!");
    }

    @Test
    void testNoSessionFallsBackToInvoke() {
        RecordingModelClient modelClient = new RecordingModelClient(
                "Fallback answer.",
                "stream should not be used"
        );
        ReActAgent agent = agentWithModel("agent_no_session", modelClient);

        Object result = agent.invoke(Map.of("query", "hello"), null)
                .toCompletableFuture()
                .join();

        Map<String, Object> resultMap = assertMap(result);
        assertThat(resultMap).containsEntry("result_type", "answer");
        assertThat(resultMap.get("output")).asString().contains("Fallback answer.");
        assertThat(modelClient.streamCalls).isZero();
        assertThat(modelClient.invokeCalls).isEqualTo(1);
    }

    private static ReActAgent agentWithModel(String agentId, RecordingModelClient modelClient) {
        ReActAgent agent = new ReActAgent(new AgentCard(agentId, agentId, "streaming parity test"));
        agent.configure(new ReActAgentConfig()
                .configurePromptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")))
                .configureMaxIterations(2));
        agent.setLlm(new Model(modelClient));
        return agent;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> assertMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    /**
     * Mirrors Python's {@code MockLLMModel} fixture in
     * {@code tests/unit_tests/agent/react_agent/test_react_agent_streaming.py}.
     */
    private static final class RecordingModelClient implements Model.ModelClient {
        private final String invokeText;
        private final String streamText;
        private int invokeCalls;
        private int streamCalls;

        private RecordingModelClient(String invokeText, String streamText) {
            this.invokeText = invokeText;
            this.streamText = streamText;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeCalls++;
            return CompletableFuture.completedFuture(new AssistantMessage(invokeText));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            streamCalls++;
            return List.of(AssistantMessageChunk.builder().content(streamText).build()).iterator();
        }
    }

    /**
     * Mirrors Python's agent session used to capture stream writes in
     * {@code tests/unit_tests/agent/react_agent/test_react_agent_streaming.py}.
     */
    private static final class MemorySession implements AgentSessionApi {
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
            if (data != null) {
                state.putAll(data);
            }
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
