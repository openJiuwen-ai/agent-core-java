/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.legacy;

/**
 * Legacy controller-group configuration.
 *
 * <p>Mirrors Python's legacy multi-agent group configuration in
 * {@code openjiuwen/core/multi_agent/legacy/group.py}.</p>
 */
public class AgentGroupConfig {
    private final String groupId;
    private int maxAgents = 10;

    public AgentGroupConfig(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupId() {
        return groupId;
    }

    public int getMaxAgents() {
        return maxAgents;
    }

    public void setMaxAgents(int maxAgents) {
        this.maxAgents = maxAgents;
    }
}
