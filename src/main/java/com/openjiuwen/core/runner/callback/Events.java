/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Event name utilities and constants for the callback framework.
 * 
 * <p>Mirrors Python's {@code events} in
 * {@code openjiuwen.core.runner.callback.events}.</p>
 * 
 * <p>Events support scope isolation using colon(:) as separator, e.g., "scope:event_name".
 * System-level events use "_framework" as default scope.</p>
 */
public final class Events {

    /** Default system scope */
    public static final String DEFAULT_SCOPE = "_framework";

    private Events() {
        // Utility class
    }

    /**
     * Build a scoped event name.
     *
     * @param scope     The scope for the event
     * @param eventName The event name
     * @return Scoped event name in format "scope:event_name"
     */
    public static String buildEventName(String scope, String eventName) {
        return scope + ":" + eventName;
    }

    /**
     * Parse a scoped event name into scope and event name.
     *
     * @param scopedEvent Scoped event name in format "scope:event_name"
     * @return Array of [scope, event_name]. If no scope is specified, returns [DEFAULT_SCOPE, event_name]
     */
    public static String[] parseEventName(String scopedEvent) {
        if (scopedEvent == null) {
            return new String[]{DEFAULT_SCOPE, ""};
        }
        int colonIndex = scopedEvent.indexOf(':');
        if (colonIndex >= 0) {
            return new String[]{
                    scopedEvent.substring(0, colonIndex),
                    scopedEvent.substring(colonIndex + 1)
            };
        }
        return new String[]{DEFAULT_SCOPE, scopedEvent};
    }

    /**
     * Get event name with default scope.
     *
     * @param eventName The raw event name
     * @return Full scoped event name with default scope
     */
    public static String getEvent(String eventName) {
        return buildEventName(DEFAULT_SCOPE, eventName);
    }

    /**
     * Get event name with specified scope.
     *
     * @param scope     The scope for the event
     * @param eventName The raw event name
     * @return Full scoped event name
     */
    public static String getEvent(String scope, String eventName) {
        return buildEventName(scope, eventName);
    }
}
