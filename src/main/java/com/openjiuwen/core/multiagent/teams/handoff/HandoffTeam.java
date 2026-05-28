/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Event-driven handoff multi-agent team.
 * <p>
 * Mirrors Python's {@code HandoffTeam} in 
 * {@code openjiuwen.core.multi_agent.teams.handoff.handoff_team}.
 * <p>
 * Agents collaborate via sequential handoffs driven by pub/sub message bus.
 */
public class HandoffTeam extends BaseTeam {
    
    private final HandoffTeamConfig handoffConfig;
    private final ConcurrentHashMap<String, Supplier<?>> agentProviders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HandoffOrchestrator> coordinatorRegistry = new ConcurrentHashMap<>();
    
    public HandoffTeam(TeamCard card) {
        this(card, new HandoffTeamConfig());
    }
    
    public HandoffTeam(TeamCard card, HandoffTeamConfig config) {
        super(card, config);
        this.handoffConfig = config;
    }
    
    @Override
    public HandoffTeam addAgent(AgentCard card, Supplier<?> provider) {
        if (runtime.hasAgent(card.getId())) {
            return this; // Skip duplicate registration
        }
        super.addAgent(card, provider);
        agentProviders.put(card.getId(), provider);
        return this;
    }
    
    /**
     * Get start agent ID.
     */
    private String getStartAgentId() {
        HandoffConfig cfg = handoffConfig.getHandoff();
        if (cfg.getStartAgent().isPresent()) {
            return cfg.getStartAgent().get().getId();
        }
        // Return first registered agent
        return runtime.getAgentIds().stream().findFirst().orElse("");
    }
    
    @Override
    public CompletableFuture<Object> invoke(Object input) {
        // TODO: Implement full handoff orchestration
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public Stream<Object> stream(Object input) {
        // TODO: Implement streaming handoff
        return Stream.empty();
    }
    
    /**
     * Create coordinator for a session.
     */
    protected HandoffOrchestrator createCoordinator(String sessionId) {
        List<String> agentIds = new ArrayList<>(runtime.getAgentIds());
        return new HandoffOrchestrator(getStartAgentId(), agentIds, handoffConfig.getHandoff());
    }
}