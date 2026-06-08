/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's event helpers in
 * {@code openjiuwen/core/runner/callback/events.py}.
 */
public final class Events {

    public static final String DEFAULT_SCOPE = "_framework";

    private Events() {
    }

    public static String buildEventName(String scope, String eventName) {
        return scope + ":" + eventName;
    }

    public static String[] parseEventName(String scopedEvent) {
        if (scopedEvent != null && scopedEvent.contains(":")) {
            return scopedEvent.split(":", 2);
        }
        return new String[]{DEFAULT_SCOPE, scopedEvent};
    }

    public static String getEvent(String eventName) {
        return buildEventName(DEFAULT_SCOPE, eventName);
    }

    public static String getEvent(String scope, String eventName) {
        return buildEventName(scope, eventName);
    }
}
