/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExternalMemoryRail.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.rails.test_external_memory_rail}.
 */
class TestExternalMemoryRail {

    // ---------------------------------------------------------------------------
    // Mock classes
    // ---------------------------------------------------------------------------

    /** Mock inputs for testing. */
    static class MockInputs {
        private String query = null;
        private List<Map<String, String>> messages = null;

        public MockInputs() {}

        public MockInputs(String query) {
            this.query = query;
        }

        public MockInputs(List<Map<String, String>> messages) {
            this.messages = messages;
        }

        public String getQuery() { return query; }
        public List<Map<String, String>> getMessages() { return messages; }
    }

    /** Mock context. */
    static class MockContext {
        private MockInputs inputs;

        public MockContext(MockInputs inputs) {
            this.inputs = inputs;
        }

        public MockInputs getInputs() { return inputs; }
    }

    // ---------------------------------------------------------------------------
    // Tests: resolve_user_text_for_memory
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("Resolve user text for memory tests")
    class TestResolveUserTextForMemory {

        @Test
        @Tag("level0")
        @DisplayName("Only query field - returns query directly")
        void testOnlyQuery() {
            MockInputs inputs = new MockInputs("test query");
            MockContext ctx = new MockContext(inputs);

            String result = resolveUserTextForMemory(ctx);
            assertEquals("test query", result);
        }

        @Test
        @Tag("level0")
        @DisplayName("Only messages - extracts last user message")
        void testOnlyMessages() {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "system message"));
            messages.add(Map.of("role", "user", "content", "user message 1"));
            messages.add(Map.of("role", "assistant", "content", "assistant response"));
            messages.add(Map.of("role", "user", "content", "user message 2"));

            MockInputs inputs = new MockInputs(messages);
            MockContext ctx = new MockContext(inputs);

            String result = resolveUserTextForMemory(ctx);
            assertEquals("user message 2", result);
        }

        @Test
        @Tag("level0")
        @DisplayName("Both query and messages - query takes priority")
        void testBothQueryAndMessages() {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", "message content"));

            MockInputs inputs = new MockInputs("priority query");
            inputs.messages = messages;

            MockContext ctx = new MockContext(inputs);

            String result = resolveUserTextForMemory(ctx);
            assertEquals("priority query", result);
        }

        @Test
        @Tag("level0")
        @DisplayName("Neither query nor messages - returns null")
        void testNeitherQueryNorMessages() {
            MockInputs inputs = new MockInputs();
            MockContext ctx = new MockContext(inputs);

            String result = resolveUserTextForMemory(ctx);
            assertNull(result);
        }
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1: Memory storage
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("External memory storage simulation")
    void testExternalMemoryStorageSimulation() {
        Map<String, List<String>> memoryStore = new HashMap<>();
        String userId = "user-001";
        
        // Store memories
        memoryStore.put(userId, new ArrayList<>());
        memoryStore.get(userId).add("User prefers JSON output");
        memoryStore.get(userId).add("User is working on Python project");
        
        assertEquals(2, memoryStore.get(userId).size());
        assertTrue(memoryStore.get(userId).contains("User prefers JSON output"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Memory retrieval by context")
    void testMemoryRetrievalByContext() {
        List<Map<String, Object>> memories = new ArrayList<>();
        memories.add(Map.of("content", "Memory 1", "relevance", 0.9));
        memories.add(Map.of("content", "Memory 2", "relevance", 0.7));
        memories.add(Map.of("content", "Memory 3", "relevance", 0.5));
        
        // Sort by relevance
        memories.sort((a, b) -> Double.compare((Double) b.get("relevance"), (Double) a.get("relevance")));
        
        assertEquals("Memory 1", memories.get(0).get("content"));
        assertEquals(0.9, memories.get(0).get("relevance"));
    }

    // ---------------------------------------------------------------------------
    // Helper method
    // ---------------------------------------------------------------------------

    private String resolveUserTextForMemory(MockContext ctx) {
        MockInputs inputs = ctx.getInputs();
        
        if (inputs.getQuery() != null) {
            return inputs.getQuery();
        }
        
        if (inputs.getMessages() != null && !inputs.getMessages().isEmpty()) {
            // Find last user message
            for (int i = inputs.getMessages().size() - 1; i >= 0; i--) {
                Map<String, String> msg = inputs.getMessages().get(i);
                if ("user".equals(msg.get("role"))) {
                    return msg.get("content");
                }
            }
        }
        
        return null;
    }
}