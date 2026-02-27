// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.skills;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for RemoteSkillUtil.
 *
 * <p>Python reference: tests/system_tests/agent/skill/test_remote_skill.py
 *
 * <p>Environment variables:
 * <ul>
 *   <li>GITHUB_TOKEN - GitHub API token (optional, but rate limits may cause failures)</li>
 *   <li>RUN_GITHUB_TEST - Set to "1" to enable GitHub API tests</li>
 * </ul>
 *
 * @since 0.1.4
 */
@DisplayName("RemoteSkillUtil Tests")
class RemoteSkillUtilTest {

    private RemoteSkillUtil remoteSkillUtil;

    // Test repository from Python tests
    private static final String TEST_REPO_OWNER = "dreamofapsychiccat";
    private static final String TEST_REPO_NAME = "remote-skills-test";

    @BeforeEach
    void setUp() {
        remoteSkillUtil = new RemoteSkillUtil("test-operation-id");
    }

    @Test
    @DisplayName("Should create GitHubTree with default values")
    void createGitHubTreeWithDefaults() {
        GitHubTree tree = GitHubTree.of("owner", "repo");

        assertThat(tree.getRepoOwner()).isEqualTo("owner");
        assertThat(tree.getRepoName()).isEqualTo("repo");
        assertThat(tree.getTreeRef()).isEqualTo(GitHubTree.HEAD_REF);
        assertThat(tree.getDirectory()).isEqualTo(Path.of(""));
    }

    @Test
    @DisplayName("Should create GitHubTree with directory")
    void createGitHubTreeWithDirectory() {
        GitHubTree tree = GitHubTree.of("owner", "repo", "skills/");

        assertThat(tree.getRepoOwner()).isEqualTo("owner");
        assertThat(tree.getRepoName()).isEqualTo("repo");
        assertThat(tree.getDirectory()).isEqualTo(Path.of("skills/"));
    }

    @Test
    @DisplayName("Should create GitHubTree with all parameters")
    void createGitHubTreeWithAllParameters() {
        GitHubTree tree = GitHubTree.of("owner", "repo", "abc123", "skills/");

        assertThat(tree.getRepoOwner()).isEqualTo("owner");
        assertThat(tree.getRepoName()).isEqualTo("repo");
        assertThat(tree.getTreeRef()).isEqualTo("abc123");
        assertThat(tree.getDirectory()).isEqualTo(Path.of("skills/"));
    }

    @Test
    @DisplayName("Should generate correct tree API URL")
    void generateTreeApiUrl() {
        GitHubTree tree = GitHubTree.of("owner", "repo", "main", "");

        String url = tree.getTreeApiUrl();

        assertThat(url).isEqualTo("https://api.github.com/repos/owner/repo/git/trees/main");
    }

    @Test
    @DisplayName("Should generate correct contents API URL")
    void generateContentsApiUrl() {
        GitHubTree tree = GitHubTree.of("owner", "repo", "main", "");

        String url = tree.getContentsApiUrl("skills/example/SKILL.md");

        assertThat(url).isEqualTo("https://api.github.com/repos/owner/repo/contents/skills/example/SKILL.md?ref=main");
    }

