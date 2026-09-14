/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TestWorkflowIntentionDetectorIntegration} and related
 * groups in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/test_intention_detector_integration.py}.
 */
class WorkflowIntentionDetectorIntegrationTest {

    @Nested
    class TestWorkflowIntentionDetectorIntegration {

        @Test
        void testIntentionDetectorInitialization() throws ReflectiveOperationException {
            Model mockLlm = modelReturning("{\"provide_process\": true}");
            IntentionDetector detector = new IntentionDetector(mockLlm);

            assertSame(mockLlm, fieldValue(detector, "llm"));
        }

        @Test
        void testDetectInitialInstructionEmptyHistory() {
            IntentionDetector detector = new IntentionDetector(modelReturning("{\"provide_process\": true}"));

            boolean result = detector.detectInitialInstruction(List.of());

            assertFalse(result);
        }

        @Test
        void testDetectInitialInstructionWithHistory() {
            IntentionDetector detector = new IntentionDetector(modelReturning("{\"provide_process\": true}"));
            List<Map<String, Object>> dialogHistory = List.of(Map.of("role", "user", "content", "create workflow"));

            Boolean result = detector.detectInitialInstruction(dialogHistory);

            assertInstanceOf(Boolean.class, result);
        }

        @Test
        void testDetectRefineIntentEmptyHistory() {
            IntentionDetector detector = new IntentionDetector(modelReturning("{\"need_refined\": true}"));

            boolean result = detector.detectRefineIntent(List.of(), "mermaid code");

            assertFalse(result);
        }

        @Test
        void testDetectRefineIntentWithHistory() {
            IntentionDetector detector = new IntentionDetector(modelReturning("{\"need_refined\": true}"));
            List<Map<String, Object>> dialogHistory = List.of(Map.of("role", "user", "content", "modify workflow"));

            Boolean result = detector.detectRefineIntent(dialogHistory, "graph TD");

            assertInstanceOf(Boolean.class, result);
        }
    }

    @Nested
    class TestWorkflowIntentionDetectorExtractIntent {

        @Test
        void testExtractIntentWithJsonBlock() {
            String input = """
                    ```json
                    {"provide_process": true}
                    ```
                    """;

            Map<String, Object> result = IntentionDetector.extractIntent(input);

            assertInstanceOf(Map.class, result);
            assertTrue((Boolean) result.get("provide_process"));
        }

        @Test
        void testExtractIntentWithoutJsonBlock() {
            String input = "{\"provide_process\": false}";

            Map<String, Object> result = IntentionDetector.extractIntent(input);

            assertInstanceOf(Map.class, result);
            assertFalse((Boolean) result.get("provide_process"));
        }
    }

    @Nested
    class TestWorkflowIntentionDetectorFormatDialogHistory {

        @Test
        void testFormatDialogHistoryUser() {
            List<Map<String, Object>> dialogHistory = List.of(Map.of("role", "user", "content", "test message"));

            String result = IntentionDetector.formatDialogHistory(dialogHistory);

            assertTrue(result.contains("User: test message"));
        }

        @Test
        void testFormatDialogHistoryAssistant() {
            List<Map<String, Object>> dialogHistory = List.of(Map.of("role", "assistant", "content", "response"));

            String result = IntentionDetector.formatDialogHistory(dialogHistory);

            assertTrue(result.contains("Assistant: response"));
        }

        @Test
        void testFormatDialogHistoryMixed() {
            List<Map<String, Object>> dialogHistory = List.of(
                    Map.of("role", "user", "content", "question"),
                    Map.of("role", "assistant", "content", "answer"));

            String result = IntentionDetector.formatDialogHistory(dialogHistory);

            assertTrue(result.contains("User: question"));
            assertTrue(result.contains("Assistant: answer"));
        }
    }

    private static Model modelReturning(String response) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(response)));
    }

    private static Object fieldValue(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
