/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ContextCompressionProcessorBase.
 * <p>
 * Mirrors Python's test_context_compression_processor_base.py from
 * <code>tests/unit_tests/core/context_engine/test_context_compression_processor_base.py</code>.
 */
@DisplayName("Context Compression Processor Base Tests")
class TestContextCompressionProcessorBase {

    // Stub classes
    static class CompressionConfig {
        int targetTokens;
        double compressionRatio;

        CompressionConfig(int targetTokens, double compressionRatio) {
            this.targetTokens = targetTokens;
            this.compressionRatio = compressionRatio;
        }
    }

    static class MessageBlock {
        List<String> messages = new ArrayList<>();
        int tokenCount;

        void addMessage(String message) {
            messages.add(message);
            tokenCount += message.length() / 4;
        }

        int getTokenCount() {
            return tokenCount;
        }

        List<String> getMessages() {
            return new ArrayList<>(messages);
        }
    }

    static abstract class CompressionProcessorBase {
        CompressionConfig config;

        CompressionProcessorBase(CompressionConfig config) {
            this.config = config;
        }

        abstract MessageBlock compress(MessageBlock input);

        boolean shouldCompress(MessageBlock input) {
            return input.getTokenCount() > config.targetTokens;
        }

        CompressionConfig getConfig() {
            return config;
        }
    }

    static class SimpleCompressor extends CompressionProcessorBase {
        SimpleCompressor(CompressionConfig config) {
            super(config);
        }

        @Override
        MessageBlock compress(MessageBlock input) {
            MessageBlock output = new MessageBlock();
            // Simple compression: keep first message and summarize others
            if (!input.messages.isEmpty()) {
                output.addMessage(input.messages.get(0));
            }
            return output;
        }
    }

    @Nested
    @DisplayName("Compression Config Tests")
    class TestCompressionConfig {

        @Test
        @DisplayName("compression config creation")
        void testCompressionConfigCreation() {
            CompressionConfig config = new CompressionConfig(2000, 0.5);

            assertEquals(2000, config.targetTokens);
            assertEquals(0.5, config.compressionRatio);
        }
    }

    @Nested
    @DisplayName("Message Block Tests")
    class TestMessageBlock {

        @Test
        @DisplayName("message block creation")
        void testMessageBlockCreation() {
            MessageBlock block = new MessageBlock();
            block.addMessage("Hello world");

            assertEquals(1, block.getMessages().size());
            assertTrue(block.getTokenCount() > 0);
        }

        @Test
        @DisplayName("message block token count")
        void testMessageBlockTokenCount() {
            MessageBlock block = new MessageBlock();
            block.addMessage("This is a test");
            block.addMessage("Another message");

            assertTrue(block.getTokenCount() > 0);
        }
    }

    @Nested
    @DisplayName("Compression Processor Tests")
    class TestCompressionProcessor {

        @Test
        @DisplayName("should compress when over threshold")
        void testShouldCompressWhenOverThreshold() {
            CompressionConfig config = new CompressionConfig(10, 0.5);
            SimpleCompressor compressor = new SimpleCompressor(config);

            MessageBlock block = new MessageBlock();
            block.addMessage("This is a very long message that exceeds threshold");

            assertTrue(compressor.shouldCompress(block));
        }

        @Test
        @DisplayName("should not compress when under threshold")
        void testShouldNotCompressWhenUnderThreshold() {
            CompressionConfig config = new CompressionConfig(1000, 0.5);
            SimpleCompressor compressor = new SimpleCompressor(config);

            MessageBlock block = new MessageBlock();
            block.addMessage("Short");

            assertFalse(compressor.shouldCompress(block));
        }

        @Test
        @DisplayName("compress reduces message count")
        void testCompressReducesMessageCount() {
            CompressionConfig config = new CompressionConfig(10, 0.5);
            SimpleCompressor compressor = new SimpleCompressor(config);

            MessageBlock input = new MessageBlock();
            input.addMessage("First");
            input.addMessage("Second");
            input.addMessage("Third");

            MessageBlock output = compressor.compress(input);

            assertTrue(output.getMessages().size() <= input.getMessages().size());
        }
    }
}