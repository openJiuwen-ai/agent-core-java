/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.memory_call;

import com.openjiuwen.agentevolving.optimizer.BaseOptimizer;

import java.util.Arrays;
import java.util.List;

/**
 * Memory dimension optimizer base class.
 * <p>
 * Optimizes tunables exposed by MemoryCallOperator.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.memory_call.base.MemoryOptimizerBase}.
 * 
 * @since 0.1.7
 */
public abstract class MemoryOptimizerBase extends BaseOptimizer {
    /**
     * MemoryOptimizerBase.
     * 
     * @since 0.1.7
     */
    protected MemoryOptimizerBase() {
        this.domain = "memory";
    }

    /**
     * defaultTargets.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> defaultTargets() {
        return Arrays.asList("enabled", "max_retries");
    }
}
