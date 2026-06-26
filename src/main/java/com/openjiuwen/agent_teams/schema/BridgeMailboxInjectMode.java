/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mailbox wrapping mode used before forwarding bridge-agent messages.
 *
 * <p>Mirrors Python's {@code BridgeMailboxInjectMode} in
 * {@code openjiuwen/agent_teams/schema/team.py}.</p>
 */
public enum BridgeMailboxInjectMode {
    PASSTHROUGH("passthrough"),
    REPHRASE("rephrase");

    private final String value;

    BridgeMailboxInjectMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static BridgeMailboxInjectMode fromValue(String value) {
        for (BridgeMailboxInjectMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown bridge mailbox inject mode: " + value);
    }
}
