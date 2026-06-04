/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent;

import com.openjiuwen.core.multiagent.config.TeamConfig;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teamruntime.RuntimeConfig;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Abstract base class for agent teams.
 * <p>
 * Mirrors Python's {@code BaseTeam} in 
 * {@code openjiuwen.core.multi_agent.team}.
 * <p>
 * Team composition pattern: Card + Config + Runtime.
 * All agent registration is delegated to runtime.
 * <p>
 * Attributes:
 * <ul>
 *     <li>card: Team card (required, immutable identity)</li>
 *     <li>config: Team config (optional, mutable runtime settings)</li>
 *     <li>runtime: TeamRuntime instance</li>
 * </ul>
 */
public abstract class BaseTeam {
    
    protected final TeamCard card;
    protected TeamConfig config;
    protected final String teamId;
    protected final TeamRuntime runtime;
    
    public BaseTeam(TeamCard card) {
        this(card, null, null);
    }
    
    public BaseTeam(TeamCard card, TeamConfig config) {
        this(card, config, null);
    }
    
    public BaseTeam(TeamCard card, TeamConfig config, TeamRuntime runtime) {
        this.card = card;
        this.config = config != null ? config : createDefaultConfig();
        this.teamId = card.getName();
        this.runtime = runtime != null ? runtime : createDefaultRuntime();
    }
    
    /**
     * Create default configuration.
     */
    protected TeamConfig createDefaultConfig() {
        return new TeamConfig();
    }
    
    /**
     * Create default runtime with team_id.
     */
    protected TeamRuntime createDefaultRuntime() {
        return new TeamRuntime(RuntimeConfig.builder()
            .teamId(card.getId())
            .build());
    }

    /**
     * Replace team configuration and return this for fluent chaining.
     *
     * @param config new configuration
     * @return this team
     */
    public BaseTeam configure(TeamConfig config) {
        this.config = config;
        return this;
    }
    
    /**
     * Add an agent to the team.
     * 
     * @param card AgentCard
     * @param provider Supplier that creates agent instance
     * @return this (supports chaining)
     */
    public BaseTeam addAgent(AgentCard card, Supplier<?> provider) {
        String agentId = card.getId();
        if (runtime.hasAgent(agentId)) {
            return this;
        }
        if (runtime.getAgentCount() >= config.getMaxAgents()) {
            throw new IllegalStateException("Agent count exceeds max_agents (" + config.getMaxAgents() + ")");
        }
        runtime.registerAgent(card, provider);
        this.card.getAgentCards().add(card);
        return this;
    }
    
    /**
     * Remove an agent from the team.
     * 
     * @param agentId Agent ID
     * @return this (supports chaining)
     */
    public BaseTeam removeAgent(String agentId) {
        AgentCard removedCard = runtime.unregisterAgent(agentId);
        if (removedCard != null) {
            this.card.setAgentCards(new ArrayList<>(this.card.getAgentCards().stream()
                    .filter(c -> !removedCard.getId().equals(c.getId()))
                    .toList()));
        }
        return this;
    }

    /**
     * Remove an agent by card.
     *
     * @param agent card to remove
     * @return this (supports chaining)
     */
    public BaseTeam removeAgent(AgentCard agent) {
        if (agent == null) {
            return this;
        }
        return removeAgent(agent.getId());
    }

    /**
     * Remove an agent by ID string or AgentCard.
     *
     * @param agent agent ID or card
     * @return this (supports chaining)
     */
    public BaseTeam removeAgent(Object agent) {
        if (agent instanceof AgentCard agentCard) {
            return removeAgent(agentCard);
        }
        if (agent instanceof String agentId) {
            return removeAgent(agentId);
        }
        return this;
    }
    
    /**
     * Check if agent is registered.
     * 
     * @param agentId Agent ID
     * @return true if registered
     */
    public boolean hasAgent(String agentId) {
        return runtime.hasAgent(agentId);
    }

    /**
     * Get agent card by ID.
     *
     * @param agentId agent ID
     * @return card or null
     */
    public AgentCard getAgentCard(String agentId) {
        return runtime.getAgentCard(agentId);
    }

