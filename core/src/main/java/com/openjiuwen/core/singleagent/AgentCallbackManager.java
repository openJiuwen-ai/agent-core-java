/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.IsolatedActions;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallback;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Manager for function-style and rail-style agent callbacks.
 *
 * <p>Agent-scoped callbacks execute from a per-instance table, so a fire on
 * one agent never runs callbacks registered by sibling agents that share the
 * same agentId (per-task agents built from one card); registrations are
 * mirrored into the process-global callback framework for framework-level
 * listing.</p>
 *
 * <p>Mirrors Python's {@code AgentCallbackManager} in
 * {@code openjiuwen/core/single_agent/agent_callback_manager.py}.</p>
 */
public class AgentCallbackManager {
    private final String agentId;
    private final CallbackFramework globalCallbackFramework;
    private final CallbackFramework instanceCallbackFramework;
    private final CallbackFramework localCallbackFramework = new InstanceCallbackFramework();
    private final Map<AgentRail, Map<AgentCallbackEvent, AgentCallback>> railCallbacks = new IdentityHashMap<>();
    private final Map<AgentRail, Map<AgentCallbackEvent, AgentCallback>> instanceRailCallbacks = new IdentityHashMap<>();
    private final Map<AgentRail, Map<String, ToolCard>> railTools = new IdentityHashMap<>();

    public AgentCallbackManager(String agentId) {
        this(agentId, new ReflectionRunnerCallbackFramework());
    }

    AgentCallbackManager(String agentId, CallbackFramework callbackFramework) {
        this.agentId = agentId == null ? "" : agentId;
        this.globalCallbackFramework = callbackFramework == null ? new ReflectionRunnerCallbackFramework() : callbackFramework;
        this.instanceCallbackFramework = new InstanceCallbackFramework();
    }

    /**
     * Registers a callback for the given event.
     *
     * @param event the agent callback event
     * @param callback the callback consumer
     * @param priority execution priority (higher value runs first)
     */
    public CompletionStage<AgentCallbackManager> registerCallback(AgentCallbackEvent event,
                                                                   AgentCallback callback,
                                                                   int priority) {
        if (event == null || callback == null) {
            return CompletableFuture.completedFuture(this);
        }
        return localCallbackFramework.register(getAgentEvent(event), callback, priority)
                .thenCompose(ignored -> globalCallbackFramework.register(getAgentEvent(event), callback, priority))
                .thenApply(ignored -> this);
    }

    public CompletionStage<AgentCallbackManager> register_callback(AgentCallbackEvent event,
                                                                   AgentCallback callback,
                                                                   int priority) {
        return registerCallback(event, callback, priority);
    }

    public CompletionStage<AgentCallbackManager> registerRail(AgentRail rail, Object agent) {
        if (rail == null) {
            return CompletableFuture.completedFuture(this);
        }
        Map<AgentCallbackEvent, AgentCallback> callbacks = rail.getCallbacks();
        railCallbacks.put(rail, callbacks);
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (Map.Entry<AgentCallbackEvent, AgentCallback> entry : callbacks.entrySet()) {
            chain = chain.thenCompose(ignored -> localCallbackFramework.register(
                            getAgentEvent(entry.getKey()), entry.getValue(), rail.getPriority())
                    .thenCompose(v -> globalCallbackFramework.register(
                            getAgentEvent(entry.getKey()), entry.getValue(), rail.getPriority())));
        }
        return chain.thenApply(ignored -> {
            registerRailTools(rail, agent);
            return this;
        });
    }

    public CompletionStage<AgentCallbackManager> register_rail(AgentRail rail, Object agent) {
        return registerRail(rail, agent);
    }

    /**
     * Whether {@code rail} is registered on the global callback framework.
     *
     * @param rail the rail to look up
     * @return {@code true} if the rail is currently registered
     * @since 0.1.15
     */
    public boolean isRailRegistered(AgentRail rail) {
        return rail != null && railCallbacks.containsKey(rail);
    }

