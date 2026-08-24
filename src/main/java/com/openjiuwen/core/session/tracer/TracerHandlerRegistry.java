/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry for tracer extension handlers.
 *
 * <p>External handlers should be registered here before calling {@code agent.invoke}
 * or {@code workflow.invoke}. All {@code Tracer} instances created afterwards will
 * automatically pick up the registered handlers.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.core.session.tracer.tracer.TracerHandlerRegistry}.</p>
 *
 * @since 0.1.7
 */
public final class TracerHandlerRegistry {
    private static final Map<String, TraceExtAgentHandler> AGENT_HANDLERS = new ConcurrentHashMap<>();
    private static final Map<String, TraceExtWorkflowHandler> WORKFLOW_HANDLERS = new ConcurrentHashMap<>();

    /** Reserved names for the built-in handlers; cannot be used for extension registration. */
    private static final Set<String> RESERVED_NAMES;

    static {
        Set<String> names = Collections.newSetFromMap(new ConcurrentHashMap<>());
        for (TracerHandlerName name : EnumSet.allOf(TracerHandlerName.class)) {
            names.add(name.getValue());
        }
        RESERVED_NAMES = names;
    }

    private TracerHandlerRegistry() {
    }

    /**
     * Register an extension handler globally.
     *
     * @param handlerName unique name for the handler
     * @param handler     {@link TraceExtAgentHandler} or {@link TraceExtWorkflowHandler} instance
     * @throws IllegalArgumentException if the name is reserved, already registered, or the handler type is wrong
     */
    public static void registerHandler(String handlerName, Object handler) {
        if (handlerName == null || handlerName.isEmpty()) {
            throw new IllegalArgumentException("handler_name must not be null or empty");
        }
        if (RESERVED_NAMES.contains(handlerName)) {
            throw new IllegalArgumentException(
                    "Handler '" + handlerName + "' is a reserved name for built-in handlers, "
                            + "cannot be used for extension handler registration");
        }
        if (AGENT_HANDLERS.containsKey(handlerName) || WORKFLOW_HANDLERS.containsKey(handlerName)) {
            throw new IllegalArgumentException("Handler '" + handlerName + "' already registered");
        }
        if (handler instanceof TraceExtAgentHandler) {
            AGENT_HANDLERS.put(handlerName, (TraceExtAgentHandler) handler);
        } else if (handler instanceof TraceExtWorkflowHandler) {
            WORKFLOW_HANDLERS.put(handlerName, (TraceExtWorkflowHandler) handler);
        } else {
            throw new IllegalArgumentException(
                    "Handler '" + handlerName + "' must be TraceExtAgentHandler or "
                            + "TraceExtWorkflowHandler, got "
                            + (handler != null ? handler.getClass().getName() : "null"));
        }
    }

    /**
     * Return all registered agent handlers (copy).
     *
     * @return an unmodifiable copy of the agent handlers map
     */
    public static Map<String, TraceExtAgentHandler> getAgentHandlers() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(AGENT_HANDLERS));
    }

    /**
     * Return all registered workflow handlers (copy).
     *
     * @return an unmodifiable copy of the workflow handlers map
     */
    public static Map<String, TraceExtWorkflowHandler> getWorkflowHandlers() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(WORKFLOW_HANDLERS));
    }

    /**
     * Remove a previously registered handler by name.
     *
     * @param handlerName name of the handler to remove
     * @return {@code true} if the handler was found and removed, {@code false} otherwise
     */
    public static boolean unregisterHandler(String handlerName) {
        if (AGENT_HANDLERS.remove(handlerName) != null) {
            return true;
        }
        return WORKFLOW_HANDLERS.remove(handlerName) != null;
    }

    /**
     * Remove all registered handlers. Useful for test cleanup.
     */
    public static void clear() {
        AGENT_HANDLERS.clear();
        WORKFLOW_HANDLERS.clear();
    }
}
