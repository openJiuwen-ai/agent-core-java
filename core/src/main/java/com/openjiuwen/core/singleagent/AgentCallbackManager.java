/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Manager for agent callback/rail registration and execution.
 * <p>
 * Supports both function-style and rail-style callbacks with priority ordering.
 * Uses the Runner.callbackFramework() with agent_id-prefixed event names to avoid collisions.
 * </p>
 * 
 * @since 0.1.7
 */
public class AgentCallbackManager {
    private final String agentId;

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Map<Consumer<AgentCallbackContext>, Function<Map<String, Object>, Object>>>
        wrappedCallbacks = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, List<RegisteredCallback>> localCallbacks = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<AgentRail, List<RailRegistration>> railRegistrations = new ConcurrentHashMap<>();

    /**
     * AgentCallbackManager.
     * 
     * @param agentId agentId
     * @since 0.1.7
     */
    public AgentCallbackManager(String agentId) {
        this.agentId = agentId;
    }

    /**
     * Register an agent callback for an event.
     * 
     * @param event the agent callback event
     * @param callback the callback consumer
     * @param priority execution priority (higher value runs first)
     * @since 0.1.7
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
        wrappedCallbacks.computeIfAbsent(agentEvent, key -> new ConcurrentHashMap<>()).put(callback, wrappedCallback);
        // The list mutation (add + priority sort) runs inside the per-event
        // map slot: compute serializes against unregister's computeIfPresent
        // for the same event, so a concurrent unregister can never observe
        // (and remove) an empty list between the add and the sort. The sort
        // itself is NOT covered by SynchronizedList (no sort override in the
        // JDK), so the manual synchronized section is also the only CME
        // protection for concurrent register/register interleavings.
        localCallbacks.compute(agentEvent, (key, existing) -> {
            List<RegisteredCallback> callbacks = existing != null ? existing
                    : Collections.synchronizedList(new ArrayList<RegisteredCallback>());
            synchronized (callbacks) {
                callbacks.add(new RegisteredCallback(callback, priority));
                callbacks.sort((left, right) -> Integer.compare(right.priority(), left.priority()));
            }
            return callbacks;
        });
        String callbackName = agentEvent + "_cb_" + Integer.toHexString(System.identityHashCode(callback));
        Runner.callbackFramework().register(agentEvent, wrappedCallback, priority, callbackName);
    }

    /**
     * Register an agent callback with default priority.
     * 
     * @param event event
     * @param callback callback
     * @since 0.1.7
     */
    public void registerCallback(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback) {
        registerCallback(event, callback, 100);
    }

