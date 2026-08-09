/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for simple tool-call evaluator.
 *
 * <p>Mirrors Python's {@code SimpleEval} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/customized_eval.py}.</p>
 */
class SimpleEvalTest {

    @Test
    void rejectsWeightsThatDoNotSumToOne() {
        assertThrows(IllegalArgumentException.class, () -> new SimpleEval(null, Map.of(), 0.8d, 0.6d));
    }

    @Test
    void evaluatesExamplesAndAggregatesScores() {
        RecordingSimpleEval eval = new RecordingSimpleEval(
                (SimpleEval.ApiWrapper) (tool, call) -> new Object[] {"{\"answer\":\"ready\"}", 0},
                Map.of("eval_model_id", "eval", "llm_api_key", "key")
        );
        Map<String, Object> expectedCall = Map.of("name", "search", "arguments", Map.of("query", "status"));

        Map<String, Object> result = eval.evaluate(
                Map.of("name", "search"),
                "Find status",
                List.<Object[]>of(new Object[] {"find status", expectedCall, "{\"answer\":\"ready\"}", "ready"}),
                1
        );

        assertEquals(100.0d, (Double) result.get("score_avg"), 1e-9);
        assertEquals(100.0d, (Double) result.get("fn_call_accuracy"), 1e-9);
        assertEquals(100.0d, (Double) result.get("output_effectiveness"), 1e-9);
        assertEquals(1, ((List<?>) result.get("results")).size());
    }

    @Test
    void functionCallAccuracyParsesArgumentJsonAndUsesTolerance() {
        SimpleEval eval = new SimpleEval(null, Map.of());
        Map<String, Object> generated = Map.of("name", "search", "arguments", "{\"count\":1,\"query\":\"Status\"}");
        Map<String, Object> expected = Map.of("name", "search", "arguments", Map.of("count", 1.0d, "query", "status"));

        assertEquals(1.0d, eval.evaluateFunctionCallAccuracy(generated, expected), 1e-9);
        assertTrue(SimpleEval.compareParameterValues(1, 1.0000001d));
        assertEquals(1.0d, SimpleEval.simpleOutputComparison(Map.of("answer", "ready"), "ready"), 1e-9);
    }

    @Test
    void missingApiWrapperReturnsPythonStyleErrorResult() {
        RecordingSimpleEval eval = new RecordingSimpleEval(null, Map.of());
        Map<String, Object> expectedCall = Map.of("name", "search", "arguments", Map.of());

        Map<String, Object> result = eval.evaluate(
                Map.of("name", "search"),
                "Find status",
                List.<Object[]>of(new Object[] {"find status", expectedCall, "", "ready"}),
                1
        );

        List<?> results = (List<?>) result.get("results");
        Map<?, ?> first = (Map<?, ?>) results.get(0);
        assertEquals(0.0d, first.get("weighted_score"));
        assertTrue(String.valueOf(first.get("execution_error")).contains("api_wrapper"));
    }

    private static final class RecordingSimpleEval extends SimpleEval {

        private RecordingSimpleEval(Object apiWrapper, Map<String, Object> config) {
            super(apiWrapper, config);
        }

        @Override
        protected Map<String, Object> generateFunctionCall(
                Map<String, Object> tool,
                String description,
                String instruction
        ) {
            return Map.of("name", tool.get("name"), "arguments", Map.of("query", "status"));
        }

        @Override
        protected double evaluateOutputEffectiveness(
                String instruction,
                Object executionResult,
                Object executionError,
                String expectedAnswer
        ) {
            return executionError == null ? 1.0d : 0.0d;
        }
    }
}
