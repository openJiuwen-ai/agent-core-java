/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Standard event names for LLM call operations.
 * 
 * <p>Mirrors Python's {@code LLMCallEvents} in
 * {@code openjiuwen.core.runner.callback.events}.</p>
 */
public final class LLMCallEvents {

    /** LLM call initiated */
    public static final String LLM_CALL_STARTED = Events.getEvent("llm_call_started");
    
    /** LLM call failed with an error */
    public static final String LLM_CALL_ERROR = Events.getEvent("llm_call_error");
    
    /** LLM response received (streaming) */
    public static final String LLM_RESPONSE_RECEIVED = Events.getEvent("llm_response_received");
    
    /** Fired before BaseModelClient.invoke with call arguments */
    public static final String LLM_INVOKE_INPUT = Events.getEvent("llm_invoke_input");
    
    /** Fired after BaseModelClient.invoke with the result */
    public static final String LLM_INVOKE_OUTPUT = Events.getEvent("llm_invoke_output");
    
    /** Fired before BaseModelClient.stream with call arguments */
    public static final String LLM_STREAM_INPUT = Events.getEvent("llm_stream_input");
    
    /** Fired for each item yielded by BaseModelClient.stream */
    public static final String LLM_STREAM_OUTPUT = Events.getEvent("llm_stream_output");
    
    /** Fired before LLM request with messages/tools input data */
    public static final String LLM_INPUT = Events.getEvent("llm_input");
    
    /** Fired after LLM response with response/usage output data */
    public static final String LLM_OUTPUT = Events.getEvent("llm_output");

    private LLMCallEvents() {
        // Utility class
    }
}