/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import java.util.Map;

/**
 * Package bridge for online judge scoring helpers.
 * <p>
 * Mirrors Python's {@code openjiuwen/agent_evolving/agent_rl/online/judge/__init__.py}.
 */
public final class OnlineJudgePackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/agent_rl/online/judge/__init__.py";
    public static final String JUDGE_PROMPT_TEMPLATE = JudgeScoring.JUDGE_PROMPT_TEMPLATE;

    private OnlineJudgePackage() {
    }

    public static String buildJudgePrompt(String instructionText, String responseText, String followupUserFeedback) {
        return JudgeScoring.buildJudgePrompt(instructionText, responseText, followupUserFeedback);
    }

    public static double normalizeOverallScore(double overall) {
        return JudgeScoring.normalizeOverallScore(overall);
    }

    public static Map<String, Object> parseJudgeScores(String content, boolean raiseOnError) {
        return JudgeScoring.parseJudgeScores(content, raiseOnError);
    }
}
