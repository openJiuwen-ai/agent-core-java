/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.memory_call;

import com.openjiuwen.agent_evolving.optimizer.BaseOptimizer;
import com.openjiuwen.core.operator.Operator;

import java.util.List;
import java.util.Map;

/**
 * Memory dimension optimizer base class.
 *
 * <p>Optimizes tunables exposed by MemoryCallOperator.</p>
 *
 * <p>Mirrors Python's {@code MemoryOptimizerBase} in
 * {@code openjiuwen/agent_evolving/optimizer/memory_call/base.py}.</p>
 */
public abstract class MemoryOptimizerBase extends BaseOptimizer {

    protected MemoryOptimizerBase() {
        this.domain = "memory";
    }

    /**
     * Default targets for memory optimizers.
     *
     * @return default memory tunable names
     */
    @Override
    public List<String> defaultTargets() {
        return List.of("enabled", "max_retries");
    }

    /**
     * Filter operators exposing memory tunables while preserving base logging semantics.
     *
     * @param operators candidate operators
     * @param targets tunable names to match
     * @return filtered operators
     */
    public static Map<String, Operator> filterOperators(Map<String, Operator> operators, List<String> targets) {
        return BaseOptimizer.filterOperators(operators, targets);
    }
}
