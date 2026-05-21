/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl;

import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Shared test fixture for agent_rl unit tests.
 * <p>
 * Mirrors Python's {@code conftest.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/conftest.py}.
 */
public class AgentRlTestFixture {

    protected MockTokenizer mockTokenizer;

    @BeforeEach
    void setUpTokenizer() {
        mockTokenizer = createMockTokenizer();
    }

    public static MockTokenizer createMockTokenizer() {
        return new MockTokenizer();
    }

    /**
     * Mock tokenizer with applyChatTemplate and encode for RolloutEncoder/batch tests.
     * <p>
     * Mirrors the inner MockTokenizer class from Python's
     * {@code agent_rl/conftest.py::mock_tokenizer} fixture.
     */
    public static class MockTokenizer {

        public int padTokenId = 0;

        public String applyChatTemplate(
                List<Map<String, Object>> messages,
                boolean tokenize,
                boolean addGenerationPrompt,
                List<Object> tools) {
            if (messages != null && !messages.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (Map<String, Object> m : messages) {
                    String role = (String) m.getOrDefault("role", "user");
                    Object contentObj = m.getOrDefault("content", "");
                    String content;
                    if (contentObj == null) {
                        content = "";
                    } else if (contentObj instanceof List) {
                        List<?> contentList = (List<?>) contentObj;
                        StringBuilder sb = new StringBuilder();
                        for (Object c : contentList) {
                            if (c instanceof Map) {
                                sb.append(((Map<?, ?>) c).getOrDefault("text", String.valueOf(c)));
                            }
                        }
                        content = sb.toString();
                    } else {
                        content = contentObj.toString();
                    }
                    parts.add("<" + role + ">" + content);
                }
                return String.join(" ", parts) + (addGenerationPrompt ? " " : "");
            }
            return "";
        }

        public String applyChatTemplate(List<Map<String, Object>> messages, boolean addGenerationPrompt) {
            return applyChatTemplate(messages, false, addGenerationPrompt, null);
        }

        public String applyChatTemplate(List<Map<String, Object>> messages) {
            return applyChatTemplate(messages, false, true, null);
        }

        public List<Integer> encode(String text, boolean addSpecialTokens) {
            if (text == null || text.isEmpty()) {
                return Collections.emptyList();
            }
            List<Integer> ids = new ArrayList<>();
            int limit = Math.min(text.length(), 50);
            for (int i = 0; i < limit; i++) {
                ids.add(text.charAt(i) % 100);
            }
            return ids;
        }

        public List<Integer> encode(String text) {
            return encode(text, true);
        }
    }
}
