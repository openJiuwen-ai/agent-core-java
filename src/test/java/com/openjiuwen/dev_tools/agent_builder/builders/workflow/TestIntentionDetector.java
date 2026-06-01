/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test IntentionDetector functionality.
 * <p>
 * Mirrors Python's {@code test_intention_detector.py} in
 * {@code tests.unit_tests.dev_tools.agent_builder.builders.workflow.test_intention_detector}.
 */
class TestIntentionDetector {

    @Nested
    class TestWorkflowIntentionDetector {

        private IntentionDetector detector(String content) {
            return new IntentionDetector(new MockLlm(content));
        }

        @Test
        void testFormatDialogHistory() {
            IntentionDetector detector = detector("{\"has_instruction\": true}");
            List<Map<String, Object>> dialogHistory = List.of(
                    Map.of("role", "user", "content", "Hello"),
                    Map.of("role", "assistant", "content", "Hi there!"),
                    Map.of("role", "system", "content", "System message")
            );

            String result = IntentionDetector.formatDialogHistory(dialogHistory);

            assertTrue(result.contains("User: Hello"));
            assertTrue(result.contains("Assistant: Hi there!"));
            assertTrue(result.contains("System: System message"));
        }

        @Test
        void testFormatDialogHistoryEmpty() {
            IntentionDetector detector = detector("{\"has_instruction\": true}");
            assertEquals("", IntentionDetector.formatDialogHistory(new ArrayList<>()));
        }

        @Test
        void testFormatDialogHistoryUnknownRole() {
            IntentionDetector detector = detector("{\"has_instruction\": true}");
            List<Map<String, Object>> dialogHistory = List.of(Map.of("role", "unknown", "content", "Test"));

            String result = IntentionDetector.formatDialogHistory(dialogHistory);
            assertTrue(result.contains("User: Test"));
        }

        @Test
        void testExtractIntentWithJsonBlock() {
            String inputText = "```json\n{\"has_instruction\": true}\n```";
            Map<String, Object> result = IntentionDetector.extractIntent(inputText);
            assertEquals(Map.of("has_instruction", true), result);
        }

        @Test
        void testExtractIntentWithoutJsonBlock() {
            String inputText = "{\"has_instruction\": false}";
            Map<String, Object> result = IntentionDetector.extractIntent(inputText);
            assertEquals(Map.of("has_instruction", false), result);
        }

        @Test
        void testDetectInitialInstruction() {
            IntentionDetector detector = detector("```json\n{\"provide_process\": true}\n```");
            boolean result = detector.detectInitialInstruction(List.of(
                    Map.of("role", "user", "content", "创建一个数据处理工作流")
            ));
            assertTrue(result);
        }

        @Test
        void testDetectRefineIntentTrue() {
            IntentionDetector detector = detector("```json\n{\"need_refined\": true}\n```");
            boolean result = detector.detectRefineIntent(
                    List.of(Map.of("role", "user", "content", "修改节点")),
                    "graph TD; A-->B"
            );
            assertTrue(result);
        }

        @Test
        void testDetectRefineIntentFalse() {
            IntentionDetector detector = detector("```json\n{\"need_refined\": false}\n```");
            boolean result = detector.detectRefineIntent(
                    List.of(Map.of("role", "user", "content", "确认")),
                    "graph TD; A-->B"
            );
            assertFalse(result);
        }
    }

    static final class MockLlm {
        private final String content;

        MockLlm(String content) {
            this.content = content;
        }

        public MockResponse invoke(Object ignored) {
            return new MockResponse(content);
        }
    }

    record MockResponse(String content) {
        public String getContent() {
            return content;
        }
    }
}
