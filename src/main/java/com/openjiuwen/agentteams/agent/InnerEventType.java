/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

/**
 * Event types generated inside the coordination layer.
 * 
 * @since 0.1.7
 */
public enum InnerEventType {
    USER_INPUT("user_input"),
    POLL_MAILBOX("coordination_poll_mailbox"),
    POLL_TASK("coordination_poll_task"),
    SHUTDOWN("shutdown");

    private final String value;

    InnerEventType(String value) {
        this.value = value;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return value;
    }
}