    @Test
    @DisplayName("Should clone GitHubTree")
    void cloneGitHubTree() {
        GitHubTree original = GitHubTree.of("owner", "repo", "main", "skills/");
        GitHubTree cloned = original.clone();

        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned).isEqualTo(original);
    }

    @Test
    @DisplayName("Should create GitHubTree with new tree ref")
    void withTreeRef() {
        GitHubTree original = GitHubTree.of("owner", "repo", "main", "");
        GitHubTree modified = original.withTreeRef("abc123");

        assertThat(original.getTreeRef()).isEqualTo("main");
        assertThat(modified.getTreeRef()).isEqualTo("abc123");
        assertThat(modified.getRepoOwner()).isEqualTo(original.getRepoOwner());
        assertThat(modified.getRepoName()).isEqualTo(original.getRepoName());
    }

    @Test
    @DisplayName("Should create GitHubTree with new directory")
    void withDirectory() {
        GitHubTree original = GitHubTree.of("owner", "repo", "main", "");
        GitHubTree modified = original.withDirectory(Path.of("skills/"));

        assertThat(original.getDirectory()).isEqualTo(Path.of(""));
        assertThat(modified.getDirectory()).isEqualTo(Path.of("skills/"));
    }

    @Test
    @DisplayName("GitHubTree equals and hashCode should work correctly")
    void gitHubTreeEqualsAndHashCode() {
        GitHubTree tree1 = GitHubTree.of("owner", "repo", "main", "skills/");
        GitHubTree tree2 = GitHubTree.of("owner", "repo", "main", "skills/");
        GitHubTree tree3 = GitHubTree.of("other", "repo", "main", "skills/");

        assertThat(tree1).isEqualTo(tree2);
        assertThat(tree1.hashCode()).isEqualTo(tree2.hashCode());
        assertThat(tree1).isNotEqualTo(tree3);
    }

    @Test
    @DisplayName("GitHubTree toString should contain all fields")
    void gitHubTreeToString() {
        GitHubTree tree = GitHubTree.of("owner", "repo", "main", "skills/");

        String str = tree.toString();

        assertThat(str).contains("owner");
        assertThat(str).contains("repo");
        assertThat(str).contains("main");
        assertThat(str).contains("skills");
    }

    // ==================== GitHub API Tests ====================
    // These tests require RUN_GITHUB_TEST=1 environment variable

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_GITHUB_TEST", matches = "1")
    @DisplayName("Should fetch skills from GitHub")
    void fetchSkillFromGitHub() {
        // Skip if no GitHub token (may hit rate limits)
        String token = System.getenv("GITHUB_TOKEN");

        GitHubTree tree = GitHubTree.of(TEST_REPO_OWNER, TEST_REPO_NAME);
        RemoteSkillUtil.SearchResult result = remoteSkillUtil.searchGitHubForSkills(tree, token);

        List<RemoteSkillUtil.GitHubFile> fileList = result.files();
        List<Path> skillPaths = result.skillPaths();

        // Verify files
        assertThat(fileList).hasSize(2);

        List<Path> filePaths = fileList.stream()
                .map(RemoteSkillUtil.GitHubFile::path)
                .toList();

        assertThat(filePaths).contains(
                Path.of("skills/example-skill/SKILL.md"),
                Path.of("skills/example-skill/references/example-reference.md")
        );
        assertThat(filePaths).doesNotContain(Path.of("README.md"));

        // Verify skill paths
        assertThat(skillPaths).hasSize(1);
        assertThat(skillPaths).contains(Path.of("example-skill"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_GITHUB_TEST", matches = "1")
    @DisplayName("Should download file from GitHub")
    void downloadSkillFromGitHub() {
        String token = System.getenv("GITHUB_TOKEN");

        GitHubTree tree = GitHubTree.of(TEST_REPO_OWNER, TEST_REPO_NAME);
        byte[] content = remoteSkillUtil.downloadFileFromGitHub(
                tree,
                "skills/example-skill/references/example-reference.md",
                token
        );

        String expectedContent = "# Example Reference\n\nExample Reference";
        assertThat(new String(content)).isEqualTo(expectedContent);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_GITHUB_TEST", matches = "1")
    @DisplayName("Should handle missing file gracefully")
    void handleMissingFileFromGitHub() {
        String token = System.getenv("GITHUB_TOKEN");

        GitHubTree tree = GitHubTree.of(TEST_REPO_OWNER, TEST_REPO_NAME);

        assertThatThrownBy(() -> remoteSkillUtil.downloadFileFromGitHub(
                tree,
                "non-existent-file.md",
                token
        )).isInstanceOf(Exception.class);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_GITHUB_TEST", matches = "1")
    @DisplayName("Should upload skills from GitHub to local directory")
    void uploadSkillFromGitHub() throws Exception {
        String token = System.getenv("GITHUB_TOKEN");

        // Create temp directory for skills
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("skills-test");

        try {
            GitHubTree tree = GitHubTree.of(TEST_REPO_OWNER, TEST_REPO_NAME);
            java.util.concurrent.CompletableFuture<List<Path>> future =
                    remoteSkillUtil.uploadSkillFromGitHub(tree, tempDir.toString(), token);

            List<Path> skillPaths = future.get();

            assertThat(skillPaths).hasSize(1);
            assertThat(skillPaths).contains(Path.of("example-skill"));

            // Verify files were downloaded
            java.nio.file.Path skillDir = tempDir.resolve("example-skill");
            assertThat(java.nio.file.Files.exists(skillDir)).isTrue();
            assertThat(java.nio.file.Files.exists(skillDir.resolve("SKILL.md"))).isTrue();
        } finally {
            // Cleanup
            java.nio.file.Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            java.nio.file.Files.deleteIfExists(p);
                        } catch (java.io.IOException ignored) {
                        }
                    });
        }
    }
}