    /**
     * Get registered agent count.
     *
     * @return number of registered agents
     */
    public int getAgentCount() {
        return runtime.getAgentCount();
    }

    /**
     * List registered agent IDs.
     *
     * @return agent IDs
     */
    public List<String> listAgents() {
        return runtime.listAgents();
    }

    /**
     * Subscribe an agent to a topic.
     *
     * @param agentId agent ID
     * @param topic topic pattern
     */
    public void subscribe(String agentId, String topic) {
        runtime.subscribe(agentId, topic);
    }

    /**
     * Unsubscribe an agent from a topic.
     *
     * @param agentId agent ID
     * @param topic topic pattern
     */
    public void unsubscribe(String agentId, String topic) {
        runtime.unsubscribe(agentId, topic);
    }

    /**
     * Send a P2P message between registered team agents.
     *
     * @param message payload
     * @param recipient recipient agent ID
     * @param sender sender agent ID
     * @param sessionId optional session ID
     * @param timeout optional timeout in seconds
     * @return response future
     */
    public CompletableFuture<Object> send(
            Object message,
            String recipient,
            String sender,
            String sessionId,
            Double timeout
    ) {
        if (!runtime.hasAgent(sender)) {
            throw new IllegalStateException("Sender '" + sender + "' not found in team '" + teamId + "'");
        }
        if (!runtime.hasAgent(recipient)) {
            throw new IllegalStateException("Recipient '" + recipient + "' not found in team '" + teamId + "'");
        }
        return runtime.send(message, recipient, sender, sessionId, timeout);
    }

    /**
     * Send a P2P message between registered team agents.
     *
     * @param message payload
     * @param recipient recipient agent ID
     * @param sender sender agent ID
     * @return response future
     */
    public CompletableFuture<Object> send(Object message, String recipient, String sender) {
        return send(message, recipient, sender, null, null);
    }

    /**
     * Publish a message from a registered agent.
     *
     * @param message payload
     * @param topicId topic ID
     * @param sender sender agent ID
     * @param sessionId optional session ID
     * @return completion future
     */
    public CompletableFuture<Void> publish(Object message, String topicId, String sender, String sessionId) {
        if (!runtime.hasAgent(sender)) {
            throw new IllegalStateException("Sender '" + sender + "' not found in team '" + teamId + "'");
        }
        return runtime.publish(message, topicId, sender, sessionId);
    }

    /**
     * Publish a message from a registered agent.
     *
     * @param message payload
     * @param topicId topic ID
     * @param sender sender agent ID
     * @return completion future
     */
    public CompletableFuture<Void> publish(Object message, String topicId, String sender) {
        return publish(message, topicId, sender, null);
    }
    
    /**
     * Invoke the team with input.
     * 
     * @param input Input message
     * @return CompletableFuture with result
     */
    public abstract CompletableFuture<Object> invoke(Object input);

    /**
     * Invoke with an explicit agent-team session.
     *
     * <p>Mirrors Python's {@code BaseTeam.invoke(message, session=None)}
     * signature while keeping existing Java subclasses source-compatible.</p>
     *
     * @param input input message
     * @param session agent-team session, or null
     * @return result future
     */
    public CompletableFuture<Object> invoke(Object input, Session session) {
        return invoke(input);
    }
    
    /**
     * Stream execution (to be implemented by subclasses).
     * 
     * @param input Input message
     * @return Stream of results
     */
    public abstract java.util.stream.Stream<Object> stream(Object input);

    /**
     * Stream with an explicit agent-team session.
     *
     * <p>Mirrors Python's {@code BaseTeam.stream(message, session=None)}
     * signature while keeping existing Java subclasses source-compatible.</p>
     *
     * @param input input message
     * @param session agent-team session, or null
     * @return result stream
     */
    public java.util.stream.Stream<Object> stream(Object input, Session session) {
        return stream(input);
    }
    
    // Getters
    public TeamCard getCard() { return card; }
    public TeamConfig getConfig() { return config; }
    public String getTeamId() { return teamId; }
    public TeamRuntime getRuntime() { return runtime; }
}
