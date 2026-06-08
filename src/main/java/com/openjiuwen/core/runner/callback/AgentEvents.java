/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's {@code AgentEvents} in
 * {@code openjiuwen/core/runner/callback/events.py}.
 */
public final class AgentEvents {
    public static final String AGENT_STARTED = Events.getEvent("agent_started");
    public static final String AGENT_INVOKE_INPUT = Events.getEvent("agent_invoke_input");
    public static final String AGENT_INVOKE_OUTPUT = Events.getEvent("agent_invoke_output");
    public static final String AGENT_STREAM_INPUT = Events.getEvent("agent_stream_input");
    public static final String AGENT_STREAM_OUTPUT = Events.getEvent("agent_stream_output");

    private AgentEvents() {
    }
}
