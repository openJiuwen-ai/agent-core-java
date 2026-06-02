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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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
    private final Function<String, HandoffOrchestrator> coordinatorLookup;
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
        this(targetCard, targetProvider, allowedTargets, null);
    }

    /**
     * Create a ContainerAgent wrapper with coordinator lookup.
     *
     * @param targetCard AgentCard for the wrapped agent
     * @param targetProvider Supplier that creates the target agent instance
     * @param allowedTargets Set of agent IDs this agent can hand off to
     * @param coordinatorLookup function that resolves a session coordinator
     */
    public ContainerAgent(
            AgentCard targetCard,
            Supplier<BaseAgent> targetProvider,
            Set<String> allowedTargets,
            Function<String, HandoffOrchestrator> coordinatorLookup) {
        super(targetCard);
        this.targetProvider = targetProvider;
        this.allowedTargets = allowedTargets;
        this.coordinatorLookup = coordinatorLookup;
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
            runtime.getSubscriptionManager().subscribe(agentId, topicPattern);
        }
    }
    
    @Override
    public void unsubscribe(String topicPattern) {
        if (runtime != null) {
            runtime.getSubscriptionManager().unsubscribe(agentId, topicPattern);
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

    /**
     * Build target-agent input from handoff request.
     *
     * @param inputs request input
     * @return raw or history-enriched input
     */
    protected Object buildAgentInput(HandoffRequest inputs) {
        Object message = inputs.getInputMessage();
        List<Map<String, Object>> history = inputs.getHistory();
        if (history == null || history.isEmpty()) {
            return message;
        }
        if (message instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> result.put(String.valueOf(key), value));
            result.put("handoff_history", history);
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", message);
        result.put("handoff_history", history);
        return result;
    }

    /**
     * Remove tool messages and assistant tool-call messages from context history.
     *
     * @param messages message list
     * @return cleaned list
     */
    public static List<Object> stripHandoffMessages(List<?> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Object> cleaned = new ArrayList<>();
        for (Object message : messages) {
            Object role = readProperty(message, "role");
            if ("tool".equals(role)) {
                continue;
            }
            Object toolCalls = readProperty(message, "toolCalls");
            if (toolCalls == null) {
                toolCalls = readProperty(message, "tool_calls");
            }
            if ("assistant".equals(role) && toolCalls instanceof List<?> list && !list.isEmpty()) {
                continue;
            }
            cleaned.add(message);
        }
        return cleaned;
    }

    private static Object readProperty(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        String accessor = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return target.getClass().getMethod(accessor).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            try {
                var field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        return Collections.singletonList(invoke(inputs, session)).iterator();
    }

    @Override
    public Object invoke(Object inputs, Session session) {
        if (!(inputs instanceof HandoffRequest request)) {
            return Map.of();
        }
        HandoffOrchestrator coordinator = coordinatorLookup != null
                ? coordinatorLookup.apply(request.getSessionId())
                : null;
        if (coordinator == null) {
            throw new IllegalStateException("ContainerAgent invoked without a HandoffTeam session");
        }
        List<Map<String, Object>> history = request.getHistory() != null
                ? new ArrayList<>(request.getHistory())
                : new ArrayList<>();
        try {
            BaseAgent targetAgent = getTargetAgent();
            injectToolsOnce(targetAgent);
            Object result = targetAgent.invoke(buildAgentInput(request), session);
            history.add(Map.of("agent", targetAgent.getCard().getId(), "output", result));

            Optional<TeamInterruptSignal> interruptSignal = Interrupt.extractInterruptSignal(result);
            if (interruptSignal.isPresent()) {
                coordinator.complete(interruptSignal.get().getResult());
                return Map.of();
            }

            Optional<HandoffSignal> signal = HandoffSignal.extractHandoffSignal(result, session);
            if (signal.isEmpty()) {
                coordinator.complete(result);
                return Map.of();
            }

            HandoffSignal handoffSignal = signal.get();
            if (coordinator.requestHandoff(handoffSignal.getTarget())) {
                Object nextInput = handoffSignal.getMessage().orElse(request.getInputMessage() != null
                        ? request.getInputMessage().toString()
                        : null);
                publish(new HandoffRequest(nextInput, history, request.getSessionId()),
                        "container_" + handoffSignal.getTarget(), request.getSessionId());
            } else {
                coordinator.complete(result);
            }
        } catch (RuntimeException e) {
            Optional<TeamInterruptSignal> interruptSignal = Interrupt.extractInterruptSignal(null, e);
            if (interruptSignal.isPresent()) {
                coordinator.complete(interruptSignal.get().getResult());
                return Map.of();
            }
            coordinator.error(e);
        }
        return Map.of();
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
