/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator;

import com.openjiuwen.agentevolving.dataset.Case;
import com.openjiuwen.agentevolving.dataset.EvaluatedCase;
import com.openjiuwen.agentevolving.evaluator.metrics.Metric;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluator that computes one or more metric scores and aggregates them.
 *
 * <p>Mirrors Python's {@code MetricEvaluator} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator.py}.</p>
 */
public class MetricEvaluator extends BaseEvaluator {

    private final List<Metric> metrics;
    private final String aggregate;

    public MetricEvaluator(Metric metric) {
        this(metric, "mean");
    }

    public MetricEvaluator(Metric metric, String aggregate) {
        this(List.of(metric), aggregate);
    }

    public MetricEvaluator(List<? extends Metric> metrics) {
        this(metrics, "mean");
    }

    public MetricEvaluator(List<? extends Metric> metrics, String aggregate) {
        this.metrics = new ArrayList<>(metrics);
        this.aggregate = aggregate;
    }

    @Override
    public EvaluatedCase evaluate(Case caseValue, Map<String, Object> predict) {
        EvaluatedCase evaluated = new EvaluatedCase(caseValue, predict, 0.0d, "", null);
        Map<String, Double> perMetric = new LinkedHashMap<>();
        List<Double> scores = new ArrayList<>();
        for (Metric metric : metrics) {
            Object out = metric.compute(predict, caseValue.getLabel(), metricKwargs(caseValue));
            if (out instanceof Map<?, ?> metricMap) {
                for (Map.Entry<?, ?> entry : metricMap.entrySet()) {
                    double value = safeConvert(entry.getValue());
                    perMetric.put(String.valueOf(entry.getKey()), value);
                    scores.add(value);
                }
            } else {
                double score = safeConvert(out);
                perMetric.put(metric.getName(), score);
                scores.add(score);
            }
        }
        evaluated.setScore(EvaluatorScoreAggregator.aggregateScore(scores, aggregate));
        evaluated.setPerMetric(perMetric.isEmpty() ? null : perMetric);
        return evaluated;
    }

    double safeConvert(Object numberValue) {
        if (numberValue instanceof Number number) {
            return number.doubleValue();
        }
        if (numberValue instanceof Boolean bool) {
            return bool ? 1.0d : 0.0d;
        }
        try {
            return Double.parseDouble(String.valueOf(numberValue));
        } catch (NumberFormatException exception) {
            Loggers.COMMON.warn("Could not convert metric value {} to float", 0.0d);
            return 0.0d;
        }
    }

    private static Map<String, Object> metricKwargs(Case caseValue) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("question", caseValue.getInputs());
        kwargs.put("case", caseValue);
        return kwargs;
    }
}
