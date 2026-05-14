/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

/**
 * Raised when no human-agent member is registered on the team.
 *
 * <p>Mirrors Python's {@code HumanAgentNotEnabledError} in
 * {@code openjiuwen.agent_teams.interaction.human_agent_inbox}.
 */
public class HumanAgentNotEnabledError extends RuntimeException {

    public HumanAgentNotEnabledError(String message) {
        super(message);
    }
}
