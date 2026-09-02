// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.singleagent.skills;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.sysop.cwd.CwdContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests for {@link SkillUtil} and {@link GitHubTree}.
 */
class SkillUtilTest {
    @AfterEach
    void tearDown() {
        CwdContext.reset();
    }

    // ========== SkillUtil ==========
    @Test
    void skillUtilStartsWithoutSkills() {
        SkillUtil util = new SkillUtil("sysop-1");
        assertThat(util.hasSkill()).isFalse();
        assertThat(util.getSkillManager()).isNotNull();
        assertThat(util.getRemoteSkillUtil()).isNotNull();
    }

    @Test
    void setSysOperationIdPropagatesToManagers() {
        SkillUtil util = new SkillUtil("old");
        util.setSysOperationId("new");
        assertThat(util.getSkillManager().getSysOperationId()).isEqualTo("new");
        assertThat(util.getRemoteSkillUtil().getSysOperationId()).isEqualTo("new");
    }

    @Test
    void hasSkillIsFalseWhenEmpty() {
        SkillUtil util = new SkillUtil(null);
        assertThat(util.hasSkill()).isFalse();
    }

    @Test
    void promptIncludesRelativeSkillMdWhenInsideWorkspace(@TempDir Path workspace) throws Exception {
        Path skillDir = workspace.resolve("hotel_ranking");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\nname: hotel_ranking\ndescription: Rank hotels\n---\n# Skill\n");

        CwdContext.setWorkspace(workspace.toString());
        SkillUtil util = new SkillUtil("test");
        util.registerSkills(skillDir.toString(), null);

        String prompt = util.getSkillPrompt();
        assertThat(prompt).contains("Skill directory: hotel_ranking/SKILL.md");
        assertThat(prompt).doesNotContain(workspace.toString().replace('\\', '/'));
        assertThat(prompt).doesNotContain(workspace.toAbsolutePath().toString());
    }

    @Test
    void promptOmitsPathWhenSkillOutsideLocalFsBase(@TempDir Path outside) throws Exception {
        Path skillDir = outside.resolve("remote_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\nname: remote_skill\ndescription: Outside skill\n---\n# Skill\n");

        Path workspace = Files.createTempDirectory("skill-ws-inside");
        try {
            CwdContext.setWorkspace(workspace.toString());
            CwdContext.setCwd(workspace.toString());
            CwdContext.setProjectRoot(workspace.toString());

            SkillUtil util = new SkillUtil("test");
            util.registerSkills(skillDir.toString(), null);

            String prompt = util.getSkillPrompt();
            assertThat(prompt).contains("Skill name: remote_skill");
            assertThat(prompt).doesNotContain("Skill directory:");
            assertThat(prompt).doesNotContain(outside.toAbsolutePath().toString());
            assertThat(prompt).doesNotContain(skillDir.toAbsolutePath().toString());
        } finally {
            Files.walk(workspace)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                            // best-effort cleanup
                        }
                    });
        }
    }

    @Test
    void resolveSkillMdPathReturnsNullOutsideBases() {
        assertThat(SkillUtil.resolveSkillMdPathForPrompt("/tmp/travel-hotel-skills-123/hotel_ranking")).isNull();
    }

    // ========== GitHubTree ==========

    @Test
    void gitHubTreeDefaultConstructorValues() {
        GitHubTree tree = new GitHubTree("owner", "repo");
        assertThat(tree.getRepoOwner()).isEqualTo("owner");
        assertThat(tree.getRepoName()).isEqualTo("repo");
        assertThat(tree.getTreeRef()).isEqualTo("HEAD");
        assertThat(tree.getDirectory()).isEmpty();
    }

    @Test
    void gitHubTreeFullConstructorValues() {
        GitHubTree tree = new GitHubTree("owner", "repo", "main", "src/skills");
        assertThat(tree.getRepoOwner()).isEqualTo("owner");
        assertThat(tree.getRepoName()).isEqualTo("repo");
        assertThat(tree.getTreeRef()).isEqualTo("main");
        assertThat(tree.getDirectory()).isEqualTo("src/skills");
    }

    @Test
    void gitHubTreeCopyPreservesFields() {
        GitHubTree original = new GitHubTree("o", "r", "v1", "dir");
        GitHubTree copy = original.copy();

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getRepoOwner()).isEqualTo("o");
        assertThat(copy.getRepoName()).isEqualTo("r");
        assertThat(copy.getTreeRef()).isEqualTo("v1");
        assertThat(copy.getDirectory()).isEqualTo("dir");
    }

    @Test
    void gitHubTreeSettersUpdateFields() {
        GitHubTree tree = new GitHubTree();
        tree.setRepoOwner("newOwner");
        tree.setRepoName("newRepo");
        tree.setTreeRef("develop");
        tree.setDirectory("lib");

        assertThat(tree.getRepoOwner()).isEqualTo("newOwner");
        assertThat(tree.getRepoName()).isEqualTo("newRepo");
        assertThat(tree.getTreeRef()).isEqualTo("develop");
        assertThat(tree.getDirectory()).isEqualTo("lib");
    }
}
