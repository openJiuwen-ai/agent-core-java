// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.evaluator;

import java.util.*;

/**
 * Evaluator for evolution quality assessment.
 * <p>
 * Mirrors Python's {@code evaluator.py} from
 * {@code openjiuwen.agent_evolving.evaluator.evaluator}.
 */
public class EvolverEvaluator {
    
    private final List<Metric> metrics;
    
    public EvolverEvaluator(List<Metric> metrics) {
        this.metrics = metrics != null ? metrics : new ArrayList<>();
    }
    
    /**
     * Evaluate trajectory.
     * PLACEHOLDER: Requires Trajectory Java class.
     */
    public double evaluate(Object trajectory) {
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