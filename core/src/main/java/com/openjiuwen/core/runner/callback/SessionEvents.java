/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's {@code SessionEvents} in
 * {@code openjiuwen/core/runner/callback/events.py}.
 */
public final class SessionEvents {
    public static final String SESSION_CREATED = Events.getEvent("session_created");
    public static final String AGENT_SESSION_CREATED = Events.getEvent("agent_session_created");

    private SessionEvents() {
    }
}
