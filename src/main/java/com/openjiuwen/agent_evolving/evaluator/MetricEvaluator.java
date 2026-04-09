/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.agent_evolving.evaluator;

import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.evaluator.metrics.Metric;
import com.openjiuwen.agent_evolving.TuneUtils;

import java.util.*;

/**
 * Evaluates using one or more Metrics with aggregation.
 *
 * <p>Supports per-metric breakdown and configurable aggregation (mean, first).
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.evaluator.MetricEvaluator}.
 */
public class MetricEvaluator extends BaseEvaluator {

    private final List<Metric> metrics;
    private final String aggregate;

    /**
     * Create with single metric.
     *
     * @param metric Metric instance
     */
    public MetricEvaluator(Metric metric) {
        this(Collections.singletonList(metric), "mean");
    }

    /**
     * Create with multiple metrics.
     *
     * @param metrics   List of metrics
     * @param aggregate Aggregation method ("mean" or "first")
     */
    public MetricEvaluator(List<Metric> metrics, String aggregate) {
        this.metrics = metrics != null ? metrics : new ArrayList<>();
        this.aggregate = aggregate != null ? aggregate : "mean";
    }

    @Override
    public EvaluatedCase evaluate(Case caseData, Map<String, Object> predict) {
        EvaluatedCase evaluated = EvaluatedCase.builder()
                .caseData(caseData)
                .answer(predict)
                .build();

        Map<String, Double> perMetric = new LinkedHashMap<>();
        List<Double> scores = new ArrayList<>();

        for (Metric metric : metrics) {
            Map<String, Object> kwargs = new HashMap<>();
            kwargs.put("question", caseData.getInputs());
            kwargs.put("case", caseData);

            Object out = metric.compute(predict, caseData.getLabel(), kwargs);

            if (out instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> outMap = (Map<String, Object>) out;
                for (Map.Entry<String, Object> entry : outMap.entrySet()) {
                    double vf = safeConvert(entry.getValue());
                    perMetric.put(entry.getKey(), vf);
                    scores.add(vf);
                }
            } else {
                double score = safeConvert(out);
                perMetric.put(metric.getName(), score);
                scores.add(score);
            }
        }

        evaluated.setScore(aggScore(scores, aggregate));
        evaluated.setPerMetric(perMetric.isEmpty() ? null : perMetric);
        return evaluated;
    }

    private double safeConvert(Object num) {
        if (num instanceof Number) {
            return ((Number) num).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(num));
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(MetricEvaluator.class.getName())
                    .warning("Could not convert metric value " + num + " to double");
            return 0.0;
        }
    }

    private double aggScore(List<Double> results, String aggregate) {
        if (results == null || results.isEmpty()) {
            return 0.0;
        }
        if ("first".equals(aggregate)) {
            return results.get(0);
        }
        // Default: mean
        return results.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
