/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Rollout collector for gathering agent execution results.
 * <p>
 * Mirrors Python's {@code RolloutCollector} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.runtime.collector}.
 */
public class RolloutCollector {

    private Object agent;
    private List<Object> collectedRollouts = new ArrayList<>();

    public RolloutCollector(Object agent) {
        this.agent = agent;
    }

    /**
     * Collect a rollout from the agent.
     * 
     * @param prompt Prompt for rollout
     * @return Collected rollout
     */
    public Object collect(Object prompt) {
        // TODO: Execute agent and collect rollout
        Object rollout = null;
        collectedRollouts.add(rollout);
        return rollout;
    }

    /**
     * Get all collected rollouts.
     * 
     * @return List of rollouts
     */
    public List<Object> getCollectedRollouts() {
        return new ArrayList<>(collectedRollouts);
    }

    /**
     * Clear collected rollouts.
     */
    public void clear() {
        collectedRollouts.clear();
    }

    public Object getAgent() { return agent; }
    public void setAgent(Object agent) { this.agent = agent; }
}