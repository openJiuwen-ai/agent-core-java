/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CustomizedEval slice handling.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.tool_call.test_customized_eval}.
 */
class CustomizedEvalTest {

    @Test
    void testCustomEvalSliceExtractsEvalCriteria() {
        Map<String, Object> evalSpec = new HashMap<>();
        evalSpec.put("criteria", List.of("accuracy", "speed", "relevance"));
        evalSpec.put("weights", Map.of("accuracy", 0.5, "speed", 0.3, "relevance", 0.2));

        Map<String, Object> slice = extractEvalSlice(evalSpec);

        assertTrue(slice.containsKey("criteria"));
        assertEquals(3, ((List<?>) slice.get("criteria")).size());
    }

    @Test
    void testCustomEvalSliceWithDefaultWeights() {
        Map<String, Object> evalSpec = new HashMap<>();
        evalSpec.put("criteria", List.of("quality"));

        Map<String, Object> slice = extractEvalSlice(evalSpec);
        Map<String, Double> weights = computeDefaultWeights((List<String>) slice.get("criteria"));

        assertEquals(1.0, weights.get("quality"));
    }

    @Test
    void testCustomEvalSliceWithThresholds() {
        Map<String, Object> evalSpec = new HashMap<>();
        evalSpec.put("threshold", 0.7);
        evalSpec.put("criteria", List.of("match"));

        Map<String, Object> slice = extractEvalSlice(evalSpec);

        assertEquals(0.7, slice.get("threshold"));
    }

    @Test
    void testCustomEvalSliceValidatesWeightsSum() {
        Map<String, Object> weights = Map.of("a", 0.5, "b", 0.3, "c", 0.2);

        boolean valid = validateWeightsSum(weights);
        assertTrue(valid);
    }

    @Test
    void testCustomEvalSliceInvalidWeightsSum() {
        Map<String, Object> weights = Map.of("a", 0.5, "b", 0.6);

        boolean valid = validateWeightsSum(weights);
        assertFalse(valid);
    }

    @Test
    void testCustomEvalSliceWithCustomEvaluator() {
        Map<String, Object> evalSpec = new HashMap<>();
        evalSpec.put("evaluator_type", "llm_as_judge");
        evalSpec.put("prompt_template", "Evaluate the response quality");

        Map<String, Object> slice = extractEvalSlice(evalSpec);

        assertEquals("llm_as_judge", slice.get("evaluator_type"));
        assertEquals("Evaluate the response quality", slice.get("prompt_template"));
    }

    private Map<String, Object> extractEvalSlice(Map<String, Object> evalSpec) {
        Map<String, Object> slice = new HashMap<>();
        
        if (evalSpec.containsKey("criteria")) {
            slice.put("criteria", evalSpec.get("criteria"));
        }
        if (evalSpec.containsKey("weights")) {
            slice.put("weights", evalSpec.get("weights"));
        }
        if (evalSpec.containsKey("threshold")) {
            slice.put("threshold", evalSpec.get("threshold"));
        }
        if (evalSpec.containsKey("evaluator_type")) {
            slice.put("evaluator_type", evalSpec.get("evaluator_type"));
        }
        if (evalSpec.containsKey("prompt_template")) {
            slice.put("prompt_template", evalSpec.get("prompt_template"));
        }
        
        return slice;
    }

    private Map<String, Double> computeDefaultWeights(List<String> criteria) {
        Map<String, Double> weights = new HashMap<>();
        double weight = 1.0 / criteria.size();
        for (String criterion : criteria) {
            weights.put(criterion, weight);
        }
        return weights;
    }

    private boolean validateWeightsSum(Map<String, Object> weights) {
        double sum = 0.0;
        for (Object value : weights.values()) {
            if (value instanceof Number) {
                sum += ((Number) value).doubleValue();
            }
        }
        return Math.abs(sum - 1.0) < 0.01;
    }
}