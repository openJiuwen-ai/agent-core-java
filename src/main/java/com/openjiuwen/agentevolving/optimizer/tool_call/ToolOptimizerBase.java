/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call;

import com.openjiuwen.agentevolving.optimizer.BaseOptimizer;

import java.util.Collections;
import java.util.List;

/**
 * Tool dimension optimizer base class.
 * <p>
 * Optimizes tunables exposed by ToolCallOperator (e.g., tool_description).
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.base.ToolOptimizerBase}.
 * 
 * @since 0.1.7
 */
public abstract class ToolOptimizerBase extends BaseOptimizer {
    /**
     * ToolOptimizerBase.
     * 
     * @since 0.1.7
     */
    protected ToolOptimizerBase() {
        this.domain = "tool";
    }

    /**
     * defaultTargets.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> defaultTargets() {
        return Collections.singletonList("tool_description");
    }
}
