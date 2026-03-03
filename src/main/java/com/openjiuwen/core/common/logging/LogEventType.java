// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.logging;

/**
 * 日志事件类型枚举
 *
 * <p>定义系统中所有可能的日志事件类型。</p>
 */
public enum LogEventType {
    // Agent related events
    AGENT_START("agent_start"),
    AGENT_END("agent_end"),
    AGENT_INVOKE("agent_invoke"),
    AGENT_RESPONSE("agent_response"),
    AGENT_ERROR("agent_error"),

    // Workflow related events
    WORKFLOW_EXECUTE_START("workflow_execute_start"),
    WORKFLOW_EXECUTE_END("workflow_execute_end"),
    WORKFLOW_EXECUTE_ERROR("workflow_execute_error"),
    WORKFLOW_OUTPUT_CHUNK("workflow_output_chunk"),
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

    // Store related events
    STORE_ADD("store_add"),
    STORE_DELETE("store_delete"),
    STORE_UPDATE("store_update"),
    STORE_RETRIEVE("store_retrieve"),
    STORE_LOAD("store_load"),

    // Memory related events
    MEMORY_STORE("memory_store"),
    MEMORY_RETRIEVE("memory_retrieve"),
    MEMORY_DELETE("memory_delete"),
    MEMORY_UPDATE("memory_update"),
    MEMORY_PROCESS("memory_process"),

    // Session related events
    SESSION_CREATE("session_create"),
    SESSION_UPDATE("session_update"),
    SESSION_DELETE("session_delete"),

    // Context related events
    CONTEXT_ADD_MESSAGE("context_add_message"),
    CONTEXT_CLEAR("context_clear"),
    CONTEXT_RETRIEVE("context_retrieve"),
    CONTEXT_SAVE("context_save"),

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
    SYSTEM_ERROR("system_error"),

    // SysOperation events
    SYS_OP_START("sys_operation_start"),
    SYS_OP_END("sys_operation_end"),
    SYS_OP_ERROR("sys_operation_error"),
    SYS_OP_STREAM("sys_operation_stream"),

    // Checkpoint related events
    CHECKPOINT_SAVE("checkpoint_save"),
    CHECKPOINT_RESTORE("checkpoint_restore"),
    CHECKPOINT_CLEAR("checkpoint_clear"),
    CHECKPOINT_ERROR("checkpoint_error"),

    // Checkpointer store events
    CHECKPOINTER_STORE_ADD("checkpointer_store_add"),
    CHECKPOINTER_STORE_REMOVE("checkpointer_store_remove"),

    // Graph streaming events
    GRAPH_STREAM_CHUNK("graph_stream_chunk"),
    GRAPH_SEND_STREAM_CHUNK("graph_send_stream_chunk"),
    GRAPH_RECEIVE_STREAM_CHUNK("graph_receive_stream_chunk"),

    // Session streaming events
    SESSION_STREAM_CHUNK("session_stream_chunk"),
    SESSION_STREAM_ERROR("session_stream_error"),

    // Graph vertex execution related events
    GRAPH_VERTEX_INIT("graph_vertex_init"),
    GRAPH_VERTEX_CALL_START("graph_vertex_call_start"),
    GRAPH_VERTEX_CALL_END("graph_vertex_call_end"),
    GRAPH_VERTEX_CALL_ERROR("graph_vertex_call_error"),

    // Graph vertex stream events
    GRAPH_VERTEX_STREAM_ACTOR_START("graph_vertex_stream_actor_start"),
    GRAPH_VERTEX_STREAM_ACTOR_SHUTDOWN("graph_vertex_stream_actor_shutdown"),
    GRAPH_VERTEX_STREAM_CALL_START("graph_vertex_stream_call_start"),
    GRAPH_VERTEX_STREAM_CALL_END("graph_vertex_stream_call_end"),
    GRAPH_VERTEX_STREAM_CALL_ERROR("graph_vertex_stream_call_error"),
    GRAPH_VERTEX_ABILITY_START("graph_vertex_ability_start"),
    GRAPH_VERTEX_ABILITY_RUNNING("graph_vertex_ability_running"),
    GRAPH_VERTEX_ABILITY_END("graph_vertex_ability_end"),
    GRAPH_VERTEX_ABILITY_ERROR("graph_vertex_ability_error"),

    // Graph super step events
    GRAPH_SUPER_STEP_START("graph_super_step_start"),
    GRAPH_SUPER_STEP_END("graph_super_step_end"),
    GRAPH_SUPER_STEP_ERROR("graph_super_step_error"),

    // Graph lifecycle events
    GRAPH_START("graph_start"),
    GRAPH_END("graph_end"),
    GRAPH_ERROR("graph_error"),

    // Graph Store related events
    GRAPH_STORE_SAVE("graph_store_save"),
    GRAPH_STORE_DELETE("graph_store_delete"),
    GRAPH_STORE_GET("graph_store_get"),

    // Runner event
    RUNNER_START("runner_start"),
    RUNNER_STOP("runner_stop"),
    RESOURCE_MGR_ADD_RESOURCE("add_resource"),
    RESOURCE_MGR_REMOVE_RESOURCE("remove_resource"),
    RESOURCE_MGR_GET_RESOURCE("get_resource"),
    RESOURCE_MGR_ADD_RESOURCE_SERVER("add_resource_server"),
    RESOURCE_MGR_REMOVE_RESOURCE_SERVER("remove_resource_server"),
    RESOURCE_MGR_REMOVE_TAG("remove_tag");

    private final String value;

    LogEventType(String value) {
        this.value = value;
    }

    /**
     * 获取事件类型值
     *
     * @return 事件类型字符串
     */
    public String getValue() {
        return value;
    }

    /**
     * 从字符串值创建LogEventType
     *
     * @param value 字符串值
     * @return LogEventType枚举值
     */
    public static LogEventType fromValue(String value) {
        for (LogEventType type : LogEventType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown LogEventType: " + value);
    }
}