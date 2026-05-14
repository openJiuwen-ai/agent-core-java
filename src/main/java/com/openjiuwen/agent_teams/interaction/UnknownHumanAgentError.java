/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

/**
 * Raised when a chosen human-agent sender is not registered.
 *
 * <p>Mirrors Python's {@code UnknownHumanAgentError} in
 * {@code openjiuwen.agent_teams.interaction.human_agent_inbox}.
 */
public class UnknownHumanAgentError extends RuntimeException {

    public UnknownHumanAgentError(String message) {
        super(message);
    }
}
