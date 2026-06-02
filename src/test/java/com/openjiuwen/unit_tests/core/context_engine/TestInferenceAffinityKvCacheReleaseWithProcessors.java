/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.processor.offloader.MessageOffloaderConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for ContextEngine, InferenceAffinity model wiring, and processors.
 * <p>
 * Mirrors Python's
 * {@code agent-core-0.1.12/tests/unit_tests/core/context_engine/test_inference_affinity_kv_cache_release_with_processors.py}.
 */
@DisplayName("TestInferenceAffinityKvCacheReleaseWithProcessors")
public class TestInferenceAffinityKvCacheReleaseWithProcessors {

    @Test
    @DisplayName("KV cache release with MessageOffloader")
    void testInferenceAffinityKvCacheReleaseWithMessageOffloader() throws Exception {
        RecordingModel model = new RecordingModel();
        SimpleSession session = new SimpleSession("sess-affinity-1");
        String largeToolContent = "X".repeat(100);

        ContextEngine engine = new ContextEngine(ContextEngineConfig.builder()
                .enableKvCacheRelease(true)
                .defaultWindowMessageNum(100)
                .build());
        MessageOffloaderConfig offloaderConfig = MessageOffloaderConfig.builder()
                .messagesThreshold(3)
                .tokensThreshold(100000)
                .largeMessageThreshold(50)
                .trimSize(10)
                .offloadMessageType(List.of("tool"))
                .keepLastRound(false)
                .build();

        ModelContext context = engine.createContext(
                "affinity_offload_ctx",
                session,
                List.of(new ContextEngine.ProcessorSpec("MessageOffloader", offloaderConfig)),
                List.of(
                        new UserMessage("u0"),
                        new ToolMessage(largeToolContent, "t1"),
                        new AssistantMessage("a0")),
                null);

        ContextWindow before = context.getContextWindow(null, null, null, null, Map.of("model", model));
        model.invoke(
                before.getMessages(),
                null, null, null, "mock-model", null, null, null, null,
                Map.of("session_id", session.getSessionId(), "enable_cache_sharing", true));

        try (CapturedLogs logs = CapturedLogs.attach(Loggers.CONTEXT_ENGINE, Loggers.LLM)) {
            context.addMessages(List.of(
                    new UserMessage("Follow up question"),
                    new AssistantMessage("Follow up answer")));

            ContextWindow after = context.getContextWindow(null, null, null, null, Map.of("model", model));

            assertTrue(logs.contains("MessageOffloader triggered"),
                    "Expected MessageOffloader triggered log");
            assertTrue(logs.containsBoth("RELEASE REASON", "Message modified"),
                    "Expected release reason log for message modification");
            assertTrue(model.releaseCalled, "Expected release method to be called");
            assertEquals(session.getSessionId(), model.releaseSessionId);
            assertTrue(logs.contains("KV cache release successful"),
                    "Expected KV cache release success log");
            assertTrue(model.blockReleased > 0, "Expected block_released > 0");
            assertEquals(session.getSessionId(), model.cacheSalt);

            List<BaseMessage> toolMessagesAfter = after.getMessages().stream()
                    .filter(message -> message.getRole().toLowerCase().contains("tool"))
                    .toList();
            assertFalse(toolMessagesAfter.isEmpty(), "Expected a tool message after offload processing");
            BaseMessage toolMessage = toolMessagesAfter.getFirst();
            assertTrue(toolMessage.getContentAsString().contains("OFFLOAD")
                            || toolMessage.getContentAsString().length() < largeToolContent.length(),
                    "Expected tool message to be offloaded");
        }
    }