    public CompletionStage<AgentCallbackManager> registerInstanceRail(AgentRail rail, Object agent) {
        if (rail == null) {
            return CompletableFuture.completedFuture(this);
        }
        Map<AgentCallbackEvent, AgentCallback> callbacks = rail.getCallbacks();
        instanceRailCallbacks.put(rail, callbacks);
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (Map.Entry<AgentCallbackEvent, AgentCallback> entry : callbacks.entrySet()) {
            chain = chain.thenCompose(ignored -> instanceCallbackFramework.register(
                    getInstanceEvent(entry.getKey()),
                    entry.getValue(),
                    rail.getPriority()
            ));
        }
        return chain.thenApply(ignored -> {
            registerRailTools(rail, agent);
            return this;
        });
    }

    private void registerRailTools(AgentRail rail, Object agent) {
        if (!(agent instanceof BaseAgent baseAgent) || railTools.containsKey(rail)) {
            return;
        }
        Map<String, ToolCard> owned = new LinkedHashMap<>();
        railTools.put(rail, owned);
        List<ToolCard> cards = rail.getToolCards();
        if (cards == null) {
            return;
        }
        AbilityManager abilities = baseAgent.getAbilityManager();
        for (ToolCard card : cards) {
            Object existing = abilities.get(card.getName()).orElse(null);
            if (existing != null) {
                Loggers.AGENT.warning("[registerRail] tool '{}' already exists; keep its current owner",
                        card.getName());
                continue;
            }
            AddAbilityResult result = abilities.add(card);
            if (result.isAdded() && "added_tool".equals(result.getReason())) {
                owned.put(card.getName(), card);
            } else {
                Loggers.AGENT.warning("[registerRail] tool '{}' was not acquired: {}",
                        card.getName(), result.getReason());
            }
        }
    }

    public CompletionStage<Void> unregisterRail(AgentRail rail, Object agent) {
        if (rail == null) {
            return CompletableFuture.completedFuture(null);
        }
        Map<AgentCallbackEvent, AgentCallback> callbacks = railCallbacks.remove(rail);
        if (callbacks == null) {
            callbacks = rail.getCallbacks();
        }
        for (Map.Entry<AgentCallbackEvent, AgentCallback> entry : callbacks.entrySet()) {
            // Per-callback isolation: one failing unregister (e.g. a broken
            // callback key) must not abort the remaining rails.
            IsolatedActions.runIsolated(() -> {
                localCallbackFramework.unregister(getAgentEvent(entry.getKey()), entry.getValue())
                        .toCompletableFuture().join();
                globalCallbackFramework.unregister(getAgentEvent(entry.getKey()), entry.getValue())
                        .toCompletableFuture().join();
                return null;
            }).ifPresent(error -> Loggers.AGENT.error(
                    "[unregisterRail] rail '{}' callback unregister failed for '{}'; continue",
                    railName(rail), entry.getKey(), error));
        }
        unregisterRailTools(rail, agent);
        return CompletableFuture.completedFuture(null);
    }

    private void unregisterRailTools(AgentRail rail, Object agent) {
        if (railCallbacks.containsKey(rail) || instanceRailCallbacks.containsKey(rail)) {
            return;
        }
        Map<String, ToolCard> owned = railTools.remove(rail);
        if (owned == null || !(agent instanceof BaseAgent baseAgent)) {
            return;
        }
        // Release the registration snapshot, never the rail's mutable declaration.
        IsolatedActions.runIsolated(() -> {
            for (Map.Entry<String, ToolCard> entry : owned.entrySet()) {
                AbilityManager abilities = baseAgent.getAbilityManager();
                if (abilities.get(entry.getKey()).orElse(null) == entry.getValue()) {
                    abilities.remove(entry.getKey());
                }
            }
            return null;
        }).ifPresent(error -> Loggers.AGENT.error(
                "[unregisterRail] rail '{}' tool-card removal failed; continue", railName(rail), error));
    }

