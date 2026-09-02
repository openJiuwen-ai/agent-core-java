/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
 * LegacyBaseGroup.
 * 
 * @since 0.1.7
 */
@Deprecated
public abstract class LegacyBaseGroup {
    private final AgentGroupConfig config;
    private final String groupId;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, BaseAgent> agents = new LinkedHashMap<>();

    /**
     * Initialize the agent group.
     * 
     * @param config the configuration object for this group
     * @since 0.1.7
     */
    protected LegacyBaseGroup(AgentGroupConfig config) {
        this.config = config;
        this.groupId = config.getGroupId();
    }

    /**
     * Register agent to the group.
     * 
     * @param agentId Agent unique identifier
     * @param agent Agent instance
     * @since 0.1.7
     */
    public void addAgent(String agentId, BaseAgent agent) {
        if (agents.containsKey(agentId)) {
            throw ErrorHelper.buildError(StatusCode.AGENT_GROUP_ADD_RUNTIME_ERROR, "error_msg",
                    "Agent ID already exists");
        }

        if (getAgentCount() >= config.getMaxAgents()) {
            throw ErrorHelper.buildError(StatusCode.AGENT_GROUP_ADD_RUNTIME_ERROR, "error_msg",
                    "Agent count exceeds max agents");
        }

        agents.put(agentId, agent);

        // Auto-inject group reference to agent's controller (duck typing)
        try {
            var controller = agent.getClass().getMethod("getController").invoke(agent);
            if (controller != null) {
                var setGroupMethod = controller.getClass().getMethod("setGroup", LegacyBaseGroup.class);
                setGroupMethod.invoke(controller, this);
                Loggers.MULTI_AGENT.debug("BaseGroup: Auto-injected group reference to agent '{}' controller", agentId);
            }
        } catch (NoSuchMethodException e) {
            // Controller doesn't have setGroup — that's fine
        } catch (Exception e) {
            Loggers.MULTI_AGENT.debug("BaseGroup: Could not auto-inject group reference for agent '{}'", agentId);
        }
    }

    /**
     * Get the number of agents in the group.
     * 
     * @return agent count
     * @since 0.1.7
     */
    public int getAgentCount() {
        return agents.size();
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AgentGroupConfig getConfig() {
        return config;
    }

    /**
     * getGroupId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * getAgents.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, BaseAgent> getAgents() {
        return agents;
    }

    /**
     * Execute synchronous operation on the agent group.
     * 
     * @param message message object or map
     * @param session agent group session (nullable)
     * @return the collective output from the group
     * @since 0.1.7
     */
    public abstract Object invoke(Object message, AgentGroupSessionApi session);

    /**
     * Execute streaming operation on the agent group.
     * 
     * @param message message object or map
     * @param session agent group session (nullable)
     * @return iterator of streaming output
     * @since 0.1.7
     */
    public abstract Iterator<Object> stream(Object message, AgentGroupSessionApi session);
}
