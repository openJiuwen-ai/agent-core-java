/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway.trajectory;

import java.util.Map;
/**
 * Delayed-judge scorer seam for gateway trajectory flow.
 * <p>
 * Mirrors Python's {@code JudgeScorer.score} contract consumed by
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/trajectory/judge_dispatcher.py}.
 */
public interface JudgeScorer {

    Object score(
            String responseText,
            String instructionText,
            String followupUserFeedback,
            String sessionId,
            int turnNum);
}
