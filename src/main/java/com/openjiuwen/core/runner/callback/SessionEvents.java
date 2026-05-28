/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Standard event names for session management.
 * 
 * <p>Mirrors Python's {@code SessionEvents} in
 * {@code openjiuwen.core.runner.callback.events}.</p>
 */
public final class SessionEvents {

    /** Session was created */
    public static final String SESSION_CREATED = Events.getEvent("session_created");

    private SessionEvents() {
        // Utility class
    }
}
