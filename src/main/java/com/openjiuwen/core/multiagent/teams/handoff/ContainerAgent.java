/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.teamruntime.CommunicableAgent;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Internal per-agent wrapper created by HandoffTeam.
 * <p>
 * Mirrors Python's {@code ContainerAgent} in 
 * {@code openjiuwen.core.multi_agent.teams.handoff.container_agent}.
 * <p>
 * Wraps a target agent, injects HandoffTools for allowed transfer targets,
 * and implements CommunicableAgent for runtime binding.
 */
public class ContainerAgent extends BaseAgent implements CommunicableAgent {
    
    private static final LoggerProtocol LOGGER = Loggers.MULTI_AGENT;
    
    private final Supplier<BaseAgent> targetProvider;
    private final Set<String> allowedTargets;
    private BaseAgent targetInstance;
    private boolean toolsInjected = false;
    
    // CommunicableAgent state
    private TeamRuntime runtime;
    private String agentId;
    
    /**
     * Create a ContainerAgent wrapper.
     * 
     * @param targetCard AgentCard for the wrapped agent
     * @param targetProvider Supplier that creates the target agent instance
     * @param allowedTargets Set of agent IDs this agent can hand off to
     */
    public ContainerAgent(AgentCard targetCard, Supplier<BaseAgent> targetProvider, Set<String> allowedTargets) {
        super(targetCard);
        this.targetProvider = targetProvider;
        this.allowedTargets = allowedTargets;
        this.targetInstance = null;
        this.toolsInjected = false;
    }
    
    /**
     * Get or lazily create the target agent instance.
     * 
     * @return Target agent instance
     */
    protected BaseAgent getTargetAgent() {
        if (targetInstance == null) {
            targetInstance = targetProvider.get();
        }
        return targetInstance;
    }
    
    /**
     * Inject HandoffTools into the target agent's ability manager (once).
     * 
     * @param targetAgent The target agent to inject tools into
     */
    protected void injectToolsOnce(BaseAgent targetAgent) {
        if (toolsInjected) {
            return;
        }
        toolsInjected = true;
        
        var abilityMgr = targetAgent.getAbilityManager();
        if (abilityMgr == null) {
            LOGGER.debug("[ContainerAgent:{}] {} has no ability_manager, skipping",
                    getCard().getId(), targetAgent.getCard().getId());
            return;
        }
        
        // Inject HandoffTool for each allowed target
        for (String targetId : allowedTargets) {
            AgentCard card = runtime != null ? runtime.getAgentCard(targetId) : null;
            String description = card != null ? card.getDescription() : "";
            HandoffTool tool = new HandoffTool(targetId, description);
            abilityMgr.add(tool.getCard());
            LOGGER.debug("[ContainerAgent:{}] Injected HandoffTool for target: {}",
                    getCard().getId(), targetId);
        }
    }
    
    // ========== CommunicableAgent Implementation ==========
    
    @Override
    public void bindRuntime(TeamRuntime runtime, String agentId) {
        this.runtime = runtime;
        this.agentId = agentId;
    }
    
    @Override
    public boolean isBound() {
        return runtime != null && agentId != null;
    }
    
    @Override
    public CompletableFuture<Object> send(Object message, String recipient, String sessionId) {
        if (runtime == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Agent not bound to runtime"));
        }
        return runtime.getMessageBus().send(
            message, recipient,
            java.util.Optional.ofNullable(agentId),
            java.util.Optional.ofNullable(sessionId),
            java.util.Optional.empty()
        );
    }
    
    @Override
    public CompletableFuture<Void> publish(Object message, String topicId, String sessionId) {
        if (runtime == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Agent not bound to runtime"));
        }
        return runtime.getMessageBus().publish(
            message, topicId,
            java.util.Optional.ofNullable(agentId),
            java.util.Optional.ofNullable(sessionId)
        );
    }
    
    @Override
    public void subscribe(String topicPattern) {
        if (runtime != null) {
            runtime.getSubscriptionManager().subscribe(topicPattern, agentId);
        }
    }
    
    @Override
    public void unsubscribe(String topicPattern) {
        if (runtime != null) {
            runtime.getSubscriptionManager().unsubscribe(topicPattern, agentId);
        }
    }
    
    @Override
    public String getAgentId() {
        return agentId;
    }
    
    @Override
    public TeamRuntime getRuntime() {
        return runtime;
    }
    
    // ========== Getters ==========
    
    public Set<String> getAllowedTargets() {
        return allowedTargets;
    }
    
    public Supplier<BaseAgent> getTargetProvider() {
        return targetProvider;
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        // Delegate to target agent
        if (targetInstance != null) {
            return targetInstance.stream(inputs, session, streamModes);
        }
        return Collections.emptyIterator();
    }

    @Override
    public Object invoke(Object inputs, Session session) {
        if (targetInstance != null) {
            return targetInstance.invoke(inputs, session);
        }
        return null;
    }

    @Override
    public Object getConfig() {
        if (targetInstance != null) {
            return targetInstance.getConfig();
        }
        return null;
    }

    @Override
    public BaseAgent configure(Object config) {
        if (targetInstance != null) {
            targetInstance.configure(config);
        }
        return this;
    }
}