    private static String railName(AgentRail rail) {
        return rail == null ? "null" : rail.getClass().getName();
    }

    public CompletionStage<Void> unregister_rail(AgentRail rail, Object agent) {
        return unregisterRail(rail, agent);
    }

    /**
     * Unregister every rail registered on this manager.
     *
     * <p>Per-task DeepAgent instances register business rails on their inner
     * BaseAgent; until the rails are unregistered, the process-global
     * callback framework keeps one CallbackInfo per rail callback alive,
     * pinning the whole agent object graph. This snapshot-based bulk
     * unregister releases all of them without clearing shared event names.</p>
     *
     * @param agent the BaseAgent instance (for rail uninit)
     */
    public CompletionStage<Void> unregisterAllRails(Object agent) {
        List<AgentRail> rails = new ArrayList<>(railCallbacks.keySet());
        List<AgentRail> instanceRails = new ArrayList<>(instanceRailCallbacks.keySet());
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (AgentRail rail : rails) {
            // Per-rail isolation: rail cleanup (unregister + uninit) runs user
            // code outside core control, so one broken rail must not abort the
            // batch and leave the remaining rails pinned.
            chain = chain.thenCompose(ignored -> unregisterRail(rail, agent)
                    .thenAccept(v -> uninitQuietly(rail, agent))
                    .exceptionally(error -> null));
        }
        for (AgentRail rail : instanceRails) {
            chain = chain.thenCompose(ignored -> unregisterInstanceRail(rail, agent)
                    .thenAccept(v -> uninitQuietly(rail, agent))
                    .exceptionally(error -> null));
        }
        return chain;
    }

    /**
     * Runs the rail's uninit inline, capturing any failure it throws so one
     * broken rail does not abort the batch; the failure is logged and the
     * batch continues. Dispatches through the typed {@code uninit(BaseAgent)}
     * overload whenever the host is a BaseAgent so rails that only override
     * the typed hook (e.g. cleanup in {@code uninit(BaseAgent)}) still run.
     *
     * @param rail the rail to uninitialize, may be null
     * @param agent the agent instance passed to the rail's uninit
     */
    private static void uninitQuietly(AgentRail rail, Object agent) {
        IsolatedActions.runIsolated(() -> {
            if (rail == null) {
                return null;
            }
            if (agent instanceof BaseAgent baseAgent) {
                rail.uninit(baseAgent);
            } else {
                rail.uninit(agent);
            }
            return null;
        }).ifPresent(error -> Loggers.AGENT.error("[unregisterAllRails] rail '{}' uninit failed; continue",
                rail != null ? rail.getClass().getName() : "null", error));
    }

