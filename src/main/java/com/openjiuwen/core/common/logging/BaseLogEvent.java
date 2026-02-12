// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.logging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base log event interface
 * 
 * <p>All log event classes should implement this interface.
 * 
 * <p>Corresponds to Python: events.py::BaseLogEvent dataclass
 * 
 * @since 0.1.4
 */
public interface BaseLogEvent {
    
    // ========== Basic event information ==========
    
    /**
     * Get event ID
     * 
     * @return the event ID
     */
    String getEventId();
    
    /**
     * Get event type
     * 
     * @return the event type
     */
    LogEventType getEventType();
    
    /**
     * Get log level
     * 
     * @return the log level
     */
    LogLevel getLogLevel();
    
    /**
     * Get timestamp
     * 
     * @return the timestamp
     */
    Instant getTimestamp();
    
    // ========== Module information ==========
    
    /**
     * Get module type
     * 
     * @return the module type
     */
    ModuleType getModuleType();
    
    /**
     * Get module ID (e.g., Agent ID, Workflow ID, Tool Name)
     * 
     * @return the module ID, or null if not set
     */
    String getModuleId();
    
    /**
     * Get module name
     * 
     * @return the module name, or null if not set
     */
    String getModuleName();
    
    // ========== Context information ==========
    
    /**
     * Get session ID
     * 
     * @return the session ID
     */
    String getSessionId();
    
    /**
     * Get conversation ID
     * 
     * @return the conversation ID, or null if not set
     */
    String getConversationId();
    
    /**
     * Get trace ID
     * 
     * @return the trace ID, or null if not set
     */
    String getTraceId();
    
    /**
     * Get correlation ID for associating related events
     * 
     * @return the correlation ID, or null if not set
     */
    String getCorrelationId();
    
    /**
     * Get parent event ID for building event tree
     * 
     * @return the parent event ID, or null if not set
     */
    String getParentEventId();
    
    // ========== Status and result ==========
    
    /**
     * Get event status
     * 
     * @return the event status
     */
    EventStatus getStatus();
    
    /**
     * Get error code
     * 
     * @return the error code, or null if not set
     */
    String getErrorCode();
    
    /**
     * Get error message
     * 
     * @return the error message, or null if not set
     */
    String getErrorMessage();
    
    // ========== Message and stack trace ==========
    
    /**
     * Get log message content
     * 
     * @return the message, or null if not set
     */
    String getMessage();
    
    /**
     * Get stack trace information (for exceptions)
     * 
     * @return the stack trace, or null if not set
     */
    String getStacktrace();
    
    /**
     * Get exception detail string
     * 
     * @return the exception detail, or null if not set
     */
    String getException();
    
    // ========== Extended fields ==========
    
    /**
     * Get metadata (extended fields)
     * 
     * @return the metadata map
     */
    Map<String, Object> getMetadata();
    
    // ========== Serialization ==========
    
    /**
     * Convert event to Map for serialization
     * 
     * @return the map representation
     */
    Map<String, Object> toMap();
    
    // ========== Utility methods for recursive conversion ==========
    
    /**
     * Recursively convert Enum and Instant values in a Map
     * 
     * <p>Corresponds to Python: BaseLogEvent._convert_dict()
     * Handles nested Maps, Lists containing Enum/Instant/Map values.
     * 
     * @param map the map to convert
     * @return a new map with converted values
     */
    static Map<String, Object> convertMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), convertSingleValue(entry.getValue()));
        }
        return result;
    }
    
    /**
     * Convert a single value, handling Enum, Instant, Map, and List types recursively
     * 
     * @param value the value to convert
     * @return the converted value
     */
    static Object convertSingleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Enum<?> enumValue) {
            // Try to call getValue() if available, otherwise use name()
            try {
                return enumValue.getClass().getMethod("getValue").invoke(enumValue);
            } catch (Exception e) {
                return enumValue.name();
            }
        }
        if (value instanceof Instant instantValue) {
            return instantValue.toString();
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> converted = new HashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), convertSingleValue(entry.getValue()));
            }
            return converted;
        }
        if (value instanceof List<?> listValue) {
            List<Object> convertedList = new ArrayList<>();
            for (Object item : listValue) {
                convertedList.add(convertSingleValue(item));
            }
            return convertedList;
        }
        return value;
    }
}

