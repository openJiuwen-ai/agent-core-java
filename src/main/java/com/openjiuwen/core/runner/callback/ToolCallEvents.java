/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's {@code ToolCallEvents} in
 * {@code openjiuwen/core/runner/callback/events.py}.
 */
public final class ToolCallEvents {
    public static final String TOOL_CALL_STARTED = Events.getEvent("tool_call_started");
    public static final String TOOL_CALL_FINISHED = Events.getEvent("tool_call_finished");
    public static final String TOOL_CALL_ERROR = Events.getEvent("tool_call_error");
    public static final String TOOL_RESULT_RECEIVED = Events.getEvent("tool_result_received");
    public static final String TOOL_PARSE_STARTED = Events.getEvent("tool_parse_started");
    public static final String TOOL_PARSE_FINISHED = Events.getEvent("tool_parse_finished");
    public static final String TOOL_INVOKE_INPUT = Events.getEvent("tool_invoke_input");
    public static final String TOOL_INVOKE_OUTPUT = Events.getEvent("tool_invoke_output");
    public static final String TOOL_STREAM_INPUT = Events.getEvent("tool_stream_input");
    public static final String TOOL_STREAM_OUTPUT = Events.getEvent("tool_stream_output");
    public static final String TOOL_AUTH = Events.getEvent("tool_auth");

    private ToolCallEvents() {
    }
}
