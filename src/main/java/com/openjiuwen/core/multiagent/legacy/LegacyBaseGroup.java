/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.multiagent.legacy;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.singleagent.BaseAgent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Abstract base class for implementing agent groups (legacy pattern).
 * <p>
 * Provides the foundational structure and common functionality for managing
 * groups of agents in a multi-agent system.
 * <p>
 * Mirrors Python's legacy {@code BaseGroup} in {@code multi_agent/legacy/agent_group.py}.
 *
 * @deprecated Use {@link com.openjiuwen.core.multiagent.BaseGroup} with the new Card + Config pattern.
 */
@Deprecated
public abstract class LegacyBaseGroup {

    private final AgentGroupConfig config;
    private final String groupId;
    private final Map<String, BaseAgent> agents = new LinkedHashMap<>();

    /**
     * Initialize the agent group.
     *
     * @param config the configuration object for this group
     */
    protected LegacyBaseGroup(AgentGroupConfig config) {
        this.config = config;
        this.groupId = config.getGroupId();
    }

    /**
     * Register agent to the group.
     *
     * @param agentId Agent unique identifier
     * @param agent   Agent instance
     * @throws com.openjiuwen.core.common.exception.BaseError if agent ID already exists or max reached
     */
    public void addAgent(String agentId, BaseAgent agent) {
        if (agents.containsKey(agentId)) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_ADD_RUNTIME_ERROR,
                    "error_msg", "Agent ID already exists"
            );
        }

        if (getAgentCount() >= config.getMaxAgents()) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_ADD_RUNTIME_ERROR,
                    "error_msg", "Agent count exceeds max agents"
            );
        }

        agents.put(agentId, agent);

        // Auto-inject group reference to agent's controller (duck typing)
        try {
            var controller = agent.getClass().getMethod("getController").invoke(agent);
            if (controller != null) {
                var setGroupMethod = controller.getClass().getMethod("setGroup", LegacyBaseGroup.class);
                setGroupMethod.invoke(controller, this);
                Loggers.MULTI_AGENT.debug(
                        "BaseGroup: Auto-injected group reference to agent '{}' controller", agentId
                );
            }
        } catch (NoSuchMethodException e) {
            // Controller doesn't have setGroup — that's fine
        } catch (Exception e) {
            Loggers.MULTI_AGENT.debug(
                    "BaseGroup: Could not auto-inject group reference for agent '{}'", agentId
            );
        }
    }

    /**
     * Get the number of agents in the group.
     *
     * @return agent count
     */
    public int getAgentCount() {
        return agents.size();
    }

    public AgentGroupConfig getConfig() {
        return config;
    }

    public String getGroupId() {
        return groupId;
    }

    public Map<String, BaseAgent> getAgents() {
        return agents;
    }

    /**
     * Execute synchronous operation on the agent group.
     *
     * @param message message object or map
     * @param session agent group session (nullable)
     * @return the collective output from the group
     */
    public abstract Object invoke(Object message, AgentGroupSessionApi session);

    /**
     * Execute streaming operation on the agent group.
     *
     * @param message message object or map
     * @param session agent group session (nullable)
     * @return iterator of streaming output
     */
    public abstract Iterator<Object> stream(Object message, AgentGroupSessionApi session);
}
