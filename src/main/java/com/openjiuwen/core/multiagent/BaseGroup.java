// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.multiagent;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.schema.GroupCard;
import com.openjiuwen.core.session.AgentGroupSessionWrapper;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Abstract base class for agent groups.
 * 
 * <p>Design principles (aligned with BaseAgent):
 * <ul>
 *   <li>Card is required (defines what the Group is)</li>
 *   <li>Config is optional (defines how the Group runs)</li>
 *   <li>All configuration methods support chaining</li>
 * </ul>
 * 
 * <p>Python reference: {@code agent-core/openjiuwen/core/multi_agent/group.py}
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public abstract class BaseGroup {
    
    private static final LoggerProtocol logger = Loggers.AGENT;
    
    /**
     * Group card (required, immutable identity).
     */
    protected final GroupCard card;
    
    /**
     * Group config (optional, mutable runtime settings).
     */
    protected GroupConfig config;
    
    /**
     * Group ID (derived from card.name).
     */
    protected final String groupId;
    
    /**
     * Dictionary of agents {agent_name: agent_instance}.
     */
    protected final Map<String, BaseAgent> agents;
    
    /**
     * Initialize the agent group.
     *
     * @param card GroupCard defining group identity
     */
    protected BaseGroup(GroupCard card) {
        this(card, null);
    }
    
    /**
     * Initialize the agent group.
     *
     * @param card GroupCard defining group identity
     * @param config Optional GroupConfig for runtime settings
     */
    protected BaseGroup(GroupCard card, GroupConfig config) {
        this.card = card;
        this.config = config != null ? config : createDefaultConfig();
        this.groupId = card.getName();
        this.agents = new LinkedHashMap<>(); // Preserve insertion order
    }
    
    /**
     * Create default configuration.
     *
     * @return default GroupConfig
     */
    protected GroupConfig createDefaultConfig() {
        return new GroupConfig();
    }
    
    // ========== Configuration Interface ==========
    
    /**
     * Set configuration.
     *
     * @param config GroupConfig configuration object
     * @return self (supports chaining)
     */
    public BaseGroup configure(GroupConfig config) {
        this.config = config;
        return this;
    }
    
    // ========== Agent Management Interface ==========
    
    /**
     * Register agent to group.
     *
     * @param agent Agent instance (must have card.name)
     * @return self (supports chaining)
     * @throws JiuWenBaseException If agent ID already exists or max reached
     */
    public BaseGroup addAgent(BaseAgent agent) {
        return addAgent(agent, null);
    }
    
    /**
     * Register agent to group.
     *
     * @param agent Agent instance (must have card.name)
     * @param agentId Optional custom ID (defaults to agent.card.name)
     * @return self (supports chaining)
     * @throws JiuWenBaseException If agent ID already exists or max reached
     */
    public BaseGroup addAgent(BaseAgent agent, String agentId) {
        // Determine agent ID
        if (agentId == null) {
            if (agent.getCard() != null && agent.getCard().getName() != null) {
                agentId = agent.getCard().getName();
            } else {
                throw new JiuWenBaseException(
                    StatusCode.AGENT_GROUP_ADD_FAILED.getCode(),
                    StatusCode.AGENT_GROUP_ADD_FAILED.getMessage()
                        .replace("{reason}", "Agent must have card.name or provide agent_id")
                );
            }
        }
        
        // Check if agent ID already exists
        if (agents.containsKey(agentId)) {
            throw new JiuWenBaseException(
                StatusCode.AGENT_GROUP_ADD_FAILED.getCode(),
                StatusCode.AGENT_GROUP_ADD_FAILED.getMessage()
                    .replace("{reason}", "Agent ID '" + agentId + "' already exists")
            );
        }
        
        // Check max agents limit
        if (getAgentCount() >= config.getMaxAgents()) {
            throw new JiuWenBaseException(
                StatusCode.AGENT_GROUP_ADD_FAILED.getCode(),
                StatusCode.AGENT_GROUP_ADD_FAILED.getMessage()
                    .replace("{reason}", "Agent count exceeds max_agents (" + config.getMaxAgents() + ")")
            );
        }
        
        // Add agent
        agents.put(agentId, agent);
        
        // Sync agent card to group card
        if (agent.getCard() != null) {
            card.getAgentCards().add(agent.getCard());
        }
        
        // Auto-inject group reference to agent's controller if supported
        try {
            Object controller = getControllerFromAgent(agent);
            if (controller != null) {
                java.lang.reflect.Method setGroupMethod = controller.getClass().getMethod("setGroup", BaseGroup.class);
                setGroupMethod.invoke(controller, this);
                logger.debug("BaseGroup: Auto-injected group reference to agent '" + agentId + "' controller");
            }
        } catch (NoSuchMethodException e) {
            // Controller doesn't have setGroup method, ignore
        } catch (Exception e) {
            logger.debug("BaseGroup: Failed to inject group to controller: " + e.getMessage());
        }
        
        return this;
    }
    
    /**
     * Try to get controller from agent via reflection.
     *
     * @param agent the agent
     * @return controller object or null
     */
    private Object getControllerFromAgent(BaseAgent agent) {
        try {
            java.lang.reflect.Field controllerField = agent.getClass().getDeclaredField("controller");
            controllerField.setAccessible(true);
            return controllerField.get(agent);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Remove agent from group.
     *
     * @param agentId Agent ID string or agent instance
     * @return self (supports chaining)
     */
    public BaseGroup removeAgent(Object agentId) {
        String id = null;
        
        if (agentId instanceof String) {
            id = (String) agentId;
        } else if (agentId instanceof BaseAgent) {
            BaseAgent agent = (BaseAgent) agentId;
            if (agent.getCard() != null && agent.getCard().getName() != null) {
                id = agent.getCard().getName();
            } else {
                logger.warning("Cannot determine agent ID from instance");
                return this;
            }
        }
        
        if (id != null && agents.containsKey(id)) {
            BaseAgent agent = agents.remove(id);
            // Remove agent card from group card
            if (agent != null && agent.getCard() != null) {
                final String finalId = id;
                card.setAgentCards(
                    card.getAgentCards().stream()
                        .filter(c -> !c.getName().equals(finalId))
                        .toList()
                );
            }
            logger.debug("BaseGroup: Removed agent '" + id + "'");
        }
        
        return this;
    }
    
    // ========== Query Interface ==========
    
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
     * @return Number of agents
     */
    public int getAgentCount() {
        return agents.size();
    }
    
    /**
     * List all agent IDs.
     *
     * @return List of agent IDs
     */
    public List<String> listAgents() {
        return new ArrayList<>(agents.keySet());
    }
    
    // ========== Getters ==========
    
    /**
     * Gets the group card.
     *
     * @return the group card
     */
    public GroupCard getCard() {
        return card;
    }
    
    /**
     * Gets the group config.
     *
     * @return the group config
     */
    public GroupConfig getConfig() {
        return config;
    }
    
    /**
     * Gets the group ID.
     *
     * @return the group ID
     */
    public String getGroupId() {
        return groupId;
    }
    
    /**
     * Gets the agents map.
     *
     * @return the agents map
     */
    public Map<String, BaseAgent> getAgents() {
        return agents;
    }
    
    // ========== Abstract Methods ==========
    
    /**
     * Execute synchronous operation on the agent group.
     *
     * @param message Message object or dict
     * @param session Session for agent group instance (can be null)
     * @return The collective output from the agent group
     */
    public abstract CompletableFuture<Object> invoke(Object message, AgentGroupSessionWrapper session);
    
    /**
     * Execute streaming operation on the agent group.
     *
     * @param message Message object or dict
     * @param session Session for agent group instance (can be null)
     * @return Future containing stream of output from the agent group
     */
    public abstract CompletableFuture<Stream<Object>> stream(Object message, AgentGroupSessionWrapper session);
}

