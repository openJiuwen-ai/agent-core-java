// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.skills;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SkillUtil.
 *
 * <p>Python reference: tests/system_tests/agent/skill/test_skill_real_system.py
 * (SkillUtil related tests)
 *
 * @since 0.1.4
 */
@DisplayName("SkillUtil Tests")
class SkillUtilTest {

    private SkillUtil skillUtil;
    private Path tempSkillDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        skillUtil = new SkillUtil("test-operation-id");
        tempSkillDir = tempDir.resolve("test-skills");
        Files.createDirectories(tempSkillDir);
    }

    // ==================== Initialization Tests ====================

    @Test
    @DisplayName("Should initialize with default components")
    void initializeWithDefaultComponents() {
        assertThat(skillUtil.getSkillManager()).isNotNull();
        assertThat(skillUtil.getSkillToolKit()).isNotNull();
        assertThat(skillUtil.getRemoteSkillUtil()).isNotNull();
    }

    @Test
    @DisplayName("Should set sysOperationId for all components")
    void setSysOperationIdForAllComponents() {
        skillUtil.setSysOperationId("new-operation-id");

        assertThat(skillUtil.getSkillManager().getSysOperationId()).isEqualTo("new-operation-id");
        assertThat(skillUtil.getSkillToolKit().getSysOperationId()).isEqualTo("new-operation-id");
        assertThat(skillUtil.getRemoteSkillUtil().getSysOperationId()).isEqualTo("new-operation-id");
    }

    // ==================== Skill Registration Tests ====================

    @Test
    @DisplayName("Should return false when no skills registered")
    void noSkillsRegistered() {
        assertThat(skillUtil.hasSkill()).isFalse();
    }

    @Test
    @DisplayName("Should register skills asynchronously")
    void registerSkillsAsync() throws Exception {
        // Create a skill
        Path skillDir = tempSkillDir.resolve("async-skill");
        Files.createDirectories(skillDir);
        String skillContent = """
                ---
                description: "Async skill"
                ---
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());

        // Register asynchronously
        CompletableFuture<Void> future = skillUtil.registerSkills(tempSkillDir.toString(), false);
        future.get(); // Wait for completion

        assertThat(skillUtil.hasSkill()).isTrue();
        assertThat(skillUtil.getSkillCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should register skills with default overwrite=false")
    void registerSkillsWithDefaultOverwrite() throws Exception {
        // Create a skill
        Path skillDir = tempSkillDir.resolve("default-skill");
        Files.createDirectories(skillDir);
        String skillContent = """
                ---
                description: "Default skill"
                ---
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());

        // Register with default parameters
        CompletableFuture<Void> future = skillUtil.registerSkills(tempSkillDir.toString());
        future.get();

        assertThat(skillUtil.hasSkill()).isTrue();
    }

    @Test
    @DisplayName("Should register skills from multiple paths")
    void registerSkillsFromMultiplePaths() throws Exception {
        // Create two skill directories
        Path dir1 = tempSkillDir.resolve("dir1");
        Path dir2 = tempSkillDir.resolve("dir2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);

        // Create skills
        Path skill1 = dir1.resolve("skill-1");
        Files.createDirectories(skill1);
        Files.writeString(skill1.resolve("SKILL.md"), """
                ---
                description: "Skill 1"
                ---
                """.trim());

        Path skill2 = dir2.resolve("skill-2");
        Files.createDirectories(skill2);
        Files.writeString(skill2.resolve("SKILL.md"), """
                ---
                description: "Skill 2"
                ---
                """.trim());

        // Register from multiple paths
        CompletableFuture<Void> future = skillUtil.registerSkills(
                List.of(dir1.toString(), dir2.toString()), false);
        future.get();

        assertThat(skillUtil.getSkillCount()).isEqualTo(2);
        assertThat(skillUtil.getSkill("skill-1")).isNotNull();
        assertThat(skillUtil.getSkill("skill-2")).isNotNull();
    }

    // ==================== Skill Retrieval Tests ====================

    @Test
    @DisplayName("Should get skill by name")
    void getSkillByName() throws Exception {
        // Create a skill
        Path skillDir = tempSkillDir.resolve("named-skill");
        Files.createDirectories(skillDir);
        String skillContent = """
                ---
                description: "Named skill for testing"
                ---
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());

        skillUtil.getSkillManager().register(tempSkillDir, false);

        Skill skill = skillUtil.getSkill("named-skill");

        assertThat(skill).isNotNull();
        assertThat(skill.getName()).isEqualTo("named-skill");
        assertThat(skill.getDescription()).isEqualTo("Named skill for testing");
    }

    @Test
    @DisplayName("Should return null for non-existent skill")
    void getNonExistentSkill() {
        Skill skill = skillUtil.getSkill("non-existent");
        assertThat(skill).isNull();
    }

    @Test
    @DisplayName("Should get all skills")
    void getAllSkills() throws Exception {
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

        skillUtil.getSkillManager().register(tempSkillDir, false);
        List<Skill> skills = skillUtil.getAllSkills();

        assertThat(skills).hasSize(2);
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

        skillUtil.getSkillManager().register(tempSkillDir, false);
        List<String> names = skillUtil.getSkillNames();

        assertThat(names).hasSize(2);
        assertThat(names).contains("skill-1", "skill-2");
    }

    // ==================== Skill Unregistration Tests ====================

    @Test
    @DisplayName("Should unregister skill")
    void unregisterSkill() throws Exception {
        // Create a skill
        Path skillDir = tempSkillDir.resolve("temp-skill");
        Files.createDirectories(skillDir);
        String skillContent = """
                ---
                description: "Temporary skill"
                ---
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());

        skillUtil.getSkillManager().register(tempSkillDir, false);
        assertThat(skillUtil.hasSkill()).isTrue();

        skillUtil.unregisterSkill("temp-skill");
        assertThat(skillUtil.hasSkill()).isFalse();
    }

    @Test
    @DisplayName("Should clear all skills")
    void clearSkills() throws Exception {
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

        skillUtil.getSkillManager().register(tempSkillDir, false);
        assertThat(skillUtil.getSkillCount()).isEqualTo(2);

        skillUtil.clearSkills();
        assertThat(skillUtil.getSkillCount()).isZero();
    }

    // ==================== Skill Prompt Tests ====================

    @Test
    @DisplayName("Should generate skill prompt with registered skills")
    void generateSkillPrompt() throws Exception {
        // Create a skill
        Path skillDir = tempSkillDir.resolve("prompt-skill");
        Files.createDirectories(skillDir);
        String skillContent = """
                ---
                description: "Skill for prompt testing"
                ---
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());

        skillUtil.getSkillManager().register(tempSkillDir, false);

        String prompt = skillUtil.getSkillPrompt();

        assertThat(prompt).isNotEmpty();
        assertThat(prompt).contains("You are an agent equipped with various skills");
        assertThat(prompt).contains("prompt-skill");
        assertThat(prompt).contains("Skill for prompt testing");
    }

    @Test
    @DisplayName("Should generate system prompt only when no skills")
    void generateSystemPromptOnly() {
        String prompt = skillUtil.getSkillPrompt();

        assertThat(prompt).isNotEmpty();
        assertThat(prompt).contains("You are an agent equipped with various skills");
        assertThat(prompt).doesNotContain("Skill name:");
    }

    @Test
    @DisplayName("Should include skill directory in prompt")
    void includeSkillDirectoryInPrompt() throws Exception {
        // Create a skill
        Path skillDir = tempSkillDir.resolve("dir-skill");
        Files.createDirectories(skillDir);
        String skillContent = """
                ---
                description: "Directory skill"
                ---
                """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillContent.trim());

        skillUtil.getSkillManager().register(tempSkillDir, false);

        String prompt = skillUtil.getSkillPrompt();

        assertThat(prompt).contains("dir-skill");
        assertThat(prompt).contains("Directory skill");
    }

    // ==================== Tool Retrieval Tests ====================

    @Test
    @DisplayName("Should get all tools")
    void getAllTools() {
        List<SkillToolKit.ToolFunction> tools = skillUtil.getAllTools();

        assertThat(tools).hasSize(3);
        assertThat(tools.get(0).name()).isEqualTo("view_file");
        assertThat(tools.get(1).name()).isEqualTo("execute_code");
        assertThat(tools.get(2).name()).isEqualTo("run_command");
    }

    @Test
    @DisplayName("Should get individual tools")
    void getIndividualTools() {
        assertThat(skillUtil.getViewFileTool().name()).isEqualTo("view_file");
        assertThat(skillUtil.getExecuteCodeTool().name()).isEqualTo("execute_code");
        assertThat(skillUtil.getRunCommandTool().name()).isEqualTo("run_command");
    }

    @Test
    @DisplayName("Tools should have correct IDs")
    void toolsHaveCorrectIds() {
        assertThat(skillUtil.getViewFileTool().id()).isEqualTo("_internal_view_file");
        assertThat(skillUtil.getExecuteCodeTool().id()).isEqualTo("_internal_execute_code");
        assertThat(skillUtil.getRunCommandTool().id()).isEqualTo("_internal_run_command");
    }

    // ==================== Count Tests ====================

    @Test
    @DisplayName("Should return correct skill count")
    void correctSkillCount() throws Exception {
        assertThat(skillUtil.getSkillCount()).isZero();

        // Create skills
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

        skillUtil.getSkillManager().register(tempSkillDir, false);

        assertThat(skillUtil.getSkillCount()).isEqualTo(3);
    }

    // ==================== Remote Skill Tests ====================

    @Test
    @DisplayName("Should get remote skill util")
    void getRemoteSkillUtil() {
        assertThat(skillUtil.getRemoteSkillUtil()).isNotNull();
        assertThat(skillUtil.getRemoteSkillUtil().getSysOperationId()).isEqualTo("test-operation-id");
    }

    @Test
    @DisplayName("Should register remote skills")
    void registerRemoteSkills() {
        GitHubTree tree = GitHubTree.of("owner", "repo");

        // This returns a CompletableFuture that would need GitHub access
        CompletableFuture<List<Path>> future = skillUtil.registerRemoteSkills(
                tempSkillDir.toString(), tree, null);

        assertThat(future).isNotNull();
    }
}
