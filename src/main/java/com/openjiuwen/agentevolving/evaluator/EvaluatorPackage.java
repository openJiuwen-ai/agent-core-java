/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator;

import com.openjiuwen.agentevolving.evaluator.metrics.ExactMatchMetric;
import com.openjiuwen.agentevolving.evaluator.metrics.LLMAsJudgeMetric;
import com.openjiuwen.agentevolving.evaluator.metrics.Metric;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluator package facade preserving Python re-export names.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.evaluator} module in
 * {@code openjiuwen/agent_evolving/evaluator/__init__.py}.</p>
 */
public final class EvaluatorPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/evaluator/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "BaseEvaluator",
            "DefaultEvaluator",
            "MetricEvaluator",
            "Metric",
            "ExactMatchMetric",
            "LLMAsJudgeMetric"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private EvaluatorPackage() {
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
        exports.put("BaseEvaluator", BaseEvaluator.class);
        exports.put("DefaultEvaluator", DefaultEvaluator.class);
        exports.put("MetricEvaluator", MetricEvaluator.class);
        exports.put("Metric", Metric.class);
        exports.put("ExactMatchMetric", ExactMatchMetric.class);
        exports.put("LLMAsJudgeMetric", LLMAsJudgeMetric.class);
        return Collections.unmodifiableMap(exports);
    }
}
