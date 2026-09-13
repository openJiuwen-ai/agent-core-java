/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.rail.AgentRail;

/**
 * DeepAgentRail.
 * 
 * @since 0.1.7
 */
public abstract class DeepAgentRail extends AgentRail {
    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int priority() {
        return 50;
    }

    /**
     * getPriority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int getPriority() {
        return priority();
    }

    /**
     * init.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    public void init(Object agent) {
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    public void uninit(Object agent) {
    }
}
