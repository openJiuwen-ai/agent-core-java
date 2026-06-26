/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

/**
 * Raised when the selected sender is not a registered human-agent member.
 *
 * <p>Mirrors Python's {@code UnknownHumanAgentError} in
 * {@code openjiuwen/agent_teams/interaction/human_agent_inbox.py}.</p>
 */
public class UnknownHumanAgentError extends RuntimeException {

    public UnknownHumanAgentError(String message) {
        super(message);
    }
}
