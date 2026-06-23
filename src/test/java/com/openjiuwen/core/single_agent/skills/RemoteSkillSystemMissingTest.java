/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.skills;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestRemoteSkill} in
 * {@code tests/system_tests/agent/skill/test_remote_skill.py}.</p>
 */
class RemoteSkillSystemMissingTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: GitHub remote skill test skipped, set RUN_GITHUB_TEST=1 to enable.";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testFetchSkillFromGithub() {
        // Python skips this network-facing GitHub scenario unless RUN_GITHUB_TEST=1 is set.
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testDownloadSkillFromGithub() {
        // Python skips this network-facing GitHub scenario unless RUN_GITHUB_TEST=1 is set.
    }
}
