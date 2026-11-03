/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.lang.reflect.Method;

/**
 * Event class registry 鈥?maps {@link LogEventType} to concrete event class constructors.
 * <p>
 * Mirrors Python's event helpers in {@code openjiuwen/core/common/logging/events.py}.
 *
 * <p>
 * Contains both the static (built-in) mapping and a dynamic registry for custom event types.
 */
public final class EventClassRegistry {

    private EventClassRegistry() {
    }

    // ==================== Static Mapping ====================

    private static final Map<LogEventType, Supplier<? extends BaseLogEvent>> STATIC_MAP;

    static {
        Map<LogEventType, Supplier<? extends BaseLogEvent>> m = new EnumMap<>(LogEventType.class);

        // Agent events
        for (LogEventType t : List.of(
            LogEventType.AGENT_START, LogEventType.AGENT_END, LogEventType.AGENT_INVOKE,
            LogEventType.AGENT_RESPONSE, LogEventType.AGENT_ERROR)) {
            m.put(t, AgentEvent::new);
        }

        // Workflow events
        for (LogEventType t : List.of(
            LogEventType.WORKFLOW_EXECUTE_START, LogEventType.WORKFLOW_EXECUTE_END,
            LogEventType.WORKFLOW_EXECUTE_ERROR, LogEventType.WORKFLOW_OUTPUT_CHUNK,
            LogEventType.WORKFLOW_COMPONENT_START, LogEventType.WORKFLOW_COMPONENT_END,
            LogEventType.WORKFLOW_COMPONENT_ERROR, LogEventType.WORKFLOW_BRANCH)) {
            m.put(t, WorkflowEvent::new);
        }

        // LLM events
        for (LogEventType t : List.of(
            LogEventType.LLM_CALL_START, LogEventType.LLM_CALL_END,
            LogEventType.LLM_CALL_ERROR, LogEventType.LLM_STREAM_CHUNK)) {
            m.put(t, LLMEvent::new);
        }

        // Tool events
        for (LogEventType t : List.of(
            LogEventType.TOOL_CALL_START, LogEventType.TOOL_CALL_END, LogEventType.TOOL_CALL_ERROR)) {
            m.put(t, ToolEvent::new);
        }

        // Store events
        for (LogEventType t : List.of(
            LogEventType.STORE_ADD, LogEventType.STORE_DELETE, LogEventType.STORE_UPDATE,
            LogEventType.STORE_RETRIEVE, LogEventType.STORE_LOAD)) {
            m.put(t, StoreEvent::new);
        }

        // Memory events
        for (LogEventType t : List.of(
            LogEventType.MEMORY_INIT, LogEventType.MEMORY_PROCESS, LogEventType.MEMORY_STORE,
            LogEventType.MEMORY_RETRIEVE, LogEventType.MEMORY_DELETE, LogEventType.MEMORY_UPDATE)) {
            m.put(t, MemoryEvent::new);
        }

        // Session events
        for (LogEventType t : List.of(
            LogEventType.SESSION_CREATE, LogEventType.SESSION_UPDATE, LogEventType.SESSION_DELETE,
            LogEventType.SESSION_STREAM_CHUNK, LogEventType.SESSION_STREAM_ERROR,
            LogEventType.CHECKPOINT_SAVE, LogEventType.CHECKPOINT_RESTORE,
            LogEventType.CHECKPOINT_CLEAR, LogEventType.CHECKPOINT_ERROR,
            LogEventType.CHECKPOINTER_STORE_ADD, LogEventType.CHECKPOINTER_STORE_REMOVE)) {
            m.put(t, SessionEvent::new);
        }

        // Context events
        for (LogEventType t : List.of(
            LogEventType.CONTEXT_ADD_MESSAGE, LogEventType.CONTEXT_CLEAR, LogEventType.CONTEXT_RETRIEVE)) {
            m.put(t, ContextEvent::new);
        }

        // Retrieval events
        for (LogEventType t : List.of(
            LogEventType.RETRIEVAL_START, LogEventType.RETRIEVAL_END, LogEventType.RETRIEVAL_ERROR)) {
            m.put(t, RetrievalEvent::new);
        }

        // Performance events
        m.put(LogEventType.PERFORMANCE_METRIC, PerformanceEvent::new);

        // User interaction events
        m.put(LogEventType.USER_INPUT, UserInteractionEvent::new);
        m.put(LogEventType.USER_FEEDBACK, UserInteractionEvent::new);

        // System events
        for (LogEventType t : List.of(
            LogEventType.SYSTEM_START, LogEventType.SYSTEM_SHUTDOWN, LogEventType.SYSTEM_ERROR)) {
            m.put(t, SystemEvent::new);
        }

        // SysOperation events
        for (LogEventType t : List.of(
            LogEventType.SYS_OP_START, LogEventType.SYS_OP_END,
            LogEventType.SYS_OP_ERROR, LogEventType.SYS_OP_STREAM)) {
            m.put(t, SysOperationEvent::new);
        }

        // Graph events
        for (LogEventType t : List.of(
            LogEventType.GRAPH_SEND_STREAM_CHUNK, LogEventType.GRAPH_RECEIVE_STREAM_CHUNK,
            LogEventType.GRAPH_VERTEX_INIT, LogEventType.GRAPH_VERTEX_CALL_START,
            LogEventType.GRAPH_VERTEX_CALL_END, LogEventType.GRAPH_VERTEX_CALL_ERROR,
            LogEventType.GRAPH_VERTEX_STREAM_ACTOR_START, LogEventType.GRAPH_VERTEX_STREAM_ACTOR_SHUTDOWN,
            LogEventType.GRAPH_VERTEX_STREAM_CALL_START, LogEventType.GRAPH_VERTEX_STREAM_CALL_END,
            LogEventType.GRAPH_VERTEX_STREAM_CALL_ERROR,
            LogEventType.GRAPH_VERTEX_ABILITY_START, LogEventType.GRAPH_VERTEX_ABILITY_RUNNING,
            LogEventType.GRAPH_VERTEX_ABILITY_END, LogEventType.GRAPH_VERTEX_ABILITY_ERROR,
            LogEventType.GRAPH_SUPER_STEP_START, LogEventType.GRAPH_SUPER_STEP_END,
            LogEventType.GRAPH_SUPER_STEP_ERROR,
            LogEventType.GRAPH_START, LogEventType.GRAPH_END, LogEventType.GRAPH_ERROR,
            LogEventType.GRAPH_STORE_SAVE, LogEventType.GRAPH_STORE_DELETE, LogEventType.GRAPH_STORE_GET)) {
            m.put(t, GraphEvent::new);
        }

        // Runner events
        for (LogEventType t : List.of(
            LogEventType.RUNNER_START, LogEventType.RUNNER_STOP,
            LogEventType.RESOURCE_MGR_ADD_RESOURCE, LogEventType.RESOURCE_MGR_REMOVE_RESOURCE,
            LogEventType.RESOURCE_MGR_GET_RESOURCE, LogEventType.RESOURCE_MGR_ADD_RESOURCE_SERVER,
            LogEventType.RESOURCE_MGR_REMOVE_RESOURCE_SERVER, LogEventType.RESOURCE_MGR_REMOVE_TAG)) {
            m.put(t, RunnerEvent::new);
        }

        STATIC_MAP = Collections.unmodifiableMap(m);
    }

