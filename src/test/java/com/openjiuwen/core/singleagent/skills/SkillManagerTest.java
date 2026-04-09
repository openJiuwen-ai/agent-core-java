// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
package com.openjiuwen.core.singleagent.skills;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SkillManager} and {@link Skill}.
 */
class SkillManagerTest {

    private SkillManager manager;

    @BeforeEach
    void setUp() {
        manager = new SkillManager("test-sysop");
    }

    // ========== Skill data class ==========

    @Test
    void testSkillBuilder() {
        Skill skill = Skill.builder()
                .name("codeReview")
                .description("Code review skill")
                .directory("/skills/code_review")
                .build();

        assertThat(skill.getName()).isEqualTo("codeReview");
        assertThat(skill.getDescription()).isEqualTo("Code review skill");
        assertThat(skill.getDirectory()).isEqualTo("/skills/code_review");
    }

    @Test
    void testSkillToString() {
        Skill skill = Skill.builder()
                .name("test")
                .description("desc")
                .directory("/dir")
                .build();

        String str = skill.toString();
        assertThat(str).contains("test");
        assertThat(str).contains("desc");
        assertThat(str).contains("/dir");
    }

    // ========== SkillManager basic ==========

    @Test
    void testInitialState() {
        assertThat(manager.count()).isZero();
        assertThat(manager.getAll()).isEmpty();
        assertThat(manager.getNames()).isEmpty();
        assertThat(manager.getSysOperationId()).isEqualTo("test-sysop");
    }

    @Test
    void testSetSysOperationId() {
        manager.setSysOperationId("new-id");
        assertThat(manager.getSysOperationId()).isEqualTo("new-id");
    }

    @Test
    void testGetAndHas() {
        assertThat(manager.has("nonexistent")).isFalse();
        assertThat(manager.get("nonexistent")).isNull();
    }

    @Test
    void testClear() {
        // Manually add via registration from a temp directory
        // For now, just test clear on empty
        manager.clear();
        assertThat(manager.count()).isZero();
    }

    @Test
    void testDescription() {
        manager.setDescription("test description");
        assertThat(manager.getDescription()).isEqualTo("test description");
    }

    // ========== Registration from file system ==========

    @Test
    void testRegisterFromSkillMd(@TempDir Path tempDir) throws IOException {
        // Create a skill directory with SKILL.md
        Path skillDir = tempDir.resolve("myskill");
        Files.createDirectories(skillDir);
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, "---\ndescription: A test skill\n---\n# My Skill");

        // Register from the SKILL.md path
        manager.register(skillMd.toString());

        assertThat(manager.count()).isEqualTo(1);
        assertThat(manager.has("myskill")).isTrue();
        Skill skill = manager.get("myskill");
        assertThat(skill.getDescription()).isEqualTo("A test skill");
    }

    @Test
    void testRegisterFromDirectory(@TempDir Path tempDir) throws IOException {
        // Create subdirectories with SKILL.md files
        Path skill1Dir = tempDir.resolve("skill1");
        Files.createDirectories(skill1Dir);
        Files.writeString(skill1Dir.resolve("SKILL.md"), "---\ndescription: Skill one\n---");

        Path skill2Dir = tempDir.resolve("skill2");
        Files.createDirectories(skill2Dir);
        Files.writeString(skill2Dir.resolve("Skill.md"), "---\ndescription: Skill two\n---");

        manager.register(tempDir.toString());

        assertThat(manager.count()).isEqualTo(2);
        assertThat(manager.has("skill1")).isTrue();
        assertThat(manager.has("skill2")).isTrue();
    }

    @Test
    void testRegisterDuplicateLoggedAsWarning(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("dupskill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Dup\n---");

        manager.register(skillDir.resolve("SKILL.md").toString());
        assertThat(manager.count()).isEqualTo(1);

        // Second register catches exception internally and logs warning
        // Skill count should remain 1
        manager.register(skillDir.resolve("SKILL.md").toString());
        assertThat(manager.count()).isEqualTo(1);
    }

    @Test
    void testRegisterNullPath() {
        manager.register((String) null);
        assertThat(manager.count()).isZero();
    }

    @Test
    void testRegisterEmptyPath() {
        manager.register("");
        assertThat(manager.count()).isZero();
    }

    // ========== Unregister ==========

    @Test
    void testUnregister(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("removable");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Remove me\n---");

        manager.register(skillDir.resolve("SKILL.md").toString());
        assertThat(manager.has("removable")).isTrue();

        manager.unregister("removable");
        assertThat(manager.has("removable")).isFalse();
        assertThat(manager.count()).isZero();
    }

    @Test
    void testUnregisterNonExistent() {
        // Should not throw
        manager.unregister("does_not_exist");
    }
}
