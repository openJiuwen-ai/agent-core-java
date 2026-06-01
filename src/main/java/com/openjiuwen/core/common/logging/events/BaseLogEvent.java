/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.*;

/**
 * Base log event class — base class for all structured event types.
 * <p>
 * Mirrors Python's {@code BaseLogEvent} in
 * {@code openjiuwen.core.common.logging.events}.
 *
 * <p>
 * Uses Lombok {@code @Data} + {@code @SuperBuilder} for boilerplate reduction.
 * Subclasses should also be annotated with {@code @Data} and {@code @SuperBuilder}.
 */
@Data
@SuperBuilder
public class BaseLogEvent {

    // Basic event information
    @lombok.Builder.Default
    private String eventId = UUID.randomUUID().toString();
    private LogEventType eventType;
    private String eventTypeKey;
    @lombok.Builder.Default
    private LogLevel logLevel = LogLevel.INFO;
    @lombok.Builder.Default
    private Instant timestamp = Instant.now();

    // Module information
    @lombok.Builder.Default
    private ModuleType moduleType = ModuleType.SYSTEM;
    private String moduleId;
    private String moduleName;

    // Context information
    private String sessionId;
    private String conversationId;
    private String traceId;
    private String correlationId;
    private String parentEventId;

    // Status and result
    @lombok.Builder.Default
    private EventStatus status = EventStatus.SUCCESS;
    private String errorCode;
    private String errorMessage;

    // Message and stack trace
    private String message;
    private String stacktrace;
    private String exceptionDetail;

    // Extended fields
    @lombok.Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /** Default no-arg constructor for manual construction. */
    public BaseLogEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.logLevel = LogLevel.INFO;
        this.timestamp = Instant.now();
        this.moduleType = ModuleType.SYSTEM;
        this.status = EventStatus.SUCCESS;
        this.metadata = new LinkedHashMap<>();
    }

    /**
     * Convert to a flat map for serialization / structured logging output.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        putIfNotNull(result, "event_id", eventId);
        putIfNotNull(result, "event_type", eventType != null ? eventType.getValue() : eventTypeKey);
        putIfNotNull(result, "log_level", logLevel != null ? logLevel.getValue() : null);
        putIfNotNull(result, "timestamp", timestamp != null ? timestamp.toString() : null);
        putIfNotNull(result, "module_type", moduleType != null ? moduleType.getValue() : null);
        putIfNotNull(result, "module_id", moduleId);
        putIfNotNull(result, "module_name", moduleName);
        putIfNotNull(result, "session_id", sessionId);
        putIfNotNull(result, "conversation_id", conversationId);
        putIfNotNull(result, "trace_id", traceId);
        putIfNotNull(result, "correlation_id", correlationId);
        putIfNotNull(result, "parent_event_id", parentEventId);
        putIfNotNull(result, "status", status != null ? status.getValue() : null);
        putIfNotNull(result, "error_code", errorCode);
        putIfNotNull(result, "error_message", errorMessage);
        putIfNotNull(result, "message", message);
        putIfNotNull(result, "stacktrace", stacktrace);
        putIfNotNull(result, "exception", exceptionDetail);
        if (metadata != null) {
            result.put("metadata", convertValue(metadata));
        }
        // Subclass-specific fields are added by overriding addFieldsToMap()
        addFieldsToMap(result);
        return result;
    }

    /**
     * Extension point for subclasses to add their own fields to the map.
     * Override this instead of toMap() to keep base fields consistent.
     */
    protected void addFieldsToMap(Map<String, Object> map) {
        // default: nothing extra
    }

    protected static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, convertValue(value));
        }
    }

    @SuppressWarnings("unchecked")
    protected static Object convertValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LogEventType eventTypeValue) {
            return eventTypeValue.getValue();
        }
        if (value instanceof LogLevel logLevelValue) {
            return logLevelValue.getValue();
        }
        if (value instanceof ModuleType moduleTypeValue) {
            return moduleTypeValue.getValue();
        }
        if (value instanceof EventStatus statusValue) {
            return statusValue.getValue();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof Throwable throwable) {
            return throwable.toString();
        }
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> converted = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> {
                if (mapValue != null) {
                    converted.put(String.valueOf(key), convertValue(mapValue));
                }
            });
            return converted;
        }
        if (value instanceof Iterable<?> iterable && !(value instanceof String)) {
            List<Object> converted = new ArrayList<>();
            for (Object item : iterable) {
                converted.add(convertValue(item));
            }
            return converted;
        }
        return value;
    }
}
