/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

/**
 * Execution mode for agent invoke calls.
 *
 * <p>Mirrors Python's {@code RunKind} in
 * {@code openjiuwen.core.single_agent.rail.base}.</p>
 */
public enum RunKind {
    NORMAL("normal"),
    HEARTBEAT("heartbeat");

    private final String value;

    RunKind(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
