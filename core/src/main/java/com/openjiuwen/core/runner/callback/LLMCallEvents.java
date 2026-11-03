/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's {@code LLMCallEvents} in
 * {@code openjiuwen/core/runner/callback/events.py}.
 */
public final class LLMCallEvents {
    public static final String LLM_CALL_STARTED = Events.getEvent("llm_call_started");
    public static final String LLM_CALL_ERROR = Events.getEvent("llm_call_error");
    public static final String LLM_RESPONSE_RECEIVED = Events.getEvent("llm_response_received");
    public static final String LLM_INVOKE_INPUT = Events.getEvent("llm_invoke_input");
    public static final String LLM_INVOKE_OUTPUT = Events.getEvent("llm_invoke_output");
    public static final String LLM_STREAM_INPUT = Events.getEvent("llm_stream_input");
    public static final String LLM_STREAM_OUTPUT = Events.getEvent("llm_stream_output");
    public static final String LLM_INPUT = Events.getEvent("llm_input");
    public static final String LLM_OUTPUT = Events.getEvent("llm_output");

    private LLMCallEvents() {
    }
}
