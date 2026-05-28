// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl;

/**
 * RL Rail for trajectory collection during training.
 * <p>
 * Mirrors Python's {@code rl_rail.py} from
 * {@code openjiuwen.agent_evolving.agent_rl.rl_rail}.
 */
public class RlRail {
    
    /**
     * Rail priority.
     */
    public static final int PRIORITY = 50;
    
    /**
     * Initialize RL Rail.
     * PLACEHOLDER: Requires EvolutionRail base class.
     */
    public RlRail() {
        // PLACEHOLDER
    }
    
    /**
     * Process trajectory step.
     * PLACEHOLDER: Requires TrajectoryStep Java class.
     */
    public void processStep(Object step) {
        throw new UnsupportedOperationException(
            "processStep requires TrajectoryStep Java class. " +
            "Placeholder until rl_rail logic is translated."
        );
    }
}