    @Test
    @DisplayName("KV cache no release without modification")
    void testInferenceAffinityKvCacheNoReleaseWithoutModification() {
        RecordingModel model = new RecordingModel();
        SimpleSession session = new SimpleSession("sess-affinity-2");

        ContextEngine engine = new ContextEngine(ContextEngineConfig.builder()
                .enableKvCacheRelease(true)
                .defaultWindowMessageNum(100)
                .build());
        ModelContext context = engine.createContext(
                "no_release_ctx",
                session,
                List.of(),
                List.of(new UserMessage("Hello"), new AssistantMessage("Hi there")),
                null);

        context.getContextWindow(null, null, null, null, Map.of("model", model));

        try (CapturedLogs logs = CapturedLogs.attach(Loggers.CONTEXT_ENGINE, Loggers.LLM)) {
            context.addMessages(List.of(new UserMessage("How are you?")));
            context.getContextWindow(null, null, null, null, Map.of("model", model));

            assertFalse(model.releaseCalled,
                    "Expected release method not to be called when no messages were modified");
            assertFalse(logs.contains("KV cache release"));
            assertFalse(logs.contains("RELEASE REASON"));
        }
    }

    private static final class RecordingModel extends Model {
        private boolean releaseCalled;
        private String releaseSessionId;
        private String cacheSalt;
        private int blockReleased;
        private Map<String, Object> lastInvokeKwargs;

        private RecordingModel() {
            super(
                    ModelClientConfig.builder()
                            .clientProvider("OpenAI")
                            .apiKey("test-key")
                            .apiBase("https://api.example.test/v1")
                            .verifySsl(false)
                            .build(),
                    ModelRequestConfig.builder().modelName("mock-model").build());
        }

        @Override
        public boolean supportsKvCacheRelease() {
            return true;
        }

        @Override
        public AssistantMessage invoke(Object messages,
                                       Object tools,
                                       Float temperature,
                                       Float topP,
                                       String model,
                                       Integer maxTokens,
                                       String stop,
                                       BaseOutputParser outputParser,
                                       Float timeout,
                                       Map<String, Object> kwargs) {
            lastInvokeKwargs = kwargs == null ? null : new LinkedHashMap<>(kwargs);
            return new AssistantMessage("Mocked response");
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages,
                                                      Object tools,
                                                      Float temperature,
                                                      Float topP,
                                                      String model,
                                                      Integer maxTokens,
                                                      String stop,
                                                      BaseOutputParser outputParser,
                                                      Float timeout,
                                                      Map<String, Object> kwargs) {
            lastInvokeKwargs = kwargs == null ? null : new LinkedHashMap<>(kwargs);
            return List.of(AssistantMessageChunk.builder().content("Mocked response").build()).iterator();
        }

        @Override
        public boolean release(String sessionId,
                               List<?> messages,
                               int messagesReleasedIndex,
                               List<?> tools,
                               Integer toolsReleasedIndex,
                               String model) {
            releaseCalled = true;
            releaseSessionId = sessionId;
            blockReleased = 5;
            cacheSalt = sessionId;
            Loggers.LLM.info("KV cache release successful.");
            return true;
        }
    }

    private static final class SimpleSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new HashMap<>();

        private SimpleSession(String sessionId) {
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
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }

    private static final class CapturedLogs implements AutoCloseable {
        private final List<LoggerProtocol> loggers;
        private final CapturingHandler handler = new CapturingHandler();

        private CapturedLogs(List<LoggerProtocol> loggers) {
            this.loggers = loggers;
            handler.setLevel(Level.ALL);
            for (LoggerProtocol logger : loggers) {
                logger.setLevel(20);
                logger.addHandler(handler);
            }
        }

        private static CapturedLogs attach(LoggerProtocol... loggers) {
            return new CapturedLogs(List.of(loggers));
        }

        private boolean contains(String value) {
            return handler.messages.stream().anyMatch(message -> message.contains(value));
        }

        private boolean containsBoth(String left, String right) {
            return handler.messages.stream()
                    .anyMatch(message -> message.contains(left) && message.contains(right));
        }

        @Override
        public void close() {
            for (LoggerProtocol logger : loggers) {
                logger.removeHandler(handler);
            }
        }
    }

    private static final class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
