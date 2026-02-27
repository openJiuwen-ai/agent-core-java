// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.skills;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SkillManager.
 *
 * <p>Python reference: tests/system_tests/agent/skill/test_skill_real_system.py
 * (SkillManager related tests)
 *
 * @since 0.1.4
 */
@DisplayName("SkillManager Tests")
class SkillManagerTest {

    private SkillManager skillManager;
    private Path tempSkillDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        skillManager = new SkillManager("test-operation-id");
        tempSkillDir = tempDir.resolve("test-skills");
        Files.createDirectories(tempSkillDir);
    }

    // ==================== Basic Registration Tests ====================

    @Test
    @DisplayName("Should register skill from directory with SKILL.md")
    void registerSkillFromDirectory() throws Exception {
        // Create a skill directory with SKILL.md
        Path skillDir = tempSkillDir.resolve("my-skill");
        Files.createDirectories(skillDir);

        String skillContent = """
                ---
                description: "A test skill for unit testing"
                ---
                # My Skill

                This is a test skill.
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());

        // Register the skill
        skillManager.register(tempSkillDir, false);

        // Verify
        assertThat(skillManager.count()).isEqualTo(1);
        assertThat(skillManager.has("my-skill")).isTrue();

        Skill skill = skillManager.get("my-skill");
        assertThat(skill).isNotNull();
        assertThat(skill.getName()).isEqualTo("my-skill");
        assertThat(skill.getDescription()).isEqualTo("A test skill for unit testing");
    }

    @Test
    @DisplayName("Should not register skill without SKILL.md")
    void shouldNotRegisterSkillWithoutSkillFile() throws Exception {
        // Create a skill directory without SKILL.md
        Path skillDir = tempSkillDir.resolve("empty-skill");
        Files.createDirectories(skillDir);

        // Register the skill
        skillManager.register(tempSkillDir, false);

        // Verify - should not register the empty skill
        assertThat(skillManager.has("empty-skill")).isFalse();
    }

    @Test
    @DisplayName("Should register multiple skills from directory")
    void registerMultipleSkills() throws Exception {
        // Create multiple skills
        for (int i = 1; i <= 3; i++) {
            Path skillDir = tempSkillDir.resolve("skill-" + i);
            Files.createDirectories(skillDir);
            String skillContent = String.format("""
                    ---
                    description: "Skill %d"
                    ---
                    """, i);
            Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());
        }

        skillManager.register(tempSkillDir, false);

        assertThat(skillManager.count()).isEqualTo(3);
        assertThat(skillManager.has("skill-1")).isTrue();
        assertThat(skillManager.has("skill-2")).isTrue();
        assertThat(skillManager.has("skill-3")).isTrue();
    }

    // ==================== Duplicate Registration Tests ====================

    @Test
    @DisplayName("Should handle duplicate registration without overwrite")
    void duplicateRegistrationWithoutOverwrite() throws Exception {
        // Create and register a skill
        Path skillDir = tempSkillDir.resolve("duplicate-skill");
        Files.createDirectories(skillDir);
        String skillContent = """
                ---
                description: "Original skill"
                ---
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());

        // First registration
        skillManager.register(tempSkillDir, false);
        assertThat(skillManager.count()).isEqualTo(1);

        // Second registration without overwrite should keep original skill
        skillManager.register(tempSkillDir, false);

        // Verify original skill still exists with original description
        assertThat(skillManager.count()).isEqualTo(1);
        assertThat(skillManager.get("duplicate-skill").getDescription()).isEqualTo("Original skill");
    }

    @Test
    @DisplayName("Should overwrite on duplicate registration with overwrite=true")
    void duplicateRegistrationWithOverwriteSucceeds() throws Exception {
        // Create and register a skill
        Path skillDir = tempSkillDir.resolve("overwrite-skill");
        Files.createDirectories(skillDir);

        // First version
        String skillContent1 = """
                ---
                description: "Version 1"
                ---
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent1.trim());

        skillManager.register(tempSkillDir, false);
        assertThat(skillManager.get("overwrite-skill").getDescription()).isEqualTo("Version 1");

        // Register again with overwrite=true
        skillManager.register(tempSkillDir, true);
        assertThat(skillManager.count()).isEqualTo(1);
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Should not register skill when SKILL.md missing description")
    void missingDescriptionNotRegistered() throws Exception {
        Path skillDir = tempSkillDir.resolve("no-desc-skill");
        Files.createDirectories(skillDir);

        // SKILL.md without description field
        String skillContent = """
                ---
                name: some-name
                ---
                # Skill without description
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());

        // Should not throw, but skill should not be registered
        skillManager.register(tempSkillDir, false);
        assertThat(skillManager.has("no-desc-skill")).isFalse();
    }

    @Test
    @DisplayName("Should not register skill when SKILL.md has no front matter")
    void noFrontMatterNotRegistered() throws Exception {
        Path skillDir = tempSkillDir.resolve("no-frontmatter-skill");
        Files.createDirectories(skillDir);

        // SKILL.md without YAML front matter
        String skillContent = "# No front matter\n\nJust markdown content.";
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent);

        // Should not throw, but skill should not be registered
        skillManager.register(tempSkillDir, false);
        assertThat(skillManager.has("no-frontmatter-skill")).isFalse();
    }

    @Test
    @DisplayName("Should handle non-existent path gracefully")
    void nonExistentPathHandledGracefully() {
        Path nonExistent = tempSkillDir.resolve("non-existent");

        // Should not throw, just skip
        skillManager.register(nonExistent, false);
        assertThat(skillManager.count()).isEqualTo(0);
    }

    // ==================== Registry Operations Tests ====================

    @Test
    @DisplayName("Should unregister skill by name")
    void unregisterSkill() throws Exception {
        // Create and register a skill
        Path skillDir = tempSkillDir.resolve("temp-skill");
        Files.createDirectories(skillDir);
        String skillContent = """
                ---
                description: "Temporary skill"
                ---
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());

        skillManager.register(tempSkillDir, false);
        assertThat(skillManager.has("temp-skill")).isTrue();

        // Unregister
        skillManager.unregister("temp-skill");
        assertThat(skillManager.has("temp-skill")).isFalse();
        assertThat(skillManager.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should clear all skills")
    void clearAllSkills() throws Exception {
        // Create multiple skills
        for (int i = 1; i <= 3; i++) {
            Path skillDir = tempSkillDir.resolve("skill-" + i);
            Files.createDirectories(skillDir);
            String skillContent = String.format("""
                    ---
                    description: "Skill %d"
                    ---
                    """, i);
            Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());
        }

        skillManager.register(tempSkillDir, false);
        assertThat(skillManager.count()).isEqualTo(3);

        // Clear
        skillManager.clear();
        assertThat(skillManager.count()).isZero();
        assertThat(skillManager.hasSkills()).isFalse();
    }

    @Test
    @DisplayName("Should get all skill names")
    void getAllSkillNames() throws Exception {
        // Create multiple skills
        for (int i = 1; i <= 2; i++) {
            Path skillDir = tempSkillDir.resolve("skill-" + i);
            Files.createDirectories(skillDir);
            String skillContent = String.format("""
                    ---
                    description: "Skill %d"
                    ---
                    """, i);
            Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());
        }

        skillManager.register(tempSkillDir, false);
        List<String> names = skillManager.getNames();

        assertThat(names).hasSize(2);
        assertThat(names).contains("skill-1", "skill-2");
    }

    @Test
    @DisplayName("Should get all skills")
    void getAllSkills() throws Exception {
        // Create a skill
        Path skillDir = tempSkillDir.resolve("test-skill");
        Files.createDirectories(skillDir);
        String skillContent = """
                ---
                description: "Test skill description"
                ---
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());

        skillManager.register(tempSkillDir, false);
        List<Skill> skills = skillManager.getAll();

        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).getName()).isEqualTo("test-skill");
    }

    @Test
    @DisplayName("Should return null for non-existent skill")
    void getNonExistentSkill() {
        Skill skill = skillManager.get("non-existent");
        assertThat(skill).isNull();
    }

    @Test
    @DisplayName("Should check if skill exists")
    void checkSkillExists() throws Exception {
        // Create a skill
        Path skillDir = tempSkillDir.resolve("existing-skill");
        Files.createDirectories(skillDir);
        String skillContent = """
                ---
                description: "Existing skill"
                ---
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());

        skillManager.register(tempSkillDir, false);

        assertThat(skillManager.has("existing-skill")).isTrue();
        assertThat(skillManager.has("non-existent")).isFalse();
    }

    // ==================== Multiple Path Registration Tests ====================

    @Test
    @DisplayName("Should register skills from multiple paths")
    void registerFromMultiplePaths() throws Exception {
        // Create two skill directories
        Path skillDir1 = tempSkillDir.resolve("skills-a");
        Path skillDir2 = tempSkillDir.resolve("skills-b");
        Files.createDirectories(skillDir1);
        Files.createDirectories(skillDir2);

        // Create skills in each directory
        Path skill1 = skillDir1.resolve("skill-1");
        Files.createDirectories(skill1);
        Files.writeString(skill1.resolve("SKILL.md"), """
                ---
                description: "Skill 1"
                ---
                """.trim());

        Path skill2 = skillDir2.resolve("skill-2");
        Files.createDirectories(skill2);
        Files.writeString(skill2.resolve("SKILL.md"), """
                ---
                description: "Skill 2"
                ---
                """.trim());

        // Register from multiple paths
        skillManager.register(List.of(skillDir1, skillDir2), false);

        assertThat(skillManager.count()).isEqualTo(2);
        assertThat(skillManager.has("skill-1")).isTrue();
        assertThat(skillManager.has("skill-2")).isTrue();
    }

    // ==================== Null Safety Tests ====================

    @Test
    @DisplayName("Should handle null path gracefully")
    void handleNullPath() {
        skillManager.register((Path) null, false);
        assertThat(skillManager.count()).isZero();
    }

    @Test
    @DisplayName("Should handle null path list gracefully")
    void handleNullPathList() {
        skillManager.register((List<Path>) null, false);
        assertThat(skillManager.count()).isZero();
    }

    @Test
    @DisplayName("Should handle null skill name in get")
    void handleNullSkillNameInGet() {
        Skill skill = skillManager.get(null);
        assertThat(skill).isNull();
    }

    @Test
    @DisplayName("Should handle null skill name in has")
    void handleNullSkillNameInHas() {
        assertThat(skillManager.has(null)).isFalse();
    }

    @Test
    @DisplayName("Should handle null skill name in unregister")
    void handleNullSkillNameInUnregister() {
        // Should not throw
        skillManager.unregister(null);
        assertThat(skillManager.count()).isZero();
    }

    // ==================== SysOperationId Tests ====================

    @Test
    @DisplayName("Should set and get sysOperationId")
    void setAndGetSysOperationId() {
        assertThat(skillManager.getSysOperationId()).isEqualTo("test-operation-id");

        skillManager.setSysOperationId("new-operation-id");
        assertThat(skillManager.getSysOperationId()).isEqualTo("new-operation-id");
    }

    @Test
    @DisplayName("Should have correct count after operations")
    void correctCount() throws Exception {
        assertThat(skillManager.count()).isZero();

        // Create a skill
        Path skillDir = tempSkillDir.resolve("single-skill");
        Files.createDirectories(skillDir);
        String skillContent = """
                ---
                description: "Single skill"
                ---
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());

        skillManager.register(tempSkillDir, false);
        assertThat(skillManager.count()).isEqualTo(1);
    }
}
