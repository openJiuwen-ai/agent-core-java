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
        putIfNotNull(result, "event_type", eventType != null ? eventType.getValue() : null);
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
        if (metadata != null && !metadata.isEmpty()) {
            result.put("metadata", metadata);
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

    /**
     * Auto-generated for codecheck compliance.
     */
    protected static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
