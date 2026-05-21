// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.evaluator.metrics;

import com.openjiuwen.agent_evolving.evaluator.EvolverEvaluator;

/**
 * Base metric interface for evaluation.
 * <p>
 * Mirrors Python's {@code base.py} from
 * {@code openjiuwen.agent_evolving.evaluator.metrics.base}.
 */
public abstract class BaseMetric implements EvolverEvaluator.Metric {
    
    protected final String name;
    
    public BaseMetric(String name) {
        this.name = name;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public abstract double compute(Object trajectory);
}