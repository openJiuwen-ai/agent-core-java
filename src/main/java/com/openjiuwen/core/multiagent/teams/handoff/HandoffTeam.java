/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.multiagent.teams.TeamsUtils;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private volatile boolean internalAgentsReady = false;
    
    public HandoffTeam(TeamCard card) {
        this(card, new HandoffTeamConfig());
    }
    
    public HandoffTeam(TeamCard card, HandoffTeamConfig config) {
        super(card, config != null ? config : new HandoffTeamConfig());
        this.handoffConfig = config != null ? config : new HandoffTeamConfig();
    }
    
    @Override
    public HandoffTeam addAgent(AgentCard card, Supplier<?> provider) {
        if (runtime.hasAgent(card.getId())) {
            return this; // Skip duplicate registration
        }
        super.addAgent(card, provider);
        agentProviders.put(card.getId(), provider);
        internalAgentsReady = false;
        return this;
    }
    
    /**
     * Get start agent ID.
     */
    protected String getStartAgentId() {
        HandoffConfig cfg = handoffConfig.getHandoff();
        if (cfg.getStartAgent().isPresent()) {
            return cfg.getStartAgent().get().getId();
        }
        return card.getAgentCards().stream().findFirst().map(AgentCard::getId).orElse("");
    }

    /**
     * Get coordinator for a session.
     *
     * @param sessionId session ID
     * @return coordinator or null
     */
    protected HandoffOrchestrator lookupCoordinator(String sessionId) {
        return coordinatorRegistry.get(sessionId);
    }

    /**
     * Ensure internal container endpoint agents exist.
     */
    protected synchronized void ensureInternalAgents() {
        if (internalAgentsReady) {
            return;
        }
        HandoffConfig cfg = handoffConfig.getHandoff();
        List<String> agentIds = card.getAgentCards().stream().map(AgentCard::getId).toList();
        Map<String, Set<String>> routeGraph = HandoffOrchestrator.buildRouteGraph(agentIds, cfg.getRoutes());
        for (String agentId : agentIds) {
            AgentCard agentCard = runtime.getAgentCard(agentId);
            if (agentCard == null) {
                continue;
            }
            Set<String> allowedTargets = new HashSet<>(routeGraph.getOrDefault(agentId, Set.of()));
            String endpointId = endpointId(agentId);
            if (runtime.hasAgent(endpointId)) {
                continue;
            }
            AgentCard endpointCard = AgentCard.builder()
                    .id(endpointId)
                    .name(endpointId)
                    .description("handoff endpoint for " + agentId)
                    .build();
            runtime.registerAgent(endpointCard, () -> new ContainerAgent(
                    agentCard,
                    () -> resolveBaseAgent(agentId),
                    allowedTargets,
                    this::lookupCoordinator));
            runtime.subscribe(endpointId, "container_" + agentId);
        }
        internalAgentsReady = true;
    }

    private String endpointId(String agentId) {
        return "__handoff_ep_" + card.getId() + "_" + agentId;
    }

    private BaseAgent resolveBaseAgent(String agentId) {
        Supplier<?> provider = agentProviders.get(agentId);
        Object agent = provider != null ? provider.get() : null;
        if (agent instanceof BaseAgent baseAgent) {
            return baseAgent;
        }
        throw new IllegalStateException("HandoffTeam provider for '" + agentId + "' did not return a BaseAgent");
    }

    /**
     * Execute the handoff chain in a session.
     *
     * @param message input message
     * @param session session
     * @return final result
     */
    protected Object runChain(Object message, Session session) {
        ensureInternalAgents();
        String sessionId = session.getSessionId();
        HandoffOrchestrator coordinator = createCoordinator(sessionId);
        coordinatorRegistry.put(sessionId, coordinator);
        try {
            runtime.publish(
                    new HandoffRequest(message, new ArrayList<>(), sessionId),
                    "container_" + coordinator.getCurrentAgentId(),
                    card.getId(),
                    sessionId).join();
            return coordinator.getDoneFuture().join();
        } finally {
            coordinatorRegistry.remove(sessionId);
            runtime.cleanupSession(sessionId);
        }
    }
    
    @Override
    public CompletableFuture<Object> invoke(Object input) {
        return invoke(input, null);
    }

    /**
     * Invoke the handoff team with an optional session.
     *
     * @param input input message
     * @param session optional session
     * @return result
     */
    @Override
    public CompletableFuture<Object> invoke(Object input, Session session) {
        TeamsUtils.InvokeContext context = TeamsUtils.standaloneInvokeContext(runtime, card, input, session);
        runtime.start();
        try {
            return CompletableFuture.completedFuture(runChain(input, context.getSession()));
        } finally {
            context.cleanup();
        }
    }
    
    @Override
    public Stream<Object> stream(Object input) {
        Object result = invoke(input, null).join();
        return Stream.of(result);
    }
    
    /**
     * Create coordinator for a session.
     */
    protected HandoffOrchestrator createCoordinator(String sessionId) {
        List<String> agentIds = card.getAgentCards().stream().map(AgentCard::getId).toList();
        return new HandoffOrchestrator(getStartAgentId(), agentIds, handoffConfig.getHandoff());
    }

    public boolean isInternalAgentsReady() {
        return internalAgentsReady;
    }

    public ConcurrentHashMap<String, HandoffOrchestrator> getCoordinatorRegistry() {
        return coordinatorRegistry;
    }
}
