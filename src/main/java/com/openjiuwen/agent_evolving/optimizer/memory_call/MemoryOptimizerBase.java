/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.memory_call;

import com.openjiuwen.agentevolving.optimizer.BaseOptimizer;

import java.util.Arrays;
import java.util.List;

/**
 * Memory dimension optimizer base class.
 *
 * <p>Optimizes tunables exposed by MemoryCallOperator.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.memory_call.base.MemoryOptimizerBase}.
 */
public abstract class MemoryOptimizerBase extends BaseOptimizer {

    /**
     * Auto-generated for codecheck compliance.
     */
    protected MemoryOptimizerBase() {
        this.domain = "memory";
    }

    /**
     * Default targets for memory optimizers.
     *
     * @return List of default targets
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> defaultTargets() {
        return Arrays.asList("enabled", "max_retries");
    }
}
