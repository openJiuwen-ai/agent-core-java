/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.metrics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.metrics} package facade in
 * {@code openjiuwen/agent_evolving/evaluator/metrics/__init__.py}.
 */
class MetricsPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals("openjiuwen/agent_evolving/evaluator/metrics/__init__.py", MetricsPackage.PYTHON_MODULE);
        assertEquals(List.of("Metric", "ExactMatchMetric", "LLMAsJudgeMetric"), MetricsPackage.all());
        assertSame(MetricsPackage.EXPORTED_SYMBOLS, MetricsPackage.all());
        assertTrue(MetricsPackage.DESCRIPTION.contains("Evaluation metrics"));
    }

    @Test
    void resolvesExportedMetricTypes() {
        assertSame(Metric.class, MetricsPackage.typeFor("Metric"));
        assertSame(ExactMatchMetric.class, MetricsPackage.typeFor("ExactMatchMetric"));
        assertSame(LLMAsJudgeMetric.class, MetricsPackage.typeFor("LLMAsJudgeMetric"));
        assertTrue(MetricsPackage.exports("Metric"));
        assertFalse(MetricsPackage.exports("missing"));
    }
}
