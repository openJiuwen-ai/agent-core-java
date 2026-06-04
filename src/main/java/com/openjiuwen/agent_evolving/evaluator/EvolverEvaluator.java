// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.evaluator;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluator for evolution quality assessment.
 * <p>
 * Mirrors Python's {@code evaluator.py} from
 * {@code openjiuwen.agent_evolving.evaluator.evaluator}.
 */
public class EvolverEvaluator {
    
    private final List<Metric> metrics;
    
    public EvolverEvaluator(List<Metric> metrics) {
        this.metrics = metrics != null ? new ArrayList<>(metrics) : new ArrayList<>();
    }
    
    /**
     * Evaluate trajectory.
     *
     * <p>Mirrors the mean aggregation used by Python's {@code _agg_score} for
     * {@code MetricEvaluator}: no metric outputs produce {@code 0.0}; otherwise
     * scores are averaged in input order.
     */
    public double evaluate(Object trajectory) {
        if (metrics.isEmpty()) {
            return 0.0;
        }
        double totalScore = 0.0;
        
        for (Metric metric : metrics) {
            totalScore += metric.compute(trajectory);
        }
        
        return totalScore / metrics.size();
    }
    
    /**
     * Metric interface.
     */
    public interface Metric {
        double compute(Object trajectory);
        String getName();
    }
}
