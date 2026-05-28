/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.constants;

/**
 * Common enum constants.
 *
 * <p>Mirrors Python's enum definitions in {@code openjiuwen.core.common.constants.enums}.</p>
 */
public class Enums {

    /**
     * Agent execution state.
     */
    public enum AgentState {
        INITIALIZED,
        RUNNING,
        INTERRUPTED,
        COMPLETED,
        FAILED,
        TIMEOUT
    }

    /**
     * Model response type.
     */
    public enum ResponseType {
        ANSWER,
        INTERRUPT,
        ERROR,
        TOOL_CALL
    }

    /**
     * Task priority level.
     */
    public enum TaskPriority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }

    /**
     * Session status.
     */
    public enum SessionStatus {
        CREATED,
        ACTIVE,
        PAUSED,
        CLOSED,
        ERROR
    }

    /**
     * Tool call status.
     */
    public enum ToolCallStatus {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED,
        TIMEOUT
    }

    /**
     * Message role.
     */
    public enum MessageRole {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }
}