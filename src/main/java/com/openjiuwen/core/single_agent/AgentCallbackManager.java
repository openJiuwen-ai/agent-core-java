/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent;

import com.openjiuwen.core.single_agent.rail.AgentCallback;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.AgentCallbackEvent;
import com.openjiuwen.core.single_agent.rail.AgentRail;

import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Manager for function-style and rail-style agent callbacks.
 *
 * <p>Mirrors Python's {@code AgentCallbackManager} in
 * {@code openjiuwen/core/single_agent/agent_callback_manager.py}.</p>
 */
public class AgentCallbackManager {
    private final String agentId;
    private final CallbackFramework callbackFramework;
    private final Map<AgentRail, Map<AgentCallbackEvent, AgentCallback>> railCallbacks = new IdentityHashMap<>();

    public AgentCallbackManager(String agentId) {
        this(agentId, new ReflectionRunnerCallbackFramework());
    }

    AgentCallbackManager(String agentId, CallbackFramework callbackFramework) {
        this.agentId = agentId == null ? "" : agentId;
        this.callbackFramework = callbackFramework == null ? new ReflectionRunnerCallbackFramework() : callbackFramework;
    }

    public CompletionStage<AgentCallbackManager> registerCallback(AgentCallbackEvent event,
                                                                  AgentCallback callback,
                                                                  int priority) {
        if (event == null || callback == null) {
            return CompletableFuture.completedFuture(this);
        }
        return callbackFramework.register(getAgentEvent(event), callback, priority)
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
            chain = chain.thenCompose(ignored -> callbackFramework.register(
                    getAgentEvent(entry.getKey()),
                    entry.getValue(),
                    rail.getPriority()
            ));
        }
        return chain.thenApply(ignored -> this);
    }

    public CompletionStage<AgentCallbackManager> register_rail(AgentRail rail, Object agent) {
        return registerRail(rail, agent);
    }

    public CompletionStage<Void> unregisterRail(AgentRail rail, Object agent) {
        if (rail == null) {
            return CompletableFuture.completedFuture(null);
        }
        Map<AgentCallbackEvent, AgentCallback> callbacks = railCallbacks.remove(rail);
        if (callbacks == null) {
            callbacks = rail.getCallbacks();
        }
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (Map.Entry<AgentCallbackEvent, AgentCallback> entry : callbacks.entrySet()) {
            chain = chain.thenCompose(ignored -> callbackFramework.unregister(getAgentEvent(entry.getKey()),
                    entry.getValue()));
        }
        return chain;
    }

    public CompletionStage<Void> unregister_rail(AgentRail rail, Object agent) {
        return unregisterRail(rail, agent);
    }

    public CompletionStage<Void> unregister(AgentCallbackEvent event, AgentCallback callback) {
        if (event == null || callback == null) {
            return CompletableFuture.completedFuture(null);
        }
        return callbackFramework.unregister(getAgentEvent(event), callback);
    }

    public CompletionStage<Void> clear(AgentCallbackEvent event) {
        if (event == null) {
            CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
            for (AgentCallbackEvent callbackEvent : AgentCallbackEvent.values()) {
                chain = chain.thenCompose(ignored -> callbackFramework.unregisterEvent(getAgentEvent(callbackEvent)));
            }
            return chain;
        }
        return callbackFramework.unregisterEvent(getAgentEvent(event));
    }

    public boolean hasHooks(AgentCallbackEvent event) {
        if (event == null) {
            return false;
        }
        return !callbackFramework.listCallbacks(getAgentEvent(event)).toCompletableFuture().join().isEmpty();
    }

    public boolean has_hooks(AgentCallbackEvent event) {
        return hasHooks(event);
    }

    public CompletionStage<AgentCallbackContext> execute(AgentCallbackEvent event, AgentCallbackContext context) {
        if (event == null) {
            return CompletableFuture.completedFuture(context);
        }
        return callbackFramework.trigger(getAgentEvent(event), context)
                .thenApply(ignored -> context);
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
        private final Map<AgentCallback, Function<Map<String, Object>, Object>> wrappers = new IdentityHashMap<>();

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
                        callback.handle(context).toCompletableFuture().join();
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
            try {
                Function<Map<String, Object>, Object> wrapper = wrappers.remove(callback);
                if (wrapper != null) {
                    runnerCallbackFramework().getClass()
                            .getMethod("unregister", String.class, Function.class)
                            .invoke(runnerCallbackFramework(), event, wrapper);
                }
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
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Missing framework means there are no callbacks to trigger.
            }
            return CompletableFuture.completedFuture(null);
        }

        private Object runnerCallbackFramework() throws ReflectiveOperationException {
            Class<?> runnerType = Class.forName("com.openjiuwen.core.runner.Runner");
            return runnerType.getMethod("getCallbackFramework").invoke(null);
        }
    }
}
