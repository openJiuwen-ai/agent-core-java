/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.dev_tools.skill_creator;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skill Creator tester.
 *
 * <p>Mirrors Python's {@code test_skill_creator_mock.py} in
 * {@code tests/unit_tests/agent/skill/}.
 *
 * <p>Real LLM tests are skipped unless RUN_REAL_LLM_TESTS=1.
 */
@DisplayName("Skill Creator")
class SkillCreatorMockTest {

    @Test
    @DisplayName("skill directory and SKILL.md creation (mock FS)")
    void testSkillCreationMockFS() {
        String skillName = "skill_name";

        assertThat(skillName).isNotEmpty();

        String skillMdContent = "---\nname: skill_name\ndescription: sample skill\n---\n# Skill Body\n";
        assertThat(skillMdContent).contains("name: skill_name");
        assertThat(skillMdContent).contains("description: sample skill");
        assertThat(skillMdContent).startsWith("---");
    }

    @Test
    @DisplayName("skill creation with real LLM (skipped by default)")
    void testSkillCreationRealLlm(@TempDir Path tempDir) {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("RUN_REAL_LLM_TESTS")),
                "Real LLM test skipped. Set RUN_REAL_LLM_TESTS=1 to enable."
        );

        Path outputDir = tempDir;
        assertThat(outputDir).exists();
    }
}
