/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import java.util.Map;

/**
 * Minimal delayed-judge scorer seam for gateway trajectory flow.
 * <p>
 * Mirrors Python's {@code JudgeScorer.score} contract in
 * {@code openjiuwen.agent_evolving.agent_rl.online.judge.judge_scorer}.
 */
public interface JudgeScorer {

    Map<String, Object> score(String responseText,
                              String instructionText,
                              String followupUserFeedback,
                              String sessionId,
                              int turnNum);
}
