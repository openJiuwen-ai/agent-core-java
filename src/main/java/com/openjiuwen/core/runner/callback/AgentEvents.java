/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Standard event names for agent lifecycle events.
 * 
 * <p>Mirrors Python's {@code AgentEvents} in
 * {@code openjiuwen.core.runner.callback.events}.</p>
 */
public final class AgentEvents {

    /** Agent execution started */
    public static final String AGENT_STARTED = Events.getEvent("agent_started");
    
    /** Fired before Agent.invoke with input */
    public static final String AGENT_INVOKE_INPUT = Events.getEvent("agent_invoke_input");
    
    /** Fired after Agent.invoke with output */
    public static final String AGENT_INVOKE_OUTPUT = Events.getEvent("agent_invoke_output");
    
    /** Fired before Agent.stream with input */
    public static final String AGENT_STREAM_INPUT = Events.getEvent("agent_stream_input");
    
    /** Fired for each item yielded by Agent.stream */
    public static final String AGENT_STREAM_OUTPUT = Events.getEvent("agent_stream_output");

    private AgentEvents() {
        // Utility class
    }
}
