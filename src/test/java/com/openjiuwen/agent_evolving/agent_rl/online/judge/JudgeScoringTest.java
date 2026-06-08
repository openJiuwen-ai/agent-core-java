/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeScoringTest {

    @Test
    void scoringBuildsPromptAndNormalizesOverall() {
        String prompt = JudgeScoring.buildJudgePrompt("inst", "resp", "feedback");
        assertTrue(prompt.contains("inst"));
        assertTrue(prompt.contains("resp"));
        assertTrue(prompt.contains("feedback"));
        assertEquals(0.6, JudgeScoring.normalizeOverallScore(8.0));
    }

    @Test
    void parseJudgeScoresFillsOverallFromDimensions() {
        Map<String, Object> parsed = JudgeScoring.parseJudgeScores(
                "{\"task_completion\":8,\"response_quality\":7,\"tool_usage\":9,\"coherence\":8}",
                true
        );
        assertEquals(8.0, ((Number) parsed.get("overall")).doubleValue());
    }
}