    public CompletionStage<Void> unregisterInstanceRail(AgentRail rail, Object agent) {
        if (rail == null) {
            return CompletableFuture.completedFuture(null);
        }
        Map<AgentCallbackEvent, AgentCallback> callbacks = instanceRailCallbacks.remove(rail);
        if (callbacks == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (Map.Entry<AgentCallbackEvent, AgentCallback> entry : callbacks.entrySet()) {
            chain = chain.thenCompose(ignored -> instanceCallbackFramework.unregister(getInstanceEvent(entry.getKey()),
                    entry.getValue()));
        }
        return chain.thenRun(() -> unregisterRailTools(rail, agent));
    }

    public CompletionStage<Void> unregister(AgentCallbackEvent event, AgentCallback callback) {
        if (event == null || callback == null) {
            return CompletableFuture.completedFuture(null);
        }
        return localCallbackFramework.unregister(getAgentEvent(event), callback)
                .thenCompose(ignored -> globalCallbackFramework.unregister(getAgentEvent(event), callback));
    }

    public CompletionStage<Void> clear(AgentCallbackEvent event) {
        if (event != null) {
            return clearAgentEvent(event);
        }
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (AgentCallbackEvent callbackEvent : AgentCallbackEvent.values()) {
            chain = chain.thenCompose(ignored -> clearAgentEvent(callbackEvent));
        }
        return chain;
    }

    private CompletionStage<Void> clearAgentEvent(AgentCallbackEvent event) {
        return localCallbackFramework.unregisterEvent(getAgentEvent(event))
                .thenCompose(ignored -> globalCallbackFramework.unregisterEvent(getAgentEvent(event)));
    }

    public CompletionStage<Void> clearInstance(AgentCallbackEvent event) {
        if (event == null) {
            CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
            for (AgentCallbackEvent callbackEvent : AgentCallbackEvent.values()) {
                chain = chain.thenCompose(ignored -> instanceCallbackFramework.unregisterEvent(
                        getInstanceEvent(callbackEvent)));
            }
            return chain;
        }
        return instanceCallbackFramework.unregisterEvent(getInstanceEvent(event));
    }

    public boolean hasHooks(AgentCallbackEvent event) {
        if (event == null) {
            return false;
        }
        return !localCallbackFramework.listCallbacks(getAgentEvent(event)).toCompletableFuture().join().isEmpty();
    }

    public boolean has_hooks(AgentCallbackEvent event) {
        return hasHooks(event);
    }

    public boolean hasInstanceHooks(AgentCallbackEvent event) {
        if (event == null) {
            return false;
        }
        return !instanceCallbackFramework.listCallbacks(getInstanceEvent(event)).toCompletableFuture().join().isEmpty();
    }

    public CompletionStage<AgentCallbackContext> execute(AgentCallbackEvent event, AgentCallbackContext context) {
        if (event == null) {
            return CompletableFuture.completedFuture(context);
        }
        try {
            localCallbackFramework.trigger(getAgentEvent(event), context).toCompletableFuture().join();
        } catch (CompletionException exception) {
            throw rawCallbackFailure(exception);
        }
        return instanceCallbackFramework.trigger(getInstanceEvent(event), context)
                .thenApply(ignored -> context);
    }

    /**
     * Restores the raw callback failure (abort, interruption, fatal error) so
     * callers observe the original exception instead of its completion
     * wrapper.
     *
     * @param exception the completion wrapper thrown when joining the chain
     * @return the unwrapped failure to rethrow, never {@code null}
     */
    private static RuntimeException rawCallbackFailure(CompletionException exception) {
        Throwable cause = exception;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof AbortError abortError && abortError.getCause() != null) {
            // The runner framework rethrows an abort's cause so interrupt
            // requests keep their original exception type.
            return runtimeFailure(abortError.getCause());
        }
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return exception;
    }

