/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent;

import com.openjiuwen.core.multiagent.config.TeamConfig;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.Optional;

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
    protected final TeamConfig config;
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
        return new TeamRuntime(new TeamRuntime.RuntimeConfigBuilder()
            .teamId(teamId)
            .build());
    }
    
    /**
     * Add an agent to the team.
     * 
     * @param card AgentCard
     * @param provider Supplier that creates agent instance
     * @return this (supports chaining)
     */
    public BaseTeam addAgent(AgentCard card, Supplier<?> provider) {
        runtime.registerAgent(card, provider);
        return this;
    }
    
    /**
     * Remove an agent from the team.
     * 
     * @param agentId Agent ID
     * @return this (supports chaining)
     */
    public BaseTeam removeAgent(String agentId) {
        runtime.unregisterAgent(agentId);
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
     * Invoke the team with input.
     * 
     * @param input Input message
     * @return CompletableFuture with result
     */
    public abstract CompletableFuture<Object> invoke(Object input);
    
    /**
     * Stream execution (to be implemented by subclasses).
     * 
     * @param input Input message
     * @return Stream of results
     */
    public abstract java.util.stream.Stream<Object> stream(Object input);
    
    // Getters
    public TeamCard getCard() { return card; }
    public TeamConfig getConfig() { return config; }
    public String getTeamId() { return teamId; }
    public TeamRuntime getRuntime() { return runtime; }
}