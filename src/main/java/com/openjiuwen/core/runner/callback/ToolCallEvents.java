/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Standard event names for tool call operations.
 * 
 * <p>Mirrors Python's {@code ToolCallEvents} in
 * {@code openjiuwen.core.runner.callback.events}.</p>
 */
public final class ToolCallEvents {

    /** Tool call initiated */
    public static final String TOOL_CALL_STARTED = Events.getEvent("tool_call_started");
    
    /** Tool call completed successfully */
    public static final String TOOL_CALL_FINISHED = Events.getEvent("tool_call_finished");
    
    /** Tool call failed with an error */
    public static final String TOOL_CALL_ERROR = Events.getEvent("tool_call_error");
    
    /** Tool result received */
    public static final String TOOL_RESULT_RECEIVED = Events.getEvent("tool_result_received");
    
    /** Tool result parsing started */
    public static final String TOOL_PARSE_STARTED = Events.getEvent("tool_parse_started");
    
    /** Tool result parsing completed */
    public static final String TOOL_PARSE_FINISHED = Events.getEvent("tool_parse_finished");
    
    /** Fired before Tool.invoke with call arguments */
    public static final String TOOL_INVOKE_INPUT = Events.getEvent("tool_invoke_input");
    
    /** Fired after Tool.invoke with the result */
    public static final String TOOL_INVOKE_OUTPUT = Events.getEvent("tool_invoke_output");
    
    /** Fired before Tool.stream with call arguments */
    public static final String TOOL_STREAM_INPUT = Events.getEvent("tool_stream_input");
    
    /** Fired for each item yielded by Tool.stream */
    public static final String TOOL_STREAM_OUTPUT = Events.getEvent("tool_stream_output");
    
    /** Tool authentication event for configuring authentication */
    public static final String TOOL_AUTH = Events.getEvent("tool_auth");

    private ToolCallEvents() {
        // Utility class
    }
}
