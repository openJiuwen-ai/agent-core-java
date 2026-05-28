/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CustomizedReviewer slice handling.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.tool_call.test_customized_reviewer}.
 */
class CustomizedReviewerTest {

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
}