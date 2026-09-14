/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

/**
 * 0.1.12-compatible agent-group manager alias.
 *
 * <p>Mirrors Python's {@code AgentGroupMgr} in
 * {@code openjiuwen/core/runner/resources_manager/agent_group_manager.py}.</p>
 *
 * @param <T> agent group type retained for source compatibility
 */
public class AgentGroupMgr<T> extends AgentTeamManager {

    public String kind() {
        return "agent_team";
    }

    public void addAgentGroup(String agentGroupId, java.util.function.Supplier<?> agentGroup) {
        addAgentTeam(agentGroupId, agentGroup);
    }

    public java.util.function.Supplier<?> removeAgentGroup(String agentGroupId) {
        return removeAgentTeam(agentGroupId);
    }

    public java.util.concurrent.CompletionStage<Object> getAgentGroup(String agentGroupId) {
        return getAgentTeam(agentGroupId);
    }
}
