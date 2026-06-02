/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import com.openjiuwen.harness.rails.memory.ExternalMemoryRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ExternalMemoryRail.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.rails.test_external_memory_rail}.
 */
class TestExternalMemoryRail {

    static class MockInputs {
        Object query;
        Object messages;
        Object result;
    }

    static class MockContext {
        Object inputs;

        MockContext(Object inputs) {
            this.inputs = inputs;
        }
    }

    @Nested
    @DisplayName("Resolve user text for memory tests")
    class TestResolveUserTextForMemory {

        @Test
        @Tag("level0")
        void testOnlyQuery() {
            MockInputs inputs = new MockInputs();
            inputs.query = "test query";
            assertEquals("test query", ExternalMemoryRail.resolveUserTextForMemory(new MockContext(inputs)));
        }

        @Test
        @Tag("level0")
        void testOnlyMessages() {
            MockInputs inputs = new MockInputs();
            inputs.messages = List.of(
                    Map.of("role", "assistant", "content", "response"),
                    Map.of("role", "user", "content", "test message")
            );
            assertEquals("test message", ExternalMemoryRail.resolveUserTextForMemory(new MockContext(inputs)));
        }

        @Test
        @Tag("level0")
        void testBothQueryAndMessages() {
            MockInputs inputs = new MockInputs();
            inputs.query = "query value";
            inputs.messages = List.of(Map.of("role", "user", "content", "message value"));
            assertEquals("query value", ExternalMemoryRail.resolveUserTextForMemory(new MockContext(inputs)));
        }

        @Test
        @Tag("level0")
        void testBothEmpty() {
            assertEquals("", ExternalMemoryRail.resolveUserTextForMemory(new MockContext(new MockInputs())));
        }

        @Test
        @Tag("level0")
        void testMessagesWithListContent() {
            MockInputs inputs = new MockInputs();
            inputs.messages = List.of(Map.of(
                    "role", "user",
                    "content", List.of(Map.of("type", "text", "text", "hello world"))
            ));
            assertEquals("hello world", ExternalMemoryRail.resolveUserTextForMemory(new MockContext(inputs)));
        }

        @Test
        @Tag("level0")
        void testMessagesWithMultipleUserTakeLast() {
            MockInputs inputs = new MockInputs();
            inputs.messages = List.of(
                    Map.of("role", "user", "content", "first"),
                    Map.of("role", "user", "content", "last")
            );
            assertEquals("last", ExternalMemoryRail.resolveUserTextForMemory(new MockContext(inputs)));
        }
    }

    @Nested
    @DisplayName("Extract assistant output tests")
    class TestExtractAssistantOutput {

        @Test
        @Tag("level0")
        void testResultWithOutputKey() {
            MockInputs inputs = new MockInputs();
            inputs.result = Map.of("output", "assistant response");
            assertEquals("assistant response", ExternalMemoryRail.extractAssistantOutput(new MockContext(inputs)));
        }

        @Test
        @Tag("level0")
        void testResultWithMessageContent() {
            MockInputs inputs = new MockInputs();
            inputs.result = Map.of("message", Map.of("content", "assistant response"));
            assertEquals("assistant response", ExternalMemoryRail.extractAssistantOutput(new MockContext(inputs)));
        }

        @Test
        @Tag("level0")
        void testResultWithContentKey() {
            MockInputs inputs = new MockInputs();
            inputs.result = Map.of("content", "assistant response");
            assertEquals("assistant response", ExternalMemoryRail.extractAssistantOutput(new MockContext(inputs)));
        }

        @Test
        @Tag("level0")
        void testResultMissing() {
            assertEquals("", ExternalMemoryRail.extractAssistantOutput(new MockContext(new MockInputs())));
        }

        @Test
        @Tag("level0")
        void testResultWithUnknownKeys() {
            MockInputs inputs = new MockInputs();
            inputs.result = Map.of("unknown", "value", "other", 123);
            assertEquals("", ExternalMemoryRail.extractAssistantOutput(new MockContext(inputs)));
        }
    }

    @Nested
    @DisplayName("Build memory context block tests")
    class TestBuildMemoryContextBlock {

        @Test
        @Tag("level0")
        void testBuildMemoryContext() {
            String result = ExternalMemoryRail.buildMemoryContextBlock("Previous conversation context");
            assertTrue(result.contains("<memory-context>"));
            assertTrue(result.contains("Previous conversation context"));
            assertTrue(result.contains("</memory-context>"));
            assertTrue(result.contains("NOT new user input"));
        }
    }
}
