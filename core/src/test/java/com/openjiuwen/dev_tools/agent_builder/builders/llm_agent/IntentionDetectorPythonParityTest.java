/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.common.exception.ApplicationError;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code TestIntentionDetector} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/llm_agent/test_intention_detector.py}.</p>
 */
class IntentionDetectorPythonParityTest {

    @Test
    void extractIntentWithJsonBlock() {
        String inputText = """
                ```json
                {"need_refined": true}
                ```
                """;

        Map<String, Object> result = IntentionDetector.extractIntent(inputText);

        assertEquals(Map.of("need_refined", Boolean.TRUE), result);
    }

    @Test
    void extractIntentWithoutJsonBlock() {
        String inputText = "{\"need_refined\": false}";

        Map<String, Object> result = IntentionDetector.extractIntent(inputText);

        assertEquals(Map.of("need_refined", Boolean.FALSE), result);
    }

    @Test
    void extractIntentWithMultilineJson() {
        String inputText = """
                ```json
                {
                  "need_refined": true,
                  "reason": "test"
                }
                ```
                """;

        Map<String, Object> result = IntentionDetector.extractIntent(inputText);

        assertEquals(Boolean.TRUE, result.get("need_refined"));
        assertEquals("test", result.get("reason"));
    }

    @Test
    void detectRefineIntentEmptyQuery() {
        List<List<BaseMessage>> capturedMessages = new ArrayList<>();
        IntentionDetector detector = new IntentionDetector(modelReturning(capturedMessages, "{\"need_refined\": true}"));

        boolean result = detector.detectRefineIntent("", "some config");

        assertFalse(result);
        assertTrue(capturedMessages.isEmpty());
    }

    @Test
    void detectRefineIntentNullQuery() {
        List<List<BaseMessage>> capturedMessages = new ArrayList<>();
        IntentionDetector detector = new IntentionDetector(modelReturning(capturedMessages, "{\"need_refined\": true}"));

        boolean result = detector.detectRefineIntent(null, "some config");

        assertFalse(result);
        assertTrue(capturedMessages.isEmpty());
    }

    @Test
    void detectRefineIntentReturnsTrue() {
        IntentionDetector detector = new IntentionDetector(modelReturning(
                new ArrayList<>(),
                "```json\n{\"need_refined\": true}\n```"
        ));

        boolean result = detector.detectRefineIntent("修改配置", "current config");

        assertTrue(result);
    }

    @Test
    void detectRefineIntentReturnsFalse() {
        IntentionDetector detector = new IntentionDetector(modelReturning(
                new ArrayList<>(),
                "```json\n{\"need_refined\": false}\n```"
        ));

        boolean result = detector.detectRefineIntent("确认", "current config");

        assertFalse(result);
    }

    @Test
    void detectRefineIntentHandlesException() {
        IntentionDetector detector = new IntentionDetector(modelThrowing(new RuntimeException("Test error")));

        ApplicationError error = assertThrows(
                ApplicationError.class,
                () -> detector.detectRefineIntent("test query", "config")
        );

        assertTrue(error.getMessage().contains("Test error"));
    }

    private static Model modelReturning(List<List<BaseMessage>> capturedMessages, String response) {
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            capturedMessages.add(new ArrayList<>(messages));
            return CompletableFuture.completedFuture(new AssistantMessage(response));
        });
    }

    private static Model modelThrowing(RuntimeException exception) {
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            throw exception;
        });
    }
}
