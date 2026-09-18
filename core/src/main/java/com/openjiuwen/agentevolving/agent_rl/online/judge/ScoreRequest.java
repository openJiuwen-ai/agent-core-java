/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.judge;

/**
 * Judge score request payload.
 * <p>
 * Mirrors Python's {@code ScoreRequest} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/judge/judge_server.py}.
 */
public record ScoreRequest(
        String responseText,
        String instructionText,
        String followupUserFeedback,
        String sessionId,
        int turnNum
) {
}
