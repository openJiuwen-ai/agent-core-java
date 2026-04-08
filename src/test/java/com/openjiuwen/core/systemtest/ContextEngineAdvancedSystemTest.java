/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextMessageBuffer;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.processor.compressor.CurrentRoundCompressorConfig;
import com.openjiuwen.core.context.processor.compressor.DialogueCompressorConfig;
import com.openjiuwen.core.context.processor.compressor.RoundLevelCompressorConfig;
import com.openjiuwen.core.context.processor.offloader.MessageOffloaderConfig;
import com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloaderConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.Session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Advanced ContextEngine system tests covering gaps identified in CHECK doc:
 * ContextMessageBuffer, SessionModelContext messages, Compressor/Offloader configs.
 * All tests are local (no remote API required).
 */
@Tag("system-test")
class ContextEngineAdvancedSystemTest {

    static class MinimalSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private String currentOperatorId;

        MinimalSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override public String getSessionId() { return sessionId; }
        @Override public Object getState(String key) { return state.get(key); }
        @Override public void updateState(Map<String, Object> stateMap) {
            if (stateMap != null) state.putAll(stateMap);
        }
        @Override public void setCurrentOperatorId(String operatorId) { this.currentOperatorId = operatorId; }
        @Override public String getCurrentOperatorId() { return currentOperatorId; }
    }

    @Nested
    @DisplayName("ContextMessageBuffer Tests")
    class ContextMessageBufferTests {

        @Test
        @DisplayName("Buffer addBack and getBack")
        void testAddAndGetBack() {
            List<BaseMessage> history = new ArrayList<>();
            history.add(new UserMessage("History msg"));

            ContextMessageBuffer buffer = new ContextMessageBuffer(history, 100);
            buffer.addBack(List.of(
                    new UserMessage("Hello"),
                    new AssistantMessage("Hi there")));

            List<BaseMessage> messages = buffer.getBack();
            assertNotNull(messages);
            assertTrue(messages.size() >= 3, "Should include history + new messages");
            System.out.println("[Buffer] Message count: " + messages.size());
        }

        @Test
        @DisplayName("Buffer size reflects content")
        void testBufferSize() {
            ContextMessageBuffer buffer = new ContextMessageBuffer(List.of(), 100);
            assertEquals(0, buffer.size());

            buffer.addBack(List.of(new UserMessage("msg1"), new UserMessage("msg2")));
            assertEquals(2, buffer.size());
        }

        @Test
        @DisplayName("Buffer popBack removes messages")
        void testPopBack() {
            ContextMessageBuffer buffer = new ContextMessageBuffer(List.of(), 100);
            buffer.addBack(List.of(
                    new UserMessage("msg1"),
                    new AssistantMessage("msg2"),
                    new UserMessage("msg3")));

            List<BaseMessage> popped = buffer.popBack(1, false);
            assertNotNull(popped);
            assertEquals(1, popped.size());
            assertEquals(2, buffer.size(), "Buffer should have 2 messages after pop");
        }

        @Test
        @DisplayName("Buffer setMessages replaces content")
        void testSetMessages() {
            ContextMessageBuffer buffer = new ContextMessageBuffer(List.of(), 100);
            buffer.addBack(List.of(new UserMessage("old1"), new UserMessage("old2")));
            assertEquals(2, buffer.size());

            buffer.setMessages(List.of(new UserMessage("new1")), false);
            assertEquals(1, buffer.size());
        }

        @Test
        @DisplayName("Buffer getBack with size limit")
        void testGetBackWithSizeLimit() {
            ContextMessageBuffer buffer = new ContextMessageBuffer(List.of(), 100);
            buffer.addBack(List.of(
                    new UserMessage("msg1"),
                    new AssistantMessage("msg2"),
                    new UserMessage("msg3"),
                    new AssistantMessage("msg4")));

            List<BaseMessage> last2 = buffer.getBack(2, false);
            assertNotNull(last2);
            assertEquals(2, last2.size(), "Should return only last 2 messages");
        }
    }

    @Nested
    @DisplayName("SessionModelContext Tests")
    class SessionModelContextTests {

        @Test
        @DisplayName("SessionModelContext addMessages and getMessages")
        void testAddAndGetMessages() {
            ContextEngineConfig config = new ContextEngineConfig();
            List<BaseMessage> history = new ArrayList<>();
            history.add(new UserMessage("Previous question"));
            history.add(new AssistantMessage("Previous answer"));

            SessionModelContext ctx = new SessionModelContext(
                    "ctx_1", "sess_1", config, history, List.of(), null);

            ctx.addMessages(List.of(
                    new UserMessage("New question"),
                    new AssistantMessage("New answer")));

            List<BaseMessage> messages = ctx.getMessages(null, true);
            assertNotNull(messages);
            assertTrue(messages.size() >= 4, "Should have history + new messages");
            System.out.println("[SessionModelContext] Messages: " + messages.size());
        }

        @Test
        @DisplayName("SessionModelContext clearMessages")
        void testClearMessages() {
            SessionModelContext ctx = new SessionModelContext(
                    "ctx_2", "sess_2", new ContextEngineConfig(), List.of(), List.of(), null);

            ctx.addMessages(List.of(new UserMessage("Q1"), new AssistantMessage("A1")));
            assertTrue(ctx.size() > 0);

            ctx.clearMessages(false);
            assertEquals(0, ctx.size());
        }

        @Test
        @DisplayName("SessionModelContext contextId and sessionId")
        void testContextIdentifiers() {
            SessionModelContext ctx = new SessionModelContext(
                    "my_ctx", "my_sess", new ContextEngineConfig(), List.of(), List.of(), null);

            assertEquals("my_ctx", ctx.contextId());
            assertEquals("my_sess", ctx.sessionId());
        }
    }

    @Nested
    @DisplayName("ContextEngine SaveContexts Tests")
    class ContextEngineSaveTests {

        @Test
        @DisplayName("ContextEngine createContext with history and retrieve")
        void testCreateWithHistoryAndRetrieve() {
            ContextEngine engine = new ContextEngine();
            String sessionId = "save_sess";
            MinimalSession session = new MinimalSession(sessionId);

            List<BaseMessage> history = new ArrayList<>();
            history.add(new UserMessage("Hello"));
            history.add(new AssistantMessage("Hi!"));

            engine.createContext("ctx_save", session, null, history, null);
            ModelContext retrieved = engine.getContext("ctx_save", sessionId);
            assertNotNull(retrieved);
        }
    }

    @Nested
    @DisplayName("Compressor Config Tests")
    class CompressorConfigTests {

        @Test
        @DisplayName("CurrentRoundCompressorConfig defaults")
        void testCurrentRoundCompressorDefaults() {
            CurrentRoundCompressorConfig config = new CurrentRoundCompressorConfig();
            assertEquals(10000, config.getTokensThreshold());
            assertEquals(1000, config.getLargeMessageThreshold());
            assertEquals(false, config.isSingleMultiCompression());
        }

        @Test
        @DisplayName("DialogueCompressorConfig builder")
        void testDialogueCompressorBuilder() {
            DialogueCompressorConfig config = DialogueCompressorConfig.builder()
                    .messagesThreshold(20)
                    .tokensThreshold(8000)
                    .keepLastRound(true)
                    .compressionTokenLimit(3000)
                    .build();

            assertEquals(20, config.getMessagesThreshold());
            assertEquals(8000, config.getTokensThreshold());
            assertTrue(config.isKeepLastRound());
            assertEquals(3000, config.getCompressionTokenLimit());
        }

        @Test
        @DisplayName("RoundLevelCompressorConfig builder")
        void testRoundLevelCompressorBuilder() {
            RoundLevelCompressorConfig config = RoundLevelCompressorConfig.builder()
                    .roundsThreshold(5)
                    .tokensThreshold(5000)
                    .keepLastRound(false)
                    .build();

            assertEquals(5, config.getRoundsThreshold());
            assertEquals(5000, config.getTokensThreshold());
            assertEquals(false, config.isKeepLastRound());
        }
    }

    @Nested
    @DisplayName("Offloader Config Tests")
    class OffloaderConfigTests {

        @Test
        @DisplayName("MessageOffloaderConfig defaults")
        void testMessageOffloaderDefaults() {
            MessageOffloaderConfig config = new MessageOffloaderConfig();
            assertEquals(20000, config.getTokensThreshold());
            assertEquals(1000, config.getLargeMessageThreshold());
            assertEquals(100, config.getTrimSize());
            assertTrue(config.isKeepLastRound());
        }

        @Test
        @DisplayName("MessageOffloaderConfig builder")
        void testMessageOffloaderBuilder() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesThreshold(50)
                    .tokensThreshold(15000)
                    .offloadMessageType(List.of("tool", "system"))
                    .trimSize(200)
                    .keepLastRound(false)
                    .build();

            assertEquals(50, config.getMessagesThreshold());
            assertEquals(15000, config.getTokensThreshold());
            assertEquals(List.of("tool", "system"), config.getOffloadMessageType());
            assertEquals(200, config.getTrimSize());
            assertNotNull(config);
        }

        @Test
        @DisplayName("MessageSummaryOffloaderConfig builder")
        void testMessageSummaryOffloaderBuilder() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesThreshold(30)
                    .tokensThreshold(10000)
                    .customizedSummaryPrompt("Summarize the following message:")
                    .build();

            assertEquals(30, config.getMessagesThreshold());
            assertEquals(10000, config.getTokensThreshold());
            assertNotNull(config.getCustomizedSummaryPrompt());
        }
    }
}