    // ==================== Dynamic Registry ====================

    private static final Map<String, Supplier<? extends BaseLogEvent>> CUSTOM_MAP = new ConcurrentHashMap<>();

    /**
     * Register a custom event class for a string event type.
     *
     * @throws IllegalArgumentException if the key conflicts with a built-in LogEventType value
     */
    public static void register(String eventTypeKey, Supplier<? extends BaseLogEvent> factory) {
        // Ensure no conflict with built-in enum values
        for (LogEventType t : LogEventType.values()) {
            if (t.getValue().equals(eventTypeKey)) {
                throw new IllegalArgumentException(
                    "Event type '" + eventTypeKey + "' conflicts with predefined enum value.");
            }
        }
        CUSTOM_MAP.put(eventTypeKey, factory);
    }

    /** Unregister a custom event class. */
    public static boolean unregister(String eventTypeKey) {
        return CUSTOM_MAP.remove(eventTypeKey) != null;
    }

    /**
     * Get event factory for a given event type (enum or string).
     * Priority: custom registry 鈫?static map 鈫?BaseLogEvent.
     */
    public static Supplier<? extends BaseLogEvent> getFactory(LogEventType eventType) {
        // Check custom first (by string value)
        Supplier<? extends BaseLogEvent> custom = CUSTOM_MAP.get(eventType.getValue());
        if (custom != null) {
            return custom;
        }
        return STATIC_MAP.getOrDefault(eventType, BaseLogEvent::new);
    }

    /** Get event factory by string key. */
    public static Supplier<? extends BaseLogEvent> getFactory(String eventTypeKey) {
        Supplier<? extends BaseLogEvent> custom = CUSTOM_MAP.get(eventTypeKey);
        if (custom != null) {
            return custom;
        }
        // Try to match against LogEventType valueOf
        LogEventType enumType = LogEventType.fromValue(eventTypeKey);
        if (enumType != null) {
            return STATIC_MAP.getOrDefault(enumType, BaseLogEvent::new);
        }
        return BaseLogEvent::new;
    }

