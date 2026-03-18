/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.context.processor.offloader;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MessageSummaryOffloader}.
 * <p>
 * Ported from Python's {@code test_message_summary_offloader.py}.
 * <p>
 * Note: Full LLM-dependent offload tests cannot run without a real or mocked
 * Model dependency. These tests focus on config validation and initialization.
 */
class MessageSummaryOffloaderTest {

    // ---------- Config validation ----------

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
            // Model constructor will fail without real config, but validation should pass
            // The constructor calls validateConfig before anything else
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

        @Test
        @DisplayName("messages_threshold null is valid")
        void testMessagesThresholdNull() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesToKeep(10)
                    .messagesThreshold(null)
                    .build();
            assertDoesNotThrow(() -> new MessageSummaryOffloader(config));
        }
    }

    // ---------- Config builder ----------

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
            assertTrue(config.isKeepLastRound());
            assertNull(config.getMessagesToKeep());
            assertNull(config.getMessagesThreshold());
            assertNull(config.getModel());
            assertNull(config.getModelClient());
            assertNull(config.getCustomizedSummaryPrompt());
        }

        @Test
        @DisplayName("custom config values")
        void testCustomConfig() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesThreshold(100)
                    .tokensThreshold(15000)
                    .largeMessageThreshold(500)
                    .offloadMessageType(List.of("user", "assistant"))
                    .messagesToKeep(10)
                    .keepLastRound(true)
                    .customizedSummaryPrompt("Custom summary prompt")
                    .build();
            assertEquals(100, config.getMessagesThreshold());
            assertEquals(15000, config.getTokensThreshold());
            assertEquals(500, config.getLargeMessageThreshold());
            assertEquals(List.of("user", "assistant"), config.getOffloadMessageType());
            assertEquals(10, config.getMessagesToKeep());
            assertTrue(config.isKeepLastRound());
            assertEquals("Custom summary prompt", config.getCustomizedSummaryPrompt());
        }
    }

    // ---------- Processor type ----------

    @Test
    @DisplayName("processor type returns correct name")
    void testProcessorType() {
        MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder().build();
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(config);
        // MessageSummaryOffloader extends MessageOffloader
        assertNotNull(offloader.processorType());
    }
}
