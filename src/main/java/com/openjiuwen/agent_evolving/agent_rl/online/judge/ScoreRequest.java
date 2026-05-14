/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

/**
 * Judge score request payload.
 * <p>
 * Mirrors Python's {@code ScoreRequest} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.judge.judge_server}.
 */
public record ScoreRequest(
        String responseText,
        String instructionText,
        String followupUserFeedback,
        String sessionId,
        int turnNum
) {

    public ScoreRequest {
        responseText = responseText != null ? responseText : "";
        instructionText = instructionText != null ? instructionText : "";
        followupUserFeedback = followupUserFeedback != null ? followupUserFeedback : "";
        sessionId = sessionId != null ? sessionId : "";
    }
}
