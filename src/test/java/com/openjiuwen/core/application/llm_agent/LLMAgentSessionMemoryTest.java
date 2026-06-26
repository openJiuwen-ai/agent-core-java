/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.AddMemResult;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.legacy.config.LegacyReActAgentConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_session_id_in_memory} module in
 * {@code tests/unit_tests/core/memory/test_session_id_in_memory.py}.
 */
class LLMAgentSessionMemoryTest {

    @AfterEach
    void resetLongTermMemorySingleton() {
        LongTermMemory.resetInstance();
    }

    @Test
    void invokeWithSessionIdWritesUserAndAssistantMessages() {
        RecordingLongTermMemory memory = installRecordingMemory();
        TestableLLMAgent agent = new TestableLLMAgent(memoryEnabledConfig(), answer("Test response"));

        agent.invoke(Map.of(
                "query", "Hello, how are you?",
                "user_id", "test_user_456",
                "conversation_id", "test_session_123"
        )).toCompletableFuture().join();

        RecordingLongTermMemory.Call call = awaitSingleCall(memory);
        assertThat(call.sessionId()).isEqualTo("test_session_123");
        assertThat(call.messages()).hasSize(2);
        assertThat(call.messages().get(0).getRole()).isEqualTo("user");
        assertThat(call.messages().get(0).getContentAsString()).isEqualTo("Hello, how are you?");
        assertThat(call.messages().get(1).getRole()).isEqualTo("assistant");
        assertThat(call.messages().get(1).getContentAsString()).isEqualTo("Test response");
    }

    @Test
    void streamWithSessionIdUsesExternalSessionId() {
        RecordingLongTermMemory memory = installRecordingMemory();
        TestableLLMAgent agent = new TestableLLMAgent(memoryEnabledConfig(), answer("The weather is sunny."));
        StubSession session = new StubSession("test_stream_session_789");

        Iterator<Object> stream = agent.stream(Map.of(
                "query", "What's the weather today?",
                "user_id", "test_stream_user_012",
                "conversation_id", "ignored_input_session"
        ), session, List.of(StreamMode.OUTPUT));
        while (stream.hasNext()) {
            stream.next();
        }

        RecordingLongTermMemory.Call call = awaitSingleCall(memory);
        assertThat(call.sessionId()).isEqualTo("test_stream_session_789");
    }

    @Test
    void writeMessagesToMemoryWithHistoryCarriesUserIdAndConversation() {
        RecordingLongTermMemory memory = installRecordingMemory();
        TestableLLMAgent agent = new TestableLLMAgent(
                memoryEnabledConfig(),
                answer("AI stands for Artificial Intelligence.")
        );

        agent.invoke(Map.of(
                "query", "Tell me about AI",
                "user_id", "test_history_user_678",
                "conversation_id", "test_history_session_345"
        )).toCompletableFuture().join();

        RecordingLongTermMemory.Call call = awaitSingleCall(memory);
        assertThat(call.sessionId()).isEqualTo("test_history_session_345");
        assertThat(call.userId()).isEqualTo("test_history_user_678");
        assertThat(call.messages()).extracting(BaseMessage::getRole).containsExactly("user", "assistant");
        assertThat(call.messages()).extracting(BaseMessage::getContentAsString)
                .containsExactly("Tell me about AI", "AI stands for Artificial Intelligence.");
    }

    @Test
    void memoryDisabledSkipsMemoryWrites() throws Exception {
        RecordingLongTermMemory memory = installRecordingMemory();
        TestableLLMAgent agent = new TestableLLMAgent(memoryDisabledConfig(), answer("Test response"));

        agent.invoke(Map.of(
                "query", "Hello",
                "user_id", "test_user",
                "conversation_id", "test_session"
        )).toCompletableFuture().join();
        TimeUnit.MILLISECONDS.sleep(100L);

        assertThat(memory.calls()).isEmpty();
    }

    private static LegacyReActAgentConfig memoryEnabledConfig() {
        LegacyReActAgentConfig config = baseConfig("test_agent");
        config.setMemoryScopeId("test_scope_id");
        config.setAgentMemoryConfig(AgentMemoryConfig.builder()
                .enableLongTermMem(true)
                .build());
        return config;
    }

    private static LegacyReActAgentConfig memoryDisabledConfig() {
        LegacyReActAgentConfig config = baseConfig("test_agent_no_memory");
        config.setMemoryScopeId("");
        config.setAgentMemoryConfig(AgentMemoryConfig.builder()
                .enableLongTermMem(false)
                .enableUserProfile(false)
                .enableSemanticMemory(false)
                .enableEpisodicMemory(false)
                .enableSummaryMemory(false)
                .build());
        return config;
    }

    private static LegacyReActAgentConfig baseConfig(String id) {
        LegacyReActAgentConfig config = new LegacyReActAgentConfig();
        config.setId(id);
        config.setVersion("1.0");
        config.setDescription("Test Agent");
        return config;
    }

    private static Map<String, Object> answer(String output) {
        return Map.of("result_type", "answer", "output", output);
    }

    private static RecordingLongTermMemory installRecordingMemory() {
        try {
            RecordingLongTermMemory memory = new RecordingLongTermMemory();
            Field instanceField = LongTermMemory.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, memory);
            return memory;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static RecordingLongTermMemory.Call awaitSingleCall(RecordingLongTermMemory memory) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (System.nanoTime() < deadline) {
            if (!memory.calls().isEmpty()) {
                return memory.calls().get(0);
            }
            try {
                TimeUnit.MILLISECONDS.sleep(20L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
        throw new AssertionError("Timed out waiting for memory write");
    }

    /**
     * Mirrors Python's patched {@code LLMController.invoke} collaborator in
     * {@code tests/unit_tests/core/memory/test_session_id_in_memory.py}.
     */
    private static final class TestableLLMAgent extends LLMAgent {
        private final Object response;

        private TestableLLMAgent(LegacyReActAgentConfig agentConfig, Object response) {
            super(agentConfig);
            this.response = response;
        }

        @Override
        protected Object invokeController(Map<String, Object> inputs, AgentSessionApi session) {
            return response;
        }
    }

    /**
     * Mirrors Python's mock {@code LongTermMemory.add_messages} call recorder in
     * {@code tests/unit_tests/core/memory/test_session_id_in_memory.py}.
     */
    private static final class RecordingLongTermMemory extends LongTermMemory {
        private final List<Call> calls = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<AddMemResult> addMessages(List<BaseMessage> messages,
                                                           AgentMemoryConfig agentConfig,
                                                           String userId,
                                                           String scopeId,
                                                           String sessionId,
                                                           ZonedDateTime time,
                                                           boolean sync,
                                                           int retries) {
            calls.add(new Call(List.copyOf(messages), userId, scopeId, sessionId));
            return CompletableFuture.completedFuture(null);
        }

        private List<Call> calls() {
            return calls;
        }

        private record Call(List<BaseMessage> messages, String userId, String scopeId, String sessionId) {
        }
    }

    private static final class StubSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private StubSession(String sessionId) {
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
            state.put("lastStream", data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return List.of().iterator();
        }
    }
}
