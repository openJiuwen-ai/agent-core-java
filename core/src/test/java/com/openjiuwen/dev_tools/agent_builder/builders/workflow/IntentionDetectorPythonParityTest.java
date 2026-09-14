/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code TestWorkflowIntentionDetector} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_intention_detector.py}.</p>
 */
class IntentionDetectorPythonParityTest {

    @Test
    void formatDialogHistory() {
        List<Map<String, Object>> dialogHistory = List.of(
                message("user", "Hello"),
                message("assistant", "Hi there!"),
                message("system", "System message")
        );

        String result = IntentionDetector.formatDialogHistory(dialogHistory);

        assertTrue(result.contains("User: Hello"));
        assertTrue(result.contains("Assistant: Hi there!"));
        assertTrue(result.contains("System: System message"));
    }

    @Test
    void formatDialogHistoryEmpty() {
        String result = IntentionDetector.formatDialogHistory(List.of());

        assertEquals("", result);
    }

    @Test
    void formatDialogHistoryUnknownRole() {
        List<Map<String, Object>> dialogHistory = List.of(message("unknown", "Test"));

        String result = IntentionDetector.formatDialogHistory(dialogHistory);

        assertTrue(result.contains("User: Test"));
    }

    @Test
    void extractIntentWithJsonBlock() {
        String inputText = """
                ```json
                {"has_instruction": true}
                ```
                """;

        Map<String, Object> result = IntentionDetector.extractIntent(inputText);

        assertEquals(Map.of("has_instruction", Boolean.TRUE), result);
    }

    @Test
    void extractIntentWithoutJsonBlock() {
        String inputText = "{\"has_instruction\": false}";

        Map<String, Object> result = IntentionDetector.extractIntent(inputText);

        assertEquals(Map.of("has_instruction", Boolean.FALSE), result);
    }

    @Test
    void detectInitialInstruction() {
        IntentionDetector detector = new IntentionDetector(modelReturning("```json\n{\"has_instruction\": true}\n```"));

        boolean result = detector.detectInitialInstruction(List.of(message("user", "创建一个数据处理工作流")));

        assertFalse(result);
    }

    @Test
    void detectRefineIntentTrue() {
        IntentionDetector detector = new IntentionDetector(modelReturning("```json\n{\"need_refined\": true}\n```"));

        boolean result = detector.detectRefineIntent(
                List.of(message("user", "修改节点")),
                "graph TD; A-->B"
        );

        assertTrue(result);
    }

    @Test
    void detectRefineIntentFalse() {
        IntentionDetector detector = new IntentionDetector(modelReturning("```json\n{\"need_refined\": false}\n```"));

        boolean result = detector.detectRefineIntent(
                List.of(message("user", "确认")),
                "graph TD; A-->B"
        );

        assertFalse(result);
    }

    private static Map<String, Object> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    private static Model modelReturning(String response) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(response)));
    }
}
