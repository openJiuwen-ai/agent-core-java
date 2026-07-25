/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator;

import com.openjiuwen.agent_evolving.evaluator.metrics.ExactMatchMetric;
import com.openjiuwen.agent_evolving.evaluator.metrics.LLMAsJudgeMetric;
import com.openjiuwen.agent_evolving.evaluator.metrics.Metric;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_evolving.evaluator} module in
 * {@code openjiuwen/agent_evolving/evaluator/__init__.py}.
 */
class EvaluatorPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals("openjiuwen/agent_evolving/evaluator/__init__.py", EvaluatorPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "BaseEvaluator",
                "DefaultEvaluator",
                "MetricEvaluator",
                "Metric",
                "ExactMatchMetric",
                "LLMAsJudgeMetric"
        ), EvaluatorPackage.all());
        assertSame(EvaluatorPackage.EXPORTED_SYMBOLS, EvaluatorPackage.all());
    }

    @Test
    void resolvesExportedEvaluatorAndMetricTypes() {
        assertSame(BaseEvaluator.class, EvaluatorPackage.typeFor("BaseEvaluator"));
        assertSame(DefaultEvaluator.class, EvaluatorPackage.typeFor("DefaultEvaluator"));
        assertSame(MetricEvaluator.class, EvaluatorPackage.typeFor("MetricEvaluator"));
        assertSame(Metric.class, EvaluatorPackage.typeFor("Metric"));
        assertSame(ExactMatchMetric.class, EvaluatorPackage.typeFor("ExactMatchMetric"));
        assertSame(LLMAsJudgeMetric.class, EvaluatorPackage.typeFor("LLMAsJudgeMetric"));
        assertTrue(EvaluatorPackage.exports("DefaultEvaluator"));
        assertFalse(EvaluatorPackage.exports("missing"));
    }
}
