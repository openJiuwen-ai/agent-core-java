/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

/**
 * Raised when bridge-agent operations are attempted while the team-level bridge capability is
 * disabled.
 *
 * <p>Mirrors Python's {@code BridgeAgentNotEnabledError} in
 * {@code openjiuwen/agent_teams/interaction/bridge_protocol.py}.</p>
 */
public class BridgeAgentNotEnabledError extends RuntimeException {

    public BridgeAgentNotEnabledError() {
        super();
    }

    public BridgeAgentNotEnabledError(String message) {
        super(message);
    }
}
