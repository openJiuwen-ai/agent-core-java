/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Manager for agent callback/rail registration and execution.
 *
 * <p>Supports both function-style and rail-style callbacks with priority ordering.
 * Uses the Runner.callbackFramework() with agent_id-prefixed event names to avoid collisions.</p>
 */
public class AgentCallbackManager {

    private final String agentId;
    private final Map<String, Map<Consumer<AgentCallbackContext>, Function<Map<String, Object>, Object>>> wrappedCallbacks =
            new ConcurrentHashMap<>();
    private final Map<AgentRail, List<RailRegistration>> railRegistrations = new ConcurrentHashMap<>();

    public AgentCallbackManager(String agentId) {
        this.agentId = agentId;
    }

    /**
     * Register an agent callback for an event.
     *
     * @param event    the agent callback event
     * @param callback the callback consumer
     * @param priority execution priority (lower = runs first)
     */
    public void registerCallback(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback, int priority) {
        String agentEvent = getAgentEvent(event);
        Function<Map<String, Object>, Object> wrappedCallback = kwargs -> {
            Object ctxObj = kwargs.get("ctx");
            if (ctxObj instanceof AgentCallbackContext ctx) {
                callback.accept(ctx);
            }
            return null;
        };
        wrappedCallbacks
                .computeIfAbsent(agentEvent, key -> new ConcurrentHashMap<>())
                .put(callback, wrappedCallback);
        String callbackName = agentEvent + "_cb_" + Integer.toHexString(System.identityHashCode(callback));
        Runner.callbackFramework().register(agentEvent, wrappedCallback, priority, callbackName);
    }

    /**
     * Register an agent callback with default priority.
     */
    public void registerCallback(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback) {
        registerCallback(event, callback, 100);
    }

    /**
     * Register a rail instance.
     *
     * @param rail  the AgentRail to register
     * @param agent the BaseAgent instance (for tool registration)
     */
    public void registerRail(AgentRail rail, Object agent) {
        List<RailRegistration> registrations = new ArrayList<>();
        for (Map.Entry<AgentCallbackEvent, Consumer<AgentCallbackContext>> entry : rail.getCallbacks().entrySet()) {
            registerCallback(entry.getKey(), entry.getValue(), rail.getPriority());
            registrations.add(new RailRegistration(entry.getKey(), entry.getValue()));
        }
        railRegistrations.put(rail, registrations);

        if (rail.getTools() != null && !rail.getTools().isEmpty()) {
            if (agent instanceof BaseAgent baseAgent) {
                for (Object toolCard : rail.getTools()) {
                    baseAgent.getAbilityManager().add(toolCard);
                }
            }
        }
    }

    /**
     * Unregister a rail instance.
     *
     * @param rail  the AgentRail to unregister
     * @param agent the BaseAgent instance (for tool removal)
     */
    public void unregisterRail(AgentRail rail, Object agent) {
        List<RailRegistration> registrations = railRegistrations.remove(rail);
        if (registrations != null) {
            for (RailRegistration registration : registrations) {
                unregister(registration.event(), registration.callback());
            }
        }

        if (rail.getTools() != null && !rail.getTools().isEmpty()) {
            if (agent instanceof BaseAgent baseAgent) {
                for (var toolCard : rail.getTools()) {
                    if (toolCard.getName() != null) {
                        baseAgent.getAbilityManager().remove(toolCard.getName());
                    }
                }
            }
        }
    }

    /**
     * Unregister a callback from an event.
     *
     * @param event    the event
     * @param callback the original callback consumer
     */
    public void unregister(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback) {
        String agentEvent = getAgentEvent(event);
        Map<Consumer<AgentCallbackContext>, Function<Map<String, Object>, Object>> callbacksForEvent =
                wrappedCallbacks.get(agentEvent);
        if (callbacksForEvent == null) {
            return;
        }

        Function<Map<String, Object>, Object> wrappedCallback = callbacksForEvent.remove(callback);
        if (wrappedCallback == null) {
            return;
        }

        Runner.callbackFramework().unregister(agentEvent, wrappedCallback);
        if (callbacksForEvent.isEmpty()) {
            wrappedCallbacks.remove(agentEvent);
        }
    }

    /**
     * Clear hooks for a specific event or all events.
     *
     * @param event specific event to clear, or null to clear all
     */
    public void clear(AgentCallbackEvent event) {
        if (event != null) {
            String agentEvent = getAgentEvent(event);
            Runner.callbackFramework().unregisterEvent(agentEvent);
            wrappedCallbacks.remove(agentEvent);
            railRegistrations.values().forEach(registrations ->
                    registrations.removeIf(registration -> registration.event() == event));
        } else {
            for (AgentCallbackEvent e : AgentCallbackEvent.values()) {
                String agentEvent = getAgentEvent(e);
                Runner.callbackFramework().unregisterEvent(agentEvent);
            }
            wrappedCallbacks.clear();
            railRegistrations.clear();
        }
    }

    /**
     * Check if any hooks are registered for an event.
     *
     * @param event the event to check
     * @return true if hooks are registered
     */
    public boolean hasHooks(AgentCallbackEvent event) {
        String agentEvent = getAgentEvent(event);
        return !Runner.callbackFramework().listCallbacks(agentEvent).isEmpty();
    }

    /**
     * Execute all hooks for an event.
     *
     * @param event the event
     * @param ctx   the callback context
     */
    public void execute(AgentCallbackEvent event, AgentCallbackContext ctx) {
        String agentEvent = getAgentEvent(event);
        Runner.callbackFramework().trigger(agentEvent, Map.of("ctx", ctx));
    }

    /**
     * Generate event name with agent_id prefix.
     */
    private String getAgentEvent(AgentCallbackEvent event) {
        return agentId + "_" + event.getValue();
    }

    private record RailRegistration(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback) {
    }
}
