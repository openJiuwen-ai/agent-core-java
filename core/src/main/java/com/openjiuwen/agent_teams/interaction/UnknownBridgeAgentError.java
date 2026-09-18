/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

/**
 * Raised when bridge-agent operations target a member name that is not registered as a bridge
 * member.
 *
 * <p>Mirrors Python's {@code UnknownBridgeAgentError} in
 * {@code openjiuwen/agent_teams/interaction/bridge_protocol.py}.</p>
 */
public class UnknownBridgeAgentError extends RuntimeException {

    public UnknownBridgeAgentError() {
        super();
    }

    public UnknownBridgeAgentError(String message) {
        super(message);
    }
}
