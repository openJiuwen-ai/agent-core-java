/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for tool description reviewer behavior.
 *
 * <p>Mirrors Python's {@code ToolDescriptionReviewer} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/customized_reviewer.py}.</p>
 */
class ToolDescriptionReviewerTest {

    @Test
    void constructorInitializesFieldsAndProcessors() {
        ToolDescriptionReviewer reviewer = new ToolDescriptionReviewer("eval-model", "key");

        assertEquals("eval-model", reviewer.getEvalModelId());
        assertEquals("key", reviewer.getLlmApiKey());
        assertTrue(reviewer.getProcessors().isEmpty());
    }

    @Test
    void detectsMostlyEnglishLikePythonHelper() {
        ToolDescriptionReviewer reviewer = new ToolDescriptionReviewer("eval", "key");

        assertTrue(reviewer.isMostlyEnglish("Search weather status"));
        assertFalse(reviewer.isMostlyEnglish("天气查询"));
        assertFalse(reviewer.isMostlyEnglish("   \n\t"));
    }

    @Test
    void formatBuildsPromptAndReturnsRawRitsResponse() {
        RecordingReviewer reviewer = new RecordingReviewer("eval", "key", "raw-json");
        Map<String, Object> schema = linkedMap("description", linkedMap("type", "string"));

        Object result = reviewer.format(schema, "Original description", "ignored example");

        assertEquals("raw-json", result);
        assertEquals("gpt-5.2", reviewer.calls.get(0).modelId);
        assertEquals("key", reviewer.calls.get(0).apiKey);
        assertTrue(reviewer.calls.get(0).prompt.contains("目标 JSON 结构"));
        assertTrue(reviewer.calls.get(0).prompt.contains("Original description"));
        assertTrue(reviewer.calls.get(0).prompt.contains("\"description\""));
        assertTrue(reviewer.calls.get(0).kwargs.containsKey("verify_output"));
        assertEquals(5, reviewer.calls.get(0).kwargs.get("max_attempts"));
        assertEquals(false, reviewer.calls.get(0).kwargs.get("include_stop_sequence"));
    }

    @Test
    void cleanCrossCheckAndTranslateUseExpectedModelsAndPrompts() {
        RecordingReviewer reviewer = new RecordingReviewer("eval-model", "key", linkedMap("done", true));
        Map<String, Object> data = linkedMap("description", "Search weather status");

        assertEquals(linkedMap("done", true), reviewer.cleanAndDeduplicate(data));
        assertEquals("eval-model", reviewer.calls.get(0).modelId);
        assertTrue(reviewer.calls.get(0).prompt.contains("Remove usage example"));

        assertEquals(linkedMap("done", true), reviewer.crossCheck(data, "original tool"));
        assertTrue(reviewer.calls.get(1).prompt.contains("原始描述"));
        assertTrue(reviewer.calls.get(1).prompt.contains("original tool"));

        assertEquals(linkedMap("done", true), reviewer.translateToChinese(data));
        assertTrue(reviewer.calls.get(2).prompt.contains("Translate all English text"));
    }

    @Test
    void translateReturnsOriginalDataWhenContentIsNotMostlyEnglish() {
        RecordingReviewer reviewer = new RecordingReviewer("eval", "key", linkedMap("translated", true));
        Map<String, Object> data = linkedMap("description", "天气");

        Object result = reviewer.translateToChinese(data);

        assertSame(data, result);
        assertTrue(reviewer.calls.isEmpty());
    }

    @Test
    void processPreservesPythonCrossCheckOriginalDataBehavior() {
        StepRecordingReviewer reviewer = new StepRecordingReviewer();
        Map<String, Object> original = linkedMap("description", "Search weather status");

        Object result = reviewer.process(original, "original tool", List.of("clean", "cross_check"));

        assertEquals("cross", result);
        assertEquals(List.of("clean", "cross_check"), reviewer.steps);
        assertSame(original, reviewer.cleanInput);
        assertSame(original, reviewer.crossInput);
    }

    @Test
    void processRejectsUnknownStep() {
        ToolDescriptionReviewer reviewer = new ToolDescriptionReviewer("eval", "key");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reviewer.process(linkedMap("description", "x"), "tool", List.of("unknown"))
        );

        assertEquals("Unknown processing step: unknown", exception.getMessage());
    }

    private static Map<String, Object> linkedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            map.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return map;
    }

    private static class RecordingReviewer extends ToolDescriptionReviewer {

        private final Object response;
        private final List<Call> calls = new ArrayList<>();

        private RecordingReviewer(String evalModelId, String llmApiKey, Object response) {
            super(evalModelId, llmApiKey);
            this.response = response;
        }

        @Override
        protected Object invokeRitsResponse(
                String modelId,
                String prompt,
                boolean verbose,
                Map<String, Object> kwargs
        ) {
            calls.add(new Call(modelId, getLlmApiKey(), prompt, verbose, kwargs));
            return response;
        }
    }

    private static final class StepRecordingReviewer extends ToolDescriptionReviewer {

        private final List<String> steps = new ArrayList<>();
        private Object cleanInput;
        private Object crossInput;

        private StepRecordingReviewer() {
            super("eval", "key");
        }

        @Override
        public Object cleanAndDeduplicate(Object data) {
            steps.add("clean");
            cleanInput = data;
            return "clean";
        }

        @Override
        public Object crossCheck(Object data, String oriTool) {
            steps.add("cross_check");
            crossInput = data;
            return "cross";
        }
    }

    private record Call(
            String modelId,
            String apiKey,
            String prompt,
            boolean verbose,
            Map<String, Object> kwargs
    ) {
    }
}
