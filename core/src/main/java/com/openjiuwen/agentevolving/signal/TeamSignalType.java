/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.signal;

import com.openjiuwen.agentevolving.Protocols;

/**
 * Team-domain signal types.
 *
 * <p>Mirrors Python's {@code TeamSignalType} in
 * {@code openjiuwen/agent_evolving/signal/team.py}.</p>
 */
public enum TeamSignalType {
    USER_INTENT(Protocols.USER_INTENT_SIGNAL),
    USER_REQUEST("user_request"),
    TRAJECTORY_ISSUE(Protocols.TRAJECTORY_ISSUE_SIGNAL);

    private final String value;

    TeamSignalType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