    private static RuntimeException runtimeFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(failure);
    }

    public String agentEventName(AgentCallbackEvent event) {
        return getAgentEvent(event);
    }

    public String getAgentEvent(AgentCallbackEvent event) {
        return agentId + "_" + (event == null ? "" : "AgentCallbackEvent." + event.name());
    }

    public String _get_agent_event(AgentCallbackEvent event) {
        return getAgentEvent(event);
    }

    private String getInstanceEvent(AgentCallbackEvent event) {
        return event == null ? "" : "AgentCallbackEvent." + event.name();
    }

    public String getAgentId() {
        return agentId;
    }

    interface CallbackFramework {
        CompletionStage<Void> register(String event, AgentCallback callback, int priority);

        CompletionStage<Void> unregister(String event, AgentCallback callback);

        CompletionStage<Void> unregisterEvent(String event);

        CompletionStage<List<Object>> listCallbacks(String event);

        CompletionStage<Void> trigger(String event, AgentCallbackContext context);
    }

    private static final class ReflectionRunnerCallbackFramework implements CallbackFramework {
        private final Map<AgentCallback, Function<Map<String, Object>, Object>> wrappers = new HashMap<>();

        @Override
        public CompletionStage<Void> register(String event, AgentCallback callback, int priority) {
            try {
                Object framework = runnerCallbackFramework();
                Function<Map<String, Object>, Object> wrapper = kwargs -> {
                    Object value = kwargs == null ? null : kwargs.get("ctx");
                    AgentCallbackContext context = value instanceof AgentCallbackContext callbackContext
                            ? callbackContext
                            : null;
                    if (context != null) {
                        try {
                            callback.handle(context).toCompletableFuture().join();
                        } catch (ToolInterruptException interruption) {
                            throw new AbortError(interruption.getMessage(), interruption);
                        } catch (CompletionException exception) {
                            throw normalizeCallbackFailure(exception);
                        }
                    }
                    return null;
                };
                wrappers.put(callback, wrapper);
                framework.getClass().getMethod(
                        "register",
                        String.class,
                        Function.class,
                        int.class,
                        boolean.class,
                        String.class,
                        Set.class,
                        List.class,
                        Function.class,
                        Function.class,
                        int.class,
                        double.class,
                        Double.class,
                        String.class
                ).invoke(framework, event, wrapper, priority, false, null, null, null, null, null, 0, 0.0D, null,
                        "agent_callback");
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Missing Runner/callback framework means no callbacks can be registered yet.
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregister(String event, AgentCallback callback) {
            // Wrapper extraction stays outside the best-effort catch: a
            // broken callback key (hashCode/equals throwing) must surface
            // to the caller's per-callback isolation instead of being
            // silently dropped with the callback left registered.
            Function<Map<String, Object>, Object> wrapper = wrappers.remove(callback);
            if (wrapper == null) {
                return CompletableFuture.completedFuture(null);
            }
            try {
                runnerCallbackFramework().getClass()
                        .getMethod("unregister", String.class, Function.class)
                        .invoke(runnerCallbackFramework(), event, wrapper);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Match Python's best-effort unregistration surface when framework is absent.
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterEvent(String event) {
            try {
                runnerCallbackFramework().getClass()
                        .getMethod("unregisterEvent", String.class)
                        .invoke(runnerCallbackFramework(), event);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Match Python's best-effort clear surface when framework is absent.
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<Object>> listCallbacks(String event) {
            try {
                Object result = runnerCallbackFramework().getClass()
                        .getMethod("listCallbacks", String.class)
                        .invoke(runnerCallbackFramework(), event);
                if (result instanceof List<?> list) {
                    return CompletableFuture.completedFuture(List.copyOf(list));
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Missing framework has no hooks.
            }
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<Void> trigger(String event, AgentCallbackContext context) {
            try {
                Map<String, Object> kwargs = new LinkedHashMap<>();
                kwargs.put("ctx", context);
                runnerCallbackFramework().getClass()
                        .getMethod("trigger", String.class, Object[].class, Map.class)
                        .invoke(runnerCallbackFramework(), event, new Object[] {context}, kwargs);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Agent callback execution failed", cause);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Missing framework means there are no callbacks to trigger.
            }
            return CompletableFuture.completedFuture(null);
        }

        private static RuntimeException normalizeCallbackFailure(Throwable error) {
            Throwable normalized = unwrapCallbackFailure(error);
            if (normalized instanceof ToolInterruptException interruption) {
                return new AbortError(interruption.getMessage(), interruption);
            }
            if (normalized instanceof AbortError abortError) {
                throw abortError;
            }
            if (normalized instanceof Error fatal) {
                throw fatal;
            }
            if (error instanceof RuntimeException runtimeException) {
                return runtimeException;
            }
            return new CompletionException(error);
        }

        private static Throwable unwrapCallbackFailure(Throwable error) {
            Throwable current = error;
            while ((current instanceof CompletionException || current instanceof ExecutionException)
                    && current.getCause() != null) {
                current = current.getCause();
            }
            return current;
        }

        private Object runnerCallbackFramework() throws ReflectiveOperationException {
            Class<?> runnerType = Class.forName("com.openjiuwen.core.runner.Runner");
            return runnerType.getMethod("getCallbackFramework").invoke(null);
        }
    }
}
