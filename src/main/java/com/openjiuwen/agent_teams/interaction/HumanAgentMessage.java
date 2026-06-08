/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

/**
 * Speak as a registered human-agent team member.
 *
 * <p>Mirrors Python's {@code HumanAgentMessage} in
 * {@code openjiuwen/agent_teams/interaction/payload.py}.</p>
 */
public record HumanAgentMessage(String body, String sender, String target) implements InteractPayload {

    public HumanAgentMessage(String body, String sender) {
        this(body, sender, null);
    }
}
