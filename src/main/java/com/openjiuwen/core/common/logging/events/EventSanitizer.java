/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.events;

import java.util.*;

/**
 * Utility for sanitizing log events before output.
 * <p>
 * Replaces sensitive fields (e.g. messages, response_content, query) with
 * {@code <REDACTED>} to prevent sensitive data leakage in logs.
 * <p>
 * Java equivalent of Python's {@code sanitize_event_for_logging}.
 */
public final class EventSanitizer {

    /** The placeholder string for redacted fields. */
    public static final String REDACTED = "<REDACTED>";

    /** Default sensitive fields to sanitize. */
    public static final List<String> DEFAULT_SENSITIVE_FIELDS = List.of(
            "messages",
            "response_content",
            "input_content",
            "query",
            "arguments",
            "result",
            "message_content",
            "tool_calls",
            "input_data",
            "output_data",
            "retrieved_memories"
    );

    private EventSanitizer() {
    }

    /**
     * Sanitize event for logging output using default sensitive fields.
     *
     * @param event the event to sanitize
     * @return sanitized event dictionary with sensitive fields replaced
     */
    public static Map<String, Object> sanitizeEventForLogging(BaseLogEvent event) {
        return sanitizeEventForLogging(event, null);
    }

    /**
     * Sanitize event for logging output.
     *
     * @param event           the event to sanitize
     * @param sensitiveFields custom list of sensitive field names; if null, uses defaults
     * @return sanitized event dictionary with sensitive fields replaced
     */
    public static Map<String, Object> sanitizeEventForLogging(BaseLogEvent event,
                                                              List<String> sensitiveFields) {
        List<String> fields = sensitiveFields != null ? sensitiveFields : DEFAULT_SENSITIVE_FIELDS;

        Map<String, Object> eventDict = new LinkedHashMap<>(event.toMap());

        for (String fieldName : fields) {
            if (eventDict.containsKey(fieldName) && eventDict.get(fieldName) != null) {
                eventDict.put(fieldName, REDACTED);
            }
        }

        return eventDict;
    }
}
