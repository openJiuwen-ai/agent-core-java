/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.signal;

/**
 * Parsed user improvement intention.
 *
 * <p>Mirrors Python's {@code UserIntent} in
 * {@code openjiuwen/agent_evolving/signal/team.py}.</p>
 */
public record UserIntent(boolean improvement, String intent) {
    public UserIntent {
        intent = intent != null ? intent : "";
    }

    public boolean isImprovement() {
        return improvement;
    }
}
