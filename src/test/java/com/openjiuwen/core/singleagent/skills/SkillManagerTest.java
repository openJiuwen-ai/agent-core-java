// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.singleagent.skills;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        Skill skill = Skill.builder().name("codeReview").description("Code review skill")
                .directory("/skills/code_review").build();

        assertThat(skill.getName()).isEqualTo("codeReview");
        assertThat(skill.getDescription()).isEqualTo("Code review skill");
        assertThat(skill.getDirectory()).isEqualTo("/skills/code_review");
    }

    @Test
    void testSkillToString() {
        Skill skill = Skill.builder().name("test").description("desc").directory("/dir").build();

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

    // ========== Block scalar description parsing ==========

    @Test
    void testRegisterWithLiteralBlockScalar(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("block_literal");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\n" + "name: block_literal\n" + "description: |\n"
                        + "  8-role debate-pattern investment analysis team.\n"
                        + "  Use when analyzing a security for investment decision.\n"
                        + "  Do NOT use for single-perspective analysis.\n" + "---\n# Skill");
        manager.register(skillDir.resolve("SKILL.md").toString());
        Skill skill = manager.get("block_literal");
        assertThat(skill).isNotNull();
        assertThat(skill.getDescription()).isEqualTo("8-role debate-pattern investment analysis team.\n"
                + "Use when analyzing a security for investment decision.\n"
                + "Do NOT use for single-perspective analysis.");
    }

    @Test
    void testRegisterWithLiteralBlockScalarStripChomping(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("block_strip");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\n" + "description: |-\n" + "  line one\n" + "  line two\n" + "---\n");
        manager.register(skillDir.resolve("SKILL.md").toString());
        assertThat(manager.get("block_strip").getDescription()).isEqualTo("line one\nline two");
    }

    @Test
    void testRegisterWithFoldedBlockScalar(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("block_folded");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\n" + "description: >\n" + "  folded paragraph\n" + "  continues here\n" + "---\n");
        manager.register(skillDir.resolve("SKILL.md").toString());
        assertThat(manager.get("block_folded").getDescription()).isEqualTo("folded paragraph continues here");
    }

    @Test
    void testRegisterWithFoldedBlockScalarStripChomping(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("block_folded_strip");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\n" + "description: >-\n" + "  alpha\n" + "  beta\n" + "---\n");
        manager.register(skillDir.resolve("SKILL.md").toString());
        assertThat(manager.get("block_folded_strip").getDescription()).isEqualTo("alpha beta");
    }

    @Test
    void testRegisterWithQuotedInlineDescription(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("quoted");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\n" + "description: \"quoted: with colons and ; semicolons\"\n" + "---\n");
        manager.register(skillDir.resolve("SKILL.md").toString());
        assertThat(manager.get("quoted").getDescription()).isEqualTo("quoted: with colons and ; semicolons");
    }

    @Test
    void testRegisterWithInlineDescriptionContainingColons(@TempDir Path tempDir) throws IOException {
        // Ensures we still handle the original inline case and don't break on
        // values that themselves contain colons.
        Path skillDir = tempDir.resolve("inline_colons");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\n" + "description: Use this when path/to:thing matters\n" + "---\n");
        manager.register(skillDir.resolve("SKILL.md").toString());
        assertThat(manager.get("inline_colons").getDescription()).isEqualTo("Use this when path/to:thing matters");
    }

    @Test
    void testRegisterWithBlockScalarAndBlankLine(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("block_with_blank");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\n" + "description: |\n" + "  first paragraph\n" + "\n" + "  second paragraph\n" + "---\n");
        manager.register(skillDir.resolve("SKILL.md").toString());
        assertThat(manager.get("block_with_blank").getDescription()).isEqualTo("first paragraph\n\nsecond paragraph");
    }
}
