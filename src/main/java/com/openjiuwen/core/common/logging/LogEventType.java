// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.logging;

/**
 * Log event type enumeration
 * 
 * @since 0.1.4
 */
public enum LogEventType {
    
    // Agent related events
    AGENT_START("agent_start"),
    AGENT_END("agent_end"),
    AGENT_INVOKE("agent_invoke"),
    AGENT_RESPONSE("agent_response"),
    AGENT_ERROR("agent_error"),
    
    // Workflow related events
    WORKFLOW_START("workflow_start"),
    WORKFLOW_END("workflow_end"),
    WORKFLOW_COMPONENT_START("workflow_component_start"),
    WORKFLOW_COMPONENT_END("workflow_component_end"),
    WORKFLOW_COMPONENT_ERROR("workflow_component_error"),
    WORKFLOW_BRANCH("workflow_branch"),
    
    // LLM related events
    LLM_CALL_START("llm_call_start"),
    LLM_CALL_END("llm_call_end"),
    LLM_CALL_ERROR("llm_call_error"),
    LLM_STREAM_CHUNK("llm_stream_chunk"),
    
    // Tool related events
    TOOL_CALL_START("tool_call_start"),
    TOOL_CALL_END("tool_call_end"),
    TOOL_CALL_ERROR("tool_call_error"),
    
    // Memory related events
    MEMORY_STORE("memory_store"),
    MEMORY_RETRIEVE("memory_retrieve"),
    MEMORY_DELETE("memory_delete"),
    MEMORY_UPDATE("memory_update"),
    
    // Session related events
    SESSION_CREATE("session_create"),
    SESSION_UPDATE("session_update"),
    SESSION_DELETE("session_delete"),
    
    // Context related events
    CONTEXT_ADD_MESSAGE("context_add_message"),
    CONTEXT_CLEAR("context_clear"),
    CONTEXT_RETRIEVE("context_retrieve"),
    
    // Retrieval related events
    RETRIEVAL_START("retrieval_start"),
    RETRIEVAL_END("retrieval_end"),
    RETRIEVAL_ERROR("retrieval_error"),
    
    // Performance related events
    PERFORMANCE_METRIC("performance_metric"),
    
    // User interaction events
    USER_INPUT("user_input"),
    USER_FEEDBACK("user_feedback"),
    
    // System events
    SYSTEM_START("system_start"),
    SYSTEM_SHUTDOWN("system_shutdown"),
    SYSTEM_ERROR("system_error");
    
    private final String value;
    
    LogEventType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Get event type from string value
     * 
     * @param value the string value
     * @return the event type
     */
    public static LogEventType fromValue(String value) {
        for (LogEventType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown event type: " + value);
    }
}

