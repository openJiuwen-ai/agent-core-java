/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.skills;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Remote Skill tester.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent/skill/test_skill_remote.py}.
 *
 * <p>GitHub tests are skipped unless RUN_GITHUB_TEST=1.
 */
@DisplayName("Remote Skill")
class RemoteSkillTest {

    @Test
    @DisplayName("test_fetch_skill_from_github")
    void testFetchSkillFromGithub() {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("RUN_GITHUB_TEST")),
                "GitHub remote skill test skipped, set RUN_GITHUB_TEST=1 to enable."
        );

        RemoteSkillUtil remoteSkillUtil = new RemoteSkillUtil("github-skill-id");
        RemoteSkillUtil.SearchResult result = remoteSkillUtil.searchGitHubForSkills(githubTree(), githubToken());
        List<String> filePaths = result.files().stream()
                .map(RemoteSkillUtil.SkillFile::path)
                .toList();

        assertThat(result.files()).hasSize(2);
        assertThat(filePaths)
                .contains("skills/example-skill/SKILL.md")
                .contains("skills/example-skill/references/example-reference.md")
                .doesNotContain("README.md");
        assertThat(result.skillPaths())
                .hasSize(1)
                .contains("example-skill");
    }

    @Test
    @DisplayName("test_download_skill_from_github")
    void testDownloadSkillFromGithub() {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("RUN_GITHUB_TEST")),
                "GitHub remote skill test skipped, set RUN_GITHUB_TEST=1 to enable."
        );

        byte[] referenceFile = RemoteSkillUtil.downloadFileFromGitHub(
                githubTree(),
                "skills/example-skill/references/example-reference.md",
                githubToken()
        );

        assertThat(new String(referenceFile, StandardCharsets.UTF_8))
                .isEqualTo("# Example Reference\n\nExample Reference");
    }

    private static GitHubTree githubTree() {
        return new GitHubTree("dreamofapsychiccat", "remote-skills-test");
    }

    private static String githubToken() {
        return System.getenv().getOrDefault("GITHUB_TOKEN", "");
    }
}
