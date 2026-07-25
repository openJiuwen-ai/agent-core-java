/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.metrics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluation metrics package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.metrics} in
 * {@code openjiuwen/agent_evolving/evaluator/metrics/__init__.py}.</p>
 */
public final class MetricsPackage {

    public static final String DESCRIPTION = "Evaluation metrics: Metric, ExactMatchMetric, LLMAsJudgeMetric.";

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/evaluator/metrics/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "Metric",
            "ExactMatchMetric",
            "LLMAsJudgeMetric"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private MetricsPackage() {
    }

    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    public static Class<?> typeFor(String exportedName) {
        return EXPORTED_TYPES.get(exportedName);
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("Metric", Metric.class);
        exports.put("ExactMatchMetric", ExactMatchMetric.class);
        exports.put("LLMAsJudgeMetric", LLMAsJudgeMetric.class);
        return Collections.unmodifiableMap(exports);
    }
}
