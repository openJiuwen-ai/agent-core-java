/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

/**
 * Speak as the external user.
 *
 * <p>Mirrors Python's {@code OperatorMessage} in
 * {@code openjiuwen/agent_teams/interaction/payload.py}.</p>
 */
public record OperatorMessage(String body, String target) implements InteractPayload {

    public OperatorMessage(String body) {
        this(body, null);
    }
}
