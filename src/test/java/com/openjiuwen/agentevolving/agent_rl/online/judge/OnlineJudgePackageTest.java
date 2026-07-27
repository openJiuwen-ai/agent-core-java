/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineJudgePackageTest {

    @Test
    void exportsMatchPythonPackageSurface() {
        assertEquals("openjiuwen/agent_evolving/agent_rl/online/judge/__init__.py", OnlineJudgePackage.PYTHON_MODULE);
        assertEquals(JudgeScoring.JUDGE_PROMPT_TEMPLATE, OnlineJudgePackage.JUDGE_PROMPT_TEMPLATE);
        assertEquals(-1.0, OnlineJudgePackage.normalizeOverallScore(0.0));
        assertTrue(OnlineJudgePackage.buildJudgePrompt("i", "r", "f").contains("i"));
        assertEquals(8.0,
                ((Number) OnlineJudgePackage.parseJudgeScores("{\"overall\":8.0}", true).get("overall")).doubleValue());
    }
}