    /**
     * Register a rail instance.
     * 
     * @param rail the AgentRail to register
     * @param agent the BaseAgent instance (for tool registration)
     * @since 0.1.7
     */
    public void registerRail(AgentRail rail, Object agent) {
        rail.init(agent);
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
     * <p>Each cleanup step (callback unregister, tool-card removal,
     * rail uninit) is individually protected: a failure in
     * one step is logged and the remaining steps still run, so a single
     * broken rail cannot leave the whole rail batch half-destroyed. The
     * broad {@code Exception} catch is the required isolation semantic
     * (uninit runs user code outside core control), not a style choice.</p>
     *
     * @param rail the AgentRail to unregister
     * @param agent the BaseAgent instance (for tool removal)
     * @since 0.1.7
     */
    public void unregisterRail(AgentRail rail, Object agent) {
        List<RailRegistration> registrations = railRegistrations.remove(rail);
        if (registrations != null) {
            for (RailRegistration registration : registrations) {
                try {
                    unregister(registration.event(), registration.callback());
                } catch (Exception e) {
                    Loggers.AGENT.error("[unregisterRail] rail '{}' callback unregister failed for '{}'; continue",
                            railName(rail), registration.event(), e);
                }
            }
        }

        unregisterRailTools(rail, agent);
        uninitRail(rail, agent);
    }

    private void unregisterRailTools(AgentRail rail, Object agent) {
        try {
            if (rail.getTools() == null || rail.getTools().isEmpty()) {
                return;
            }
            if (!(agent instanceof BaseAgent baseAgent)) {
                return;
            }
            for (var toolCard : rail.getTools()) {
                if (toolCard.getName() != null) {
                    baseAgent.getAbilityManager().remove(toolCard.getName());
                }
            }
        } catch (Exception e) {
            Loggers.AGENT.error("[unregisterRail] rail '{}' tool-card removal failed; continue", railName(rail), e);
        }
    }

    private static void uninitRail(AgentRail rail, Object agent) {
        try {
            rail.uninit(agent);
        } catch (Exception e) {
            Loggers.AGENT.error("[unregisterRail] rail '{}' uninit failed; continue", railName(rail), e);
        }
    }

    private static String railName(AgentRail rail) {
        return rail != null ? rail.getClass().getName() : "null";
    }

    /**
     * Unregister every rail registered on this manager.
     *
     * <p>Per-task DeepAgent instances register business rails on their inner
     * BaseAgent; until the rails are unregistered, the process-global
     * {@code Runner.callbackFramework()} keeps one CallbackInfo per rail
     * callback alive, pinning the whole agent object graph. This snapshot
     * based bulk unregister releases all of them.</p>
     *
     * <p>Per-rail isolation: one rail's cleanup failure is
     * logged and the batch continues, so destroy always reaches every
     * remaining rail.</p>
     *
     * @param agent the BaseAgent instance (for tool removal)
     * @since 0.1.15
     */
    public void unregisterAllRails(Object agent) {
        List<AgentRail> rails = new ArrayList<>(railRegistrations.keySet());
        for (AgentRail rail : rails) {
            try {
                unregisterRail(rail, agent);
            } catch (Exception e) {
                // Broad catch is the required batch-isolation semantic: rail
                // metadata access (getTools/getPriority) runs user code and
                // must not abort the remaining rails.
                Loggers.AGENT.error("[unregisterAllRails] rail '{}' cleanup failed; continue", railName(rail), e);
            }
        }
    }

    /**
     * Unregister a callback from an event.
     * 
     * @param event the event
     * @param callback the original callback consumer
     * @since 0.1.7
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

        // Runs inside the per-event map slot so a concurrent registerCallback
        // (compute on the same key) cannot add between the removeIf and the
        // map removal: the list entry and its contents stay consistent.
        localCallbacks.computeIfPresent(agentEvent, (key, callbacksForAgentEvent) -> {
            synchronized (callbacksForAgentEvent) {
                callbacksForAgentEvent.removeIf(
                    registeredCallback -> registeredCallback.callback().equals(callback));
                return callbacksForAgentEvent.isEmpty() ? null : callbacksForAgentEvent;
            }
        });

        Runner.callbackFramework().unregister(agentEvent, wrappedCallback);
        if (callbacksForEvent.isEmpty()) {
            wrappedCallbacks.remove(agentEvent);
        }
    }

    /**
     * Clear hooks for a specific event or all events.
     * 
     * @param event specific event to clear, or null to clear all
     * @since 0.1.7
     */
    public void clear(AgentCallbackEvent event) {
        if (event != null) {
            String agentEvent = getAgentEvent(event);
            Runner.callbackFramework().unregisterEvent(agentEvent);
            wrappedCallbacks.remove(agentEvent);
            localCallbacks.remove(agentEvent);
            railRegistrations.values()
                    .forEach(registrations -> registrations.removeIf(registration -> registration.event() == event));
        } else {
            for (AgentCallbackEvent e : AgentCallbackEvent.values()) {
                String agentEvent = getAgentEvent(e);
                Runner.callbackFramework().unregisterEvent(agentEvent);
            }
            wrappedCallbacks.clear();
            localCallbacks.clear();
            railRegistrations.clear();
        }
    }

    /**
     * Check if any hooks are registered for an event.
     * 
     * @param event the event to check
     * @return true if hooks are registered
     * @since 0.1.7
     */
    public boolean hasHooks(AgentCallbackEvent event) {
        String agentEvent = getAgentEvent(event);
        return !Runner.callbackFramework().listCallbacks(agentEvent).isEmpty();
    }

    /**
     * Execute all hooks for an event.
     * 
     * @param event the event
     * @param ctx the callback context
     * @since 0.1.7
     */
    public void execute(AgentCallbackEvent event, AgentCallbackContext ctx) {
        String agentEvent = getAgentEvent(event);
        List<RegisteredCallback> callbacksForEvent = localCallbacks.get(agentEvent);
        if (callbacksForEvent == null) {
            return;
        }
        List<RegisteredCallback> snapshot;
        // Copy under the list mutex: registerCallback mutates the list inside
        // the same mutex, so an unguarded copy could hit a mid-sort iterator.
        synchronized (callbacksForEvent) {
            snapshot = new ArrayList<RegisteredCallback>(callbacksForEvent);
        }
        snapshot.sort((left, right) -> Integer.compare(right.priority(), left.priority()));
        for (RegisteredCallback registeredCallback : snapshot) {
            registeredCallback.callback().accept(ctx);
        }
    }

    /**
     * Generate event name with agent_id prefix.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
    private String getAgentEvent(AgentCallbackEvent event) {
        return agentId + "_" + event.getValue();
    }

    /**
     * RailRegistration.
     * 
     * @param event event
     * @param callback callback
     * @since 0.1.7
     */
    private record RailRegistration(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback) {
    }

    /**
     * RegisteredCallback.
     * 
     * @param callback callback
     * @param priority priority
     * @since 0.1.7
     */
    private record RegisteredCallback(Consumer<AgentCallbackContext> callback, int priority) {
    }
}
