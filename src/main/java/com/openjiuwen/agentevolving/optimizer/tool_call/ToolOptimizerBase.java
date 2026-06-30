/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call;

import com.openjiuwen.agentevolving.optimizer.BaseOptimizer;

import java.util.Collections;
import java.util.List;

/**
 * Tool dimension optimizer base class.
 *
 * <p>Optimizes tunables exposed by ToolCallOperator (e.g., tool_description).
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.base.ToolOptimizerBase}.
 */
public abstract class ToolOptimizerBase extends BaseOptimizer {

    /**
     * Auto-generated for codecheck compliance.
     */
    protected ToolOptimizerBase() {
        this.domain = "tool";
    }

    /**
     * Default targets for tool optimizers.
     *
     * @return List of default targets
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> defaultTargets() {
        return Collections.singletonList("tool_description");
    }
}
