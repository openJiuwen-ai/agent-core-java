/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.judge;

import java.util.List;
import java.util.Map;

/**
 * Judge score response payload.
 * <p>
 * Mirrors Python's {@code ScoreResponse} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/judge/judge_server.py}.
 */
public record ScoreResponse(
        double score,
        double overallRaw,
        List<Double> votes,
        Object details,
        String model,
        String sessionId,
        int turnNum
) {

    public Map<String, Object> toMap() {
        return Map.of(
                "score", score,
                "overall_raw", overallRaw,
                "votes", votes,
                "details", details,
                "model", model,
                "session_id", sessionId,
                "turn_num", turnNum
        );
    }
}
