/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

/**
 * Speak directly to the team's leader DeepAgent.
 *
 * <p>Mirrors Python's {@code GodViewMessage} in
 * {@code openjiuwen/agent_teams/interaction/payload.py}.</p>
 */
public record GodViewMessage(String body) implements InteractPayload {
}
