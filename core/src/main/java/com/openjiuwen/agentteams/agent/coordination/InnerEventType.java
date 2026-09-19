/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

/**
 * Event types generated inside the coordination layer.
 *
 * <p>Mirrors Python {@code InnerEventType} (str-Enum). These are local to the
 * coordination event bus — never cross-process. The string {@link #getValue()}
 * doubles as the framework registration key.
 *
 * @since 2026/7/9
 */
public enum InnerEventType {
    /** Bootstrap user input enqueued by {@code CoordinationKernel.enqueueUserInput}. */
    USER_INPUT("user_input"),
    /** Periodic mailbox-poll tick from the {@code EventBus}. */
    POLL_MAILBOX("coordination_poll_mailbox"),
    /** Periodic task-board-poll tick from the {@code EventBus}. */
    POLL_TASK("coordination_poll_task"),
    /** Loop shutdown signal enqueued by {@code EventBus.stop()}. */
    SHUTDOWN("shutdown");

    private final String value;

    InnerEventType(String value) {
        this.value = value;
    }

    /**
     * Return the string value used both as event_type and as framework key.
     *
     * @return the string value
     */
    public String getValue() {
        return value;
    }
}
