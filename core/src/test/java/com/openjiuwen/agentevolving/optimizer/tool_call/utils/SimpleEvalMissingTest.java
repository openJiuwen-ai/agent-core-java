/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code test_customized_eval} in
 * {@code tests/unit_tests/agent_evolving/optimizer/tool_call/test_customized_eval.py}.
 */
class SimpleEvalMissingTest {

    @Test
    void simpleEvalInitWeightValidation() {
        assertThatThrownBy(() -> new SimpleEval(null, baseConfig(), 0.7d, 0.4d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simpleEvalCallAggregate() {
        SimpleEval evaluator = new StubSimpleEval(baseConfig(), 0.4d, 0.6d);

        Map<String, Object> result = evaluator.call(
                Map.of("name", "f"),
                "d",
                List.<Object[]>of(new Object[] {"i", Map.of(), "", "a"}),
                2
        );

        assertThat((Double) result.get("score_avg")).isCloseTo(65.0d, org.assertj.core.data.Offset.offset(1e-9));
        assertThat((Double) result.get("fn_call_accuracy")).isCloseTo(50.0d, org.assertj.core.data.Offset.offset(1e-9));
        assertThat((Double) result.get("output_effectiveness")).isCloseTo(75.0d, org.assertj.core.data.Offset.offset(1e-9));
    }

    private static Map<String, Object> baseConfig() {
        return Map.of("eval_model_id", "gpt-test");
    }

    private static final class StubSimpleEval extends SimpleEval {

        private StubSimpleEval(Map<String, Object> config, double fnCallWeight, double outputEffectivenessWeight) {
            super(null, config, fnCallWeight, outputEffectivenessWeight);
        }

        @Override
        protected Map<String, Object> evaluateSingleExample(
                Map<String, Object> tool,
                String description,
                Object[] example,
                int exampleId
        ) {
            return Map.of(
                    "fn_call_score", 0.5d,
                    "output_effectiveness_score", 0.75d,
                    "weighted_score", 0.65d,
                    "answer", "ok",
                    "errors", List.of()
            );
        }
    }
}
