/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.multiagent;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.schema.GroupCard;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.singleagent.BaseAgent;

import java.util.*;

/**
 * Abstract base class for agent groups (new Card + Config pattern).
 * <p>
 * Design principles (aligned with BaseAgent):
 * <ul>
 *   <li>Card is required (defines what the Group is)</li>
 *   <li>Config is optional (defines how the Group runs)</li>
 *   <li>All configuration methods support chaining</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code BaseGroup} in {@code multi_agent/group.py}.
 */
public abstract class BaseGroup {

    private final GroupCard card;
    private GroupConfig config;
    private final String groupId;
    private final Map<String, BaseAgent> agents = new LinkedHashMap<>();

    /**
     * Initialize the agent group.
     *
     * @param card   GroupCard defining group identity (required)
     * @param config GroupConfig for runtime settings (optional)
     */
    protected BaseGroup(GroupCard card, GroupConfig config) {
        this.card = card;
        this.config = config != null ? config : createDefaultConfig();
        this.groupId = card.getName();
    }

    protected BaseGroup(GroupCard card) {
        this(card, null);
    }

    private GroupConfig createDefaultConfig() {
        return new GroupConfig();
    }

    // ========== Configuration ==========

    /**
     * Set configuration (supports chaining).
     *
     * @param config GroupConfig instance
     * @return this group
     */
    public BaseGroup configure(GroupConfig config) {
        this.config = config;
        return this;
    }

    // ========== Agent Management ==========

    /**
     * Register agent to group (supports chaining).
     *
     * @param agent   Agent instance (must have card with name)
     * @param agentId optional custom ID (defaults to agent.card.name)
     * @return this group
     * @throws com.openjiuwen.core.common.exception.BaseError if agent ID already exists or max reached
     */
    public BaseGroup addAgent(BaseAgent agent, String agentId) {
        if (agentId == null) {
            if (agent.getCard() != null && agent.getCard().getName() != null) {
                agentId = agent.getCard().getName();
            } else {
                throw ErrorHelper.buildError(
                        StatusCode.AGENT_GROUP_ADD_RUNTIME_ERROR,
                        "error_msg", "Agent must have card.name or provide agentId"
                );
            }
        }

        if (agents.containsKey(agentId)) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_ADD_RUNTIME_ERROR,
                    "error_msg", "Agent ID '" + agentId + "' already exists"
            );
        }

        if (getAgentCount() >= config.getMaxAgents()) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_ADD_RUNTIME_ERROR,
                    "error_msg", "Agent count exceeds max_agents (" + config.getMaxAgents() + ")"
            );
        }

        agents.put(agentId, agent);

        if (agent.getCard() != null) {
            card.getAgentCards().add(agent.getCard());
        }

        // Auto-inject group reference to agent's controller if supported
        try {
            var controller = agent.getClass().getMethod("getController").invoke(agent);
            if (controller != null) {
                var setGroupMethod = controller.getClass().getMethod("setGroup", BaseGroup.class);
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

        return this;
    }

    /**
     * Register agent to group using agent's card name as ID.
     *
     * @param agent Agent instance
     * @return this group
     */
    public BaseGroup addAgent(BaseAgent agent) {
        return addAgent(agent, null);
    }

    /**
     * Remove agent from group (supports chaining).
     *
     * @param agentId Agent ID string
     * @return this group
     */
    public BaseGroup removeAgent(String agentId) {
        BaseAgent agent = agents.remove(agentId);
        if (agent != null && agent.getCard() != null) {
            card.getAgentCards().removeIf(c -> agentId.equals(c.getName()));
            Loggers.MULTI_AGENT.debug("BaseGroup: Removed agent '{}'", agentId);
        }
        return this;
    }

    /**
     * Remove agent from group by instance (supports chaining).
     *
     * @param agent Agent instance
     * @return this group
     */
    public BaseGroup removeAgent(BaseAgent agent) {
        if (agent.getCard() != null && agent.getCard().getName() != null) {
            return removeAgent(agent.getCard().getName());
        }
        Loggers.MULTI_AGENT.warn("Cannot determine agent ID from instance");
        return this;
    }

    /**
     * Get agent by ID.
     *
     * @param agentId Agent ID
     * @return Agent instance or null if not found
     */
    public BaseAgent getAgent(String agentId) {
        return agents.get(agentId);
    }

    /**
     * Get the number of agents in the group.
     *
     * @return agent count
     */
    public int getAgentCount() {
        return agents.size();
    }

    /**
     * List all agent IDs.
     *
     * @return list of agent IDs
     */
    public List<String> listAgents() {
        return new ArrayList<>(agents.keySet());
    }

    // ========== Accessors ==========

    public GroupCard getCard() {
        return card;
    }

    public GroupConfig getConfig() {
        return config;
    }

    public String getGroupId() {
        return groupId;
    }

    public Map<String, BaseAgent> getAgents() {
        return agents;
    }

    // ========== Abstract execution methods ==========

    /**
     * Execute synchronous operation on the agent group.
     *
     * @param message message object or map
     * @param session agent group session (nullable)
     * @return the collective output from the agent group
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
