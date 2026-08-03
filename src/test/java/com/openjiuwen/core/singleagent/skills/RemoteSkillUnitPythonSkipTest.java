/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestRemoteSkill} in
 * {@code tests/unit_tests/agent/skill/test_skill_remote.py}.</p>
 */
class RemoteSkillUnitPythonSkipTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: GitHub remote skill test skipped, set RUN_GITHUB_TEST=1 to enable.";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testDownloadSkillFromGithub() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testFetchSkillFromGithub() {
    }
}
