/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import com.openjiuwen.agent_evolving.optimizer.tool_call.utils.ToolDescriptionReviewer;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CustomizedReviewer slice handling.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.tool_call.test_customized_reviewer}.
 */
class CustomizedReviewerTest {

    @Test
    void testFormatCleanCrossCheckTranslate() {
        RecordingReviewer reviewer = new RecordingReviewer(
                "gpt-eval",
                "k",
                "{\"name\":\"f\",\"description\":\"d\",\"parameters\":{}}",
                "{\"name\":\"f\",\"description\":\"d2\",\"parameters\":{}}",
                "{\"name\":\"f\",\"description\":\"d3\",\"parameters\":{}}",
                "{\"name\":\"f\",\"description\":\"translated\",\"parameters\":{}}"
        );

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", "");
        schema.put("description", "");
        schema.put("parameters", new LinkedHashMap<>());

        Map<String, Object> formatted = reviewer.format(schema, "raw desc", null);
        assertEquals("f", formatted.get("name"));
        assertEquals("gpt-5.2", reviewer.calls.get(0).modelId);
        assertTrue(reviewer.calls.get(0).prompt.contains("raw desc"));

        Map<String, Object> cleaned = reviewer.cleanAndDeduplicate(formatted);
        assertEquals("d2", cleaned.get("description"));

        Map<String, Object> checked = reviewer.crossCheck(cleaned, "ori");
        assertEquals("d3", checked.get("description"));

        reviewer.forcedMostlyEnglish = true;
        Map<String, Object> translated = reviewer.translateToChinese(Map.of("description", "hello world text only"));
        assertEquals("translated", translated.get("description"));

        reviewer.forcedMostlyEnglish = false;
        Map<String, Object> noTranslate = reviewer.translateToChinese(Map.of("description", "already-localized"));
        assertEquals(Map.of("description", "already-localized"), noTranslate);
        assertEquals(4, reviewer.calls.size());
    }

    @Test
    void testProcessWithStepsAndUnknownStep() {
        ProcessReviewer reviewer = new ProcessReviewer();
        Map<String, Object> input = Map.of("a", 1);

        Map<String, Object> out = reviewer.process(input, "ori", List.of("clean", "translate"));
        assertEquals(Map.of("t", Map.of("c", input)), out);

        Map<String, Object> out2 = reviewer.process(input, "ori", List.of("cross_check"));
        assertEquals("ori", out2.get("ori"));

        Map<String, Object> out3 = reviewer.process(input, "ori", List.of("clean", "cross_check"));
        assertEquals(input, out3.get("x"));

        assertThrows(IllegalArgumentException.class, () -> reviewer.process(input, "ori", List.of("bad")));
    }

    @Test
    void testMostlyEnglishMatchesPythonRatioRule() {
        RecordingReviewer reviewer = new RecordingReviewer("gpt-eval", "k");

        assertFalse(reviewer.mostlyEnglish(""));
        assertFalse(reviewer.mostlyEnglish("12345 !!!"));
        assertTrue(reviewer.mostlyEnglish("plain English text"));
    }

    @Test
    void testCustomReviewerSliceExtractsReviewCriteria() {
        Map<String, Object> reviewerSpec = new HashMap<>();
        reviewerSpec.put("criteria", List.of("correctness", "completeness", "relevance"));
        reviewerSpec.put("min_score", 0.7);

        Map<String, Object> slice = extractReviewerSlice(reviewerSpec);

        assertEquals(3, ((List<?>) slice.get("criteria")).size());
        assertEquals(0.7, slice.get("min_score"));
    }

    @Test
    void testCustomReviewerSliceWithReviewPrompt() {
        Map<String, Object> reviewerSpec = new HashMap<>();
        reviewerSpec.put("review_prompt", "Review the output for quality issues");

        Map<String, Object> slice = extractReviewerSlice(reviewerSpec);

        assertEquals("Review the output for quality issues", slice.get("review_prompt"));
    }

