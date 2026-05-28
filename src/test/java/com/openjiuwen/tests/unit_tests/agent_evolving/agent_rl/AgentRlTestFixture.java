/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code conftest.py} in {@code tests.unit_tests.agent_evolving.agent_rl}.
 * 
 * Shared fixtures for agent_rl unit tests.
 */
@Disabled("Requires RL configuration")
class AgentRlTestFixture {

    /**
     * Mock tokenizer with apply_chat_template and encode for RolloutEncoder/batch tests.
     */
    static class MockTokenizer {
        public int padTokenId = 0;

        /**
         * Apply chat template to messages.
         */
        public String applyChatTemplate(List<Map<String, Object>> messages, 
                                        boolean tokenize, 
                                        boolean addGenerationPrompt,
                                        Object tools) {
            if (messages != null && !messages.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (Map<String, Object> m : messages) {
                    String role = (String) m.getOrDefault("role", "user");
                    Object contentObj = m.get("content");
                    String content = "";
                    if (contentObj instanceof String) {
                        content = (String) contentObj;
                    } else if (contentObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentObj;
                        List<String> textParts = new ArrayList<>();
                        for (Map<String, Object> c : contentList) {
                            textParts.add((String) c.getOrDefault("text", String.valueOf(c)));
                        }
                        content = String.join(" ", textParts);
                    }
                    parts.add("<" + role + ">" + content);
                }
                String result = String.join(" ", parts);
                return addGenerationPrompt ? result + " " : result;
            }
            return "";
        }

        /**
         * Encode text to token IDs.
         */
        public List<Integer> encode(String text, boolean addSpecialTokens) {
            if (text == null || text.isEmpty()) {
                return new ArrayList<>();
            }
            List<Integer> tokens = new ArrayList<>();
            int limit = Math.min(text.length(), 50);
            for (int i = 0; i < limit; i++) {
                tokens.add((int) text.charAt(i) % 100);
            }
            return tokens;
        }
    }

    @Test
    @DisplayName("Test mock tokenizer apply chat template")
    void testMockTokenizerApplyChatTemplate() {
        MockTokenizer tokenizer = new MockTokenizer();
        
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "Hello"));
        messages.add(Map.of("role", "assistant", "content", "Hi there"));
        
        String result = tokenizer.applyChatTemplate(messages, false, true, null);
        assertTrue(result.contains("<user>Hello"));
        assertTrue(result.contains("<assistant>Hi there"));
    }

    @Test
    @DisplayName("Test mock tokenizer encode")
    void testMockTokenizerEncode() {
        MockTokenizer tokenizer = new MockTokenizer();
        
        List<Integer> tokens = tokenizer.encode("Hello", true);
        assertNotNull(tokens);
        assertEquals(5, tokens.size());
    }

    @Test
    @DisplayName("Test mock tokenizer encode empty")
    void testMockTokenizerEncodeEmpty() {
        MockTokenizer tokenizer = new MockTokenizer();
        
        List<Integer> tokens = tokenizer.encode("", true);
        assertNotNull(tokens);
        assertTrue(tokens.isEmpty());
    }

    @Test
    @DisplayName("Placeholder test")
    void testPlaceholder() {
        assertTrue(true);
    }
}
