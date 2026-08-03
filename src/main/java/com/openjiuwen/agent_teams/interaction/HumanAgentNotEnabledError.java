/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

/**
 * Raised when no human-agent member is registered for the team.
 *
 * <p>Mirrors Python's {@code HumanAgentNotEnabledError} in
 * {@code openjiuwen/agent_teams/interaction/human_agent_inbox.py}.</p>
 */
public class HumanAgentNotEnabledError extends RuntimeException {

    public HumanAgentNotEnabledError(String message) {
        super(message);
    }
}
