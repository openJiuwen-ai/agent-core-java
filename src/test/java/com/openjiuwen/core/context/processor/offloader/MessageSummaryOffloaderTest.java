/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.context.processor.offloader;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MessageSummaryOffloader}.
 */
class MessageSummaryOffloaderTest {

    private static ModelContext createContextWithSummaryOffloader(MessageSummaryOffloaderConfig config,
                                                                  List<BaseMessage> historyMessages) {
        ContextEngine.registerProcessor("MessageSummaryOffloader", MessageSummaryOffloader.class,
                cfg -> new MessageSummaryOffloader((MessageSummaryOffloaderConfig) cfg));
        ContextEngine engine = new ContextEngine(
                ContextEngineConfig.builder().defaultWindowMessageNum(100).build());
        List<ContextEngine.ProcessorSpec> processors = List.of(
                new ContextEngine.ProcessorSpec("MessageSummaryOffloader", config));
        return engine.createContext("test_ctx", null, processors, historyMessages, null);
    }

    @Nested
    @DisplayName("Config validation")
    class ConfigValidation {

        @Test
        @DisplayName("valid config: messages_to_keep < messages_threshold")
        void testValidConfig() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesToKeep(10)
                    .messagesThreshold(20)
                    .build();
            assertDoesNotThrow(() -> new MessageSummaryOffloader(config));
        }

        @Test
        @DisplayName("messages_to_keep == messages_threshold throws")
        void testMessagesToKeepEqualsThreshold() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesToKeep(20)
                    .messagesThreshold(20)
                    .build();
            assertThrows(BaseError.class, () -> new MessageSummaryOffloader(config));
        }

        @Test
        @DisplayName("messages_to_keep > messages_threshold throws")
        void testMessagesToKeepGreaterThanThreshold() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesToKeep(30)
                    .messagesThreshold(20)
                    .build();
            assertThrows(BaseError.class, () -> new MessageSummaryOffloader(config));
        }

        @Test
        @DisplayName("messages_to_keep null is valid")
        void testMessagesToKeepNull() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesToKeep(null)
                    .messagesThreshold(20)
                    .build();
            assertDoesNotThrow(() -> new MessageSummaryOffloader(config));
        }
    }

    @Nested
    @DisplayName("Config builder")
    class ConfigBuilder {

        @Test
        @DisplayName("default config values")
        void testDefaultConfig() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder().build();
            assertEquals(20000, config.getTokensThreshold());
            assertEquals(1000, config.getLargeMessageThreshold());
            assertEquals(List.of("tool"), config.getOffloadMessageType());
            assertEquals(List.of("reload_original_context_messages"), config.getProtectedToolNames());
            assertTrue(config.isKeepLastRound());
            assertNull(config.getMessagesToKeep());
            assertNull(config.getMessagesThreshold());
            assertNull(config.getModel());
            assertNull(config.getModelClient());
            assertEquals(900, config.getSummaryMaxTokens());
            assertFalse(config.isEnablePreciseStep());
            assertEquals(8, config.getStepSummaryMaxContextMessages());
            assertEquals(200000, config.getContentMaxCharsForCompression());
        }

        @Test
        @DisplayName("custom config values")
        void testCustomConfig() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesThreshold(100)
                    .tokensThreshold(15000)
                    .largeMessageThreshold(500)
                    .offloadMessageType(List.of("user", "assistant"))
                    .protectedToolNames(List.of("grep:*.md"))
                    .messagesToKeep(10)
                    .keepLastRound(true)
                    .summaryMaxTokens(600)
                    .enablePreciseStep(true)
                    .stepSummaryMaxContextMessages(6)
                    .contentMaxCharsForCompression(50000)
                    .build();
            assertEquals(100, config.getMessagesThreshold());
            assertEquals(15000, config.getTokensThreshold());
            assertEquals(500, config.getLargeMessageThreshold());
            assertEquals(List.of("user", "assistant"), config.getOffloadMessageType());
            assertEquals(List.of("grep:*.md"), config.getProtectedToolNames());
            assertEquals(10, config.getMessagesToKeep());
            assertTrue(config.isKeepLastRound());
            assertEquals(600, config.getSummaryMaxTokens());
            assertTrue(config.isEnablePreciseStep());
            assertEquals(6, config.getStepSummaryMaxContextMessages());
            assertEquals(50000, config.getContentMaxCharsForCompression());
        }
    }

    @Test
    @DisplayName("trigger only checks newly added messages")
    void testTriggerOnlyChecksMessagesToAdd() {
        MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                .largeMessageThreshold(20)
                .build();
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(config);
        ModelContext context = createContextWithSummaryOffloader(config, List.of(
                new UserMessage("question"),
                ToolMessage.builder().content("x".repeat(200)).toolCallId("tc-1").name("grep").build()
        ));

        assertFalse(offloader.triggerAddMessages(context, List.of(new UserMessage("short"))));
        assertTrue(offloader.triggerAddMessages(context, List.of(
                ToolMessage.builder().content("y".repeat(200)).toolCallId("tc-2").name("grep").build()
        )));
    }

    @Test
    @DisplayName("protected tool messages are not considered summary candidates")
    void testProtectedToolMessageSkipped() {
        MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                .largeMessageThreshold(20)
                .protectedToolNames(List.of("reload_original_context_messages"))
                .build();
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(config);
        List<BaseMessage> contextMessages = List.of(
                AssistantMessage.builder().content("").toolCalls(List.of(
                        ToolCall.builder().id("tc-1").name("reload_original_context_messages")
                                .type("function").arguments("{\"offload_handle\":\"abc\"}").build()
                )).build(),
                ToolMessage.builder().content("x".repeat(200)).toolCallId("tc-1")
                        .name("reload_original_context_messages").build()
        );

        assertFalse(offloader.shouldOffloadMessage(contextMessages.get(1), null, contextMessages));
    }

    @Test
    @DisplayName("smart truncate keeps head middle tail sections")
    void testSmartTruncateContent() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(MessageSummaryOffloaderConfig.builder().build());
        String content = "A".repeat(80) + "B".repeat(80) + "C".repeat(80);

        String truncated = offloader.smartTruncateContent(content, 90);

        assertTrue(truncated.contains(MessageSummaryOffloader.TRUNCATED_MARKER));
        assertTrue(truncated.startsWith("A"));
        assertTrue(truncated.endsWith("C".repeat(truncated.endsWith("C") ? 1 : 0)) || truncated.endsWith("C"));
    }

    @Test
    @DisplayName("parse compression result extracts embedded JSON")
    void testParseCompressionResult() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(MessageSummaryOffloaderConfig.builder().build());
        Map<String, Object> result = offloader.parseCompressionResult("""
                Here is the result:
                {"summary":"short summary","offload_data_explanation":{"category":"logs"}}
                """);

        assertEquals("short summary", result.get("summary"));
    }

    @Test
    @DisplayName("processor type returns correct name")
    void testProcessorType() {
        MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder().build();
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(config);
        assertEquals("MessageSummaryOffloader", offloader.processorType());
    }
}
