/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator;

import com.openjiuwen.agentevolving.dataset.Case;
import com.openjiuwen.agentevolving.dataset.EvaluatedCase;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Missing-test parity coverage for {@link BaseEvaluator} and {@link DefaultEvaluator}.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_evolving/evaluator/test_default_evaluator.py}.</p>
 */
class DefaultEvaluatorPythonParityTest {

    @Test
    void batchEvaluateRaisesForCasesLongerThanPredicts() {
        RecordingEvaluator evaluator = new RecordingEvaluator();

        BaseError error = assertThrows(BaseError.class,
                () -> evaluator.batchEvaluate(List.of(makeTestCase()), List.of(), 1));

        assertEquals(StatusCode.TOOLCHAIN_EVALUATOR_EXECUTION_ERROR, error.getStatus());
    }

    @Test
    void batchEvaluateRaisesForPredictsLongerThanCases() {
        RecordingEvaluator evaluator = new RecordingEvaluator();

        BaseError error = assertThrows(BaseError.class,
                () -> evaluator.batchEvaluate(List.of(), List.of(predict("pred")), 1));

        assertEquals(StatusCode.TOOLCHAIN_EVALUATOR_EXECUTION_ERROR, error.getStatus());
    }

    @Test
    void batchEvaluateRejectsZeroParallelism() {
        RecordingEvaluator evaluator = new RecordingEvaluator();

        BaseError error = assertThrows(BaseError.class,
                () -> evaluator.batchEvaluate(List.of(makeTestCase()), List.of(predict("pred")), 0));

        assertEquals(StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR, error.getStatus());
    }

    @Test
    void batchEvaluateRejectsTooHighParallelism() {
        RecordingEvaluator evaluator = new RecordingEvaluator();

        BaseError error = assertThrows(BaseError.class,
                () -> evaluator.batchEvaluate(List.of(makeTestCase()), List.of(predict("pred")), 100));

        assertEquals(StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR, error.getStatus());
    }

    @Test
    void evaluateReturnsParsingErrorWhenBothResponsesAreInvalid() {
        DefaultEvaluator evaluator = new DefaultEvaluator(modelWithResponses("invalid json", "invalid json"), "");

        EvaluatedCase result = evaluator.evaluate(makeTestCase(), predict("pred"));

        assertEquals(0.0d, result.getScore());
        assertTrue(result.getReason().contains("parsing error"));
    }

    @Test
    void evaluateReturnsPassWhenModelResultIsTrue() {
        DefaultEvaluator evaluator = new DefaultEvaluator(modelWithResponses("""
                ```json
                {"result": true, "reason": "good"}
                ```
                """), "");

        EvaluatedCase result = evaluator.evaluate(makeTestCase(), predict("pred"));

        assertEquals(1.0d, result.getScore());
        assertEquals("good", result.getReason());
    }

    @Test
    void evaluateReturnsFailWhenModelResultIsFalse() {
        DefaultEvaluator evaluator = new DefaultEvaluator(modelWithResponses("""
                ```json
                {"result": false, "reason": "bad"}
                ```
                """), "");

        EvaluatedCase result = evaluator.evaluate(makeTestCase(), predict("pred"));

        assertEquals(0.0d, result.getScore());
        assertEquals("bad", result.getReason());
    }

    @Test
    void evaluateRetriesOnInitialParseFailure() {
        DefaultEvaluator evaluator = new DefaultEvaluator(modelWithResponses(
                "invalid json",
                """
                        ```json
                        {"result": true, "reason": "retry success"}
                        ```
                        """
        ), "");

        EvaluatedCase result = evaluator.evaluate(makeTestCase(), predict("pred"));

        assertEquals(1.0d, result.getScore());
        assertEquals("retry success", result.getReason());
    }

    @Test
    void evaluateReturnsParsingErrorWhenRetryThrows() {
        DefaultEvaluator evaluator = new DefaultEvaluator(modelWithResponses(
                "invalid json",
                new IllegalStateException("retry failed")
        ), "");

        EvaluatedCase result = evaluator.evaluate(makeTestCase(), predict("pred"));

        assertEquals(0.0d, result.getScore());
        assertTrue(result.getReason().contains("parsing error"));
    }

    private static Case makeTestCase() {
        return new Case(new LinkedHashMap<>(Map.of("q", "test")),
                new LinkedHashMap<>(Map.of("ans", "expected")));
    }

    private static Map<String, Object> predict(String output) {
        return new LinkedHashMap<>(Map.of("output", output));
    }

    private static Model modelWithResponses(Object... responses) {
        AtomicInteger index = new AtomicInteger();
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            Object response = responses[Math.min(index.getAndIncrement(), responses.length - 1)];
            if (response instanceof RuntimeException exception) {
                CompletableFuture<AssistantMessage> failed = new CompletableFuture<>();
                failed.completeExceptionally(exception);
                return failed;
            }
            return CompletableFuture.completedFuture(new AssistantMessage(String.valueOf(response)));
        });
    }

    private static final class RecordingEvaluator extends BaseEvaluator {
        @Override
        public EvaluatedCase evaluate(Case caseValue, Map<String, Object> predict) {
            return new EvaluatedCase(caseValue, predict, 1.0d, "recorded", null);
        }
    }
}