    @Test
    void testCustomReviewerSliceWithAutoApproval() {
        Map<String, Object> reviewerSpec = new HashMap<>();
        reviewerSpec.put("auto_approve_threshold", 0.95);

        Map<String, Object> slice = extractReviewerSlice(reviewerSpec);

        assertEquals(0.95, slice.get("auto_approve_threshold"));
    }

    @Test
    void testCustomReviewerSliceWithFeedbackTemplate() {
        Map<String, Object> reviewerSpec = new HashMap<>();
        reviewerSpec.put("feedback_template", "Issues found: {issues}. Suggestions: {suggestions}");

        Map<String, Object> slice = extractReviewerSlice(reviewerSpec);

        assertTrue(((String) slice.get("feedback_template")).contains("{issues}"));
    }

    @Test
    void testCustomReviewerSliceEmptyCriteria() {
        Map<String, Object> reviewerSpec = new HashMap<>();
        reviewerSpec.put("criteria", new ArrayList<>());

        Map<String, Object> slice = extractReviewerSlice(reviewerSpec);

        assertTrue(((List<?>) slice.get("criteria")).isEmpty());
    }

    @Test
    void testCustomReviewerSliceWithMultipleReviewers() {
        Map<String, Object> reviewerSpec = new HashMap<>();
        reviewerSpec.put("reviewers", List.of(
            Map.of("name", "primary", "weight", 0.6),
            Map.of("name", "secondary", "weight", 0.4)
        ));

        Map<String, Object> slice = extractReviewerSlice(reviewerSpec);

        assertEquals(2, ((List<?>) slice.get("reviewers")).size());
    }

    private Map<String, Object> extractReviewerSlice(Map<String, Object> reviewerSpec) {
        Map<String, Object> slice = new HashMap<>();
        
        if (reviewerSpec.containsKey("criteria")) {
            slice.put("criteria", reviewerSpec.get("criteria"));
        }
        if (reviewerSpec.containsKey("min_score")) {
            slice.put("min_score", reviewerSpec.get("min_score"));
        }
        if (reviewerSpec.containsKey("review_prompt")) {
            slice.put("review_prompt", reviewerSpec.get("review_prompt"));
        }
        if (reviewerSpec.containsKey("auto_approve_threshold")) {
            slice.put("auto_approve_threshold", reviewerSpec.get("auto_approve_threshold"));
        }
        if (reviewerSpec.containsKey("feedback_template")) {
            slice.put("feedback_template", reviewerSpec.get("feedback_template"));
        }
        if (reviewerSpec.containsKey("reviewers")) {
            slice.put("reviewers", reviewerSpec.get("reviewers"));
        }
        
        return slice;
    }

    private static final class RecordingReviewer extends ToolDescriptionReviewer {
        private final Deque<String> responses = new ArrayDeque<>();
        private final List<RitsCall> calls = new ArrayList<>();
        private Boolean forcedMostlyEnglish;

        private RecordingReviewer(String evalModelId, String llmApiKey, String... responses) {
            super(evalModelId, llmApiKey);
            this.responses.addAll(Arrays.asList(responses));
        }

        @Override
        protected Object invokeRitsResponse(String modelId, String prompt, Function<String, Object> verifyFn) {
            calls.add(new RitsCall(modelId, prompt));
            return verifyFn.apply(responses.removeFirst());
        }

        @Override
        protected boolean isMostlyEnglish(String text) {
            return forcedMostlyEnglish != null ? forcedMostlyEnglish : super.isMostlyEnglish(text);
        }

        private boolean mostlyEnglish(String text) {
            return super.isMostlyEnglish(text);
        }
    }

    private static final class ProcessReviewer extends ToolDescriptionReviewer {
        private ProcessReviewer() {
            super("gpt-eval", "k");
        }

        @Override
        public Map<String, Object> cleanAndDeduplicate(Map<String, Object> data) {
            return Map.of("c", data);
        }

        @Override
        public Map<String, Object> crossCheck(Map<String, Object> data, String oriTool) {
            return Map.of("x", data, "ori", oriTool);
        }

        @Override
        public Map<String, Object> translateToChinese(Map<String, Object> data) {
            return Map.of("t", data);
        }
    }

    private record RitsCall(String modelId, String prompt) {
    }
}
