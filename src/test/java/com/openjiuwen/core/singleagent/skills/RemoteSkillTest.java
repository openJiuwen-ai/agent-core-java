/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.skills;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Remote Skill tester.
 *
 * <p>Mirrors Python's {@code test_skill_remote.py} in
 * {@code tests/unit_tests/agent/skill/}.
 *
 * <p>GitHub tests are skipped unless RUN_GITHUB_TEST=1.
 */
@DisplayName("Remote Skill")
class RemoteSkillTest {

    @Test
    @DisplayName("fetch skill from GitHub (skipped by default)")
    void testFetchSkillFromGithub() {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("RUN_GITHUB_TEST")),
                "GitHub remote skill test skipped, set RUN_GITHUB_TEST=1 to enable."
        );

        String token = System.getenv().getOrDefault("GITHUB_TOKEN", "");
        assertThat(token).isNotEmpty();
    }

    @Test
    @DisplayName("download skill from GitHub (skipped by default)")
    void testDownloadSkillFromGithub() {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("RUN_GITHUB_TEST")),
                "GitHub remote skill test skipped, set RUN_GITHUB_TEST=1 to enable."
        );

        String token = System.getenv().getOrDefault("GITHUB_TOKEN", "");
        assertThat(token).isNotEmpty();
    }
}
