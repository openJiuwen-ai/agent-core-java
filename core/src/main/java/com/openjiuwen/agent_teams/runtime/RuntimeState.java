/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

/**
 * Top-level state of an active team in the runtime pool.
 *
 * <p>Mirrors Python's {@code RuntimeState} in
 * {@code openjiuwen/agent_teams/runtime/pool.py}.</p>
 */
public enum RuntimeState {
    RUNNING("running"),
    PAUSED("paused");

    private final String value;

    RuntimeState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