    /**
     * Create a log event of the appropriate type for the given event type.
     */
    public static BaseLogEvent createEvent(LogEventType eventType) {
        BaseLogEvent event = getFactory(eventType).get();
        event.setEventType(eventType);
        return event;
    }

    /**
     * Create a log event of the appropriate type for the given string event type.
     *
     * @param eventTypeKey string event type key
     * @return a new event instance with the event type set
     */
    public static BaseLogEvent createEvent(String eventTypeKey) {
        LogEventType enumType = LogEventType.fromValue(eventTypeKey);
        if (enumType != null) {
            return createEvent(enumType);
        }
        BaseLogEvent event = getFactory(eventTypeKey).get();
        event.setEventTypeKey(eventTypeKey);
        return event;
    }

    /**
     * Create a log event and populate it with the given properties via setter methods.
     * <p>
     * Smart detection: if the resolved event class is {@link StreamEvent} and the properties
     * contain workflow indicators (workflowId, componentId, componentTypeStr), a
     * {@link WorkflowStreamEvent} is created instead.
     * <p>
     * Unknown property keys are silently ignored (with a warning log).
     *
     * @param eventType  the event type
     * @param properties field values to set on the event (camelCase keys)
     * @return the populated event
     */
    public static BaseLogEvent createEvent(LogEventType eventType, Map<String, Object> properties) {
        Supplier<? extends BaseLogEvent> factory = getFactory(eventType);
        BaseLogEvent probe = factory.get();

        // Smart detection: StreamEvent 鈫?WorkflowStreamEvent if workflow fields present
        if (probe instanceof StreamEvent && !(probe instanceof WorkflowStreamEvent) && properties != null) {
            Set<String> workflowIndicators = Set.of("workflowId", "componentId", "componentTypeStr");
            if (properties.keySet().stream().anyMatch(workflowIndicators::contains)) {
                probe = new WorkflowStreamEvent();
            }
        }

        probe.setEventType(eventType);

        if (properties != null && !properties.isEmpty()) {
            populateFields(probe, properties);
        }

        return probe;
    }

    /**
     * Create a log event from a string key and populate it with properties.
     */
    public static BaseLogEvent createEvent(String eventTypeKey, Map<String, Object> properties) {
        LogEventType enumType = LogEventType.fromValue(eventTypeKey);
        if (enumType != null) {
            return createEvent(enumType, properties);
        }
        BaseLogEvent event = getFactory(eventTypeKey).get();
        event.setEventTypeKey(eventTypeKey);
        if (properties != null && !properties.isEmpty()) {
            populateFields(event, properties);
        }
        return event;
    }

    /**
     * Validate an event object's validity.
     * <p>
     * Checks:
     * <ul>
     *   <li>eventId is not null/empty</li>
     *   <li>eventType is not null</li>
     *   <li>logLevel is not null</li>
     *   <li>moduleType is not null</li>
     * </ul>
     *
     * @param event the event to validate
     * @return true if the event is valid
     */
    public static boolean validateEvent(BaseLogEvent event) {
        if (event == null) {
            return false;
        }
        if (event.getEventId() == null || event.getEventId().isEmpty()) {
            return false;
        }
        if (event.getEventType() == null
            && (event.getEventTypeKey() == null || event.getEventTypeKey().isBlank())) {
            return false;
        }
        if (event.getLogLevel() == null) {
            return false;
        }
        if (event.getModuleType() == null) {
            return false;
        }
        return true;
    }

    // ==================== Field population helper ====================

    private static final Logger LOG = Logger.getLogger(EventClassRegistry.class.getName());

    /**
     * Populate fields on an event via reflection-based setter lookup.
     */
    private static void populateFields(BaseLogEvent event, Map<String, Object> properties) {
        Class<?> clazz = event.getClass();
        List<String> ignored = new ArrayList<>();

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String propertyName = normalizePropertyName(key);
            if (propertyName.isEmpty()) {
                ignored.add(key);
                continue;
            }
            String setterName = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);

            boolean found = false;
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                    try {
                        m.invoke(event, value);
                        found = true;
                    } catch (Exception e) {
                        // type mismatch or access error 鈥?treat as ignored
                        ignored.add(key);
                    }
                    break;
                }
            }
            if (!found) {
                ignored.add(key);
            }
        }

        if (!ignored.isEmpty()) {
            LOG.warning("Ignoring undefined fields for " + event.getClass().getSimpleName()
                    + ": " + String.join(", ", ignored));
        }
    }

    /**
     * Compatibility hook for Python's reset_common_logger_cache().
     */
    public static void resetCommonLoggerCache() {
        // Java resolves the common logger lazily through LogManager, so no extra cache is kept here.
    }

    private static String normalizePropertyName(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            if (upperNext) {
                builder.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}


