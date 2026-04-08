// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.singleagent.skills;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SkillUtil} and {@link GitHubTree}.
 */
class SkillUtilTest {

    // ========== SkillUtil ==========

    @Test
    void testSkillUtilInitialState() {
        SkillUtil util = new SkillUtil("sysop-1");
        assertThat(util.hasSkill()).isFalse();
        assertThat(util.getSkillManager()).isNotNull();
        assertThat(util.getRemoteSkillUtil()).isNotNull();
    }

    @Test
    void testSetSysOperationId() {
        SkillUtil util = new SkillUtil("old");
        util.setSysOperationId("new");
        assertThat(util.getSkillManager().getSysOperationId()).isEqualTo("new");
        assertThat(util.getRemoteSkillUtil().getSysOperationId()).isEqualTo("new");
    }

    @Test
    void testHasSkillWhenEmpty() {
        SkillUtil util = new SkillUtil(null);
        assertThat(util.hasSkill()).isFalse();
    }

    @Test
    void testGetSkillPromptFormat() {
        SkillUtil util = new SkillUtil("test");
        // Register a skill programmatically via the skill manager
        Skill skill = Skill.builder()
                .name("testSkill")
                .description("A test skill")
                .directory("/skills/test")
                .build();
        util.getSkillManager().getAll(); // Just verify it's accessible

        // The prompt should be generated even without skills
        String prompt = util.getSkillPrompt();
        assertThat(prompt).contains("skill");
    }

    // ========== GitHubTree ==========

    @Test
    void testGitHubTreeConstructor() {
        GitHubTree tree = new GitHubTree("owner", "repo");
        assertThat(tree.getRepoOwner()).isEqualTo("owner");
        assertThat(tree.getRepoName()).isEqualTo("repo");
        assertThat(tree.getTreeRef()).isEqualTo("HEAD");
        assertThat(tree.getDirectory()).isEmpty();
    }

    @Test
    void testGitHubTreeFullConstructor() {
        GitHubTree tree = new GitHubTree("owner", "repo", "main", "src/skills");
        assertThat(tree.getRepoOwner()).isEqualTo("owner");
        assertThat(tree.getRepoName()).isEqualTo("repo");
        assertThat(tree.getTreeRef()).isEqualTo("main");
        assertThat(tree.getDirectory()).isEqualTo("src/skills");
    }

    @Test
    void testGitHubTreeCopy() {
        GitHubTree original = new GitHubTree("o", "r", "v1", "dir");
        GitHubTree copy = original.copy();

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getRepoOwner()).isEqualTo("o");
        assertThat(copy.getRepoName()).isEqualTo("r");
        assertThat(copy.getTreeRef()).isEqualTo("v1");
        assertThat(copy.getDirectory()).isEqualTo("dir");
    }

    @Test
    void testGitHubTreeSetters() {
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
