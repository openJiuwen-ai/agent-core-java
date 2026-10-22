/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests Skill document loading.
 *
 * @since 0.1.17
 */
class SkillDocumentLoaderTest {
    @TempDir
    private Path tempDir;

    @Test
    void readMainDocument_fromFilesystemSkill() throws IOException {
        Skill skill = createFilesystemSkill("demo_skill", "Demo skill body.");

        assertThat(SkillDocumentLoader.readMainDocument(skill)).contains("Demo skill body.");
    }

    @Test
    void readRelativeDocument_fromFilesystemSkill() throws IOException {
        Skill skill = createFilesystemSkill("doc_skill", "Main body.");
        Path docsDir = skill.getDirectory().resolve("docs");
        Files.createDirectories(docsDir);
        Files.writeString(docsDir.resolve("extra.md"), "Extra content.");

        assertThat(SkillDocumentLoader.readRelativeDocument(skill, "docs/extra.md"))
                .contains("Extra content.");
    }

    @Test
    void readRelativeDocument_rejectsPathEscape() throws IOException {
        Skill skill = createFilesystemSkill("safe_skill", "Safe.");

        assertThat(SkillDocumentLoader.readRelativeDocument(skill, "../outside.md")).isEmpty();
        assertThat(SkillDocumentLoader.readRelativeDocument(skill, "/etc/passwd")).isEmpty();
    }

    @Test
    void readFromClasspathSkill_returnsBuiltinResource() throws IOException {
        Skill skill =
                new Skill("builtin_demo", "Builtin.", Path.of("issue196/skills/builtin_demo"));
        skill.setSource(SkillSource.CLASSPATH);

        assertThat(SkillDocumentLoader.readMainDocument(skill)).isPresent();
    }

    @Test
    void skillConfig_defaultsMatchPlanRecommendations() {
        SkillConfig config = new SkillConfig();
        assertThat(config.isProtectedOverrideBlocked()).isTrue();
        assertThat(config.getWorkspaceMaxBytes()).isEqualTo(262144);
        assertThat(config.isOnDemandLoading()).isTrue();
    }

    @Test
    void workspaceDocuments_rejectOversizedMainAndRelativeFiles() throws IOException {
        Skill skill = createWorkspaceSkill("oversized_ws", "x".repeat(16));
        Files.writeString(skill.getDirectory().resolve("large.md"), "x".repeat(32));
        SkillConfig config = new SkillConfig();
        config.setWorkspaceMaxBytes(8);

        assertThat(SkillDocumentLoader.readMainDocument(skill, config)).isEmpty();
        assertThat(SkillDocumentLoader.readRelativeDocument(skill, "large.md", config)).isEmpty();
    }

    @Test
    void classpathDocuments_doNotUseWorkspaceSizeLimit() {
        Skill skill =
                new Skill("builtin_demo", "Builtin.", Path.of("issue196/skills/builtin_demo"));
        skill.setSource(SkillSource.CLASSPATH);
        SkillConfig config = new SkillConfig();
        config.setWorkspaceMaxBytes(1);

        assertThat(SkillDocumentLoader.readMainDocument(skill, config)).isPresent();
    }

    private Skill createFilesystemSkill(String name, String body) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), body);
        Skill skill = new Skill(name, "Test skill.", dir);
        skill.setSource(SkillSource.FILESYSTEM);
        return skill;
    }

    private Skill createWorkspaceSkill(String name, String body) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), body);
        Skill skill = new Skill(name, "Workspace skill.", dir);
        skill.setSource(SkillSource.WORKSPACE);
        return skill;
    }
}
