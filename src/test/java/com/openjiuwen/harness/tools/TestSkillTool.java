/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.sysop.SysOperation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for skill tool.
 *
 * <p>Mirrors Python's {@code test_skill_tool.py} in
 * {@code tests.unit_tests.harness.tools}.
 */
class TestSkillTool {

    @Test
    void testSkillTool(@TempDir Path tempDir) throws Exception {
        Path skillsRoot = tempDir.resolve("skills");
        Files.createDirectories(skillsRoot);
        Skill skill = writeSkill(skillsRoot, "test_skill_1", "skill description 1", "skill body 1");
        SkillTool skillTool = new SkillTool((SysOperation) null, skills(List.of(skill)));

        ToolOutput skillRes = invoke(skillTool, "test_skill_1", "");

        assertTrue(skillRes.isSuccess());
        assertTrue(dataContainsString(skillRes.getData(), skill.getDirectory()));
        assertTrue(dataContainsString(skillRes.getData(), "skill body 1"));
    }

    @Test
    void testSkillToolInvalidSkill(@TempDir Path tempDir) throws Exception {
        Path skillsRoot = tempDir.resolve("skills");
        Files.createDirectories(skillsRoot);
        Skill skill = writeSkill(skillsRoot, "test_skill_1", "skill description 1", "skill body 1");
        SkillTool skillTool = new SkillTool((SysOperation) null, skills(List.of(skill)));

        ToolOutput skillRes = invoke(skillTool, "test_skill_2", "");

        assertFalse(skillRes.isSuccess());
        assertNotNull(skillRes.getError());
    }

    @Test
    void testSkillToolReferenceFile(@TempDir Path tempDir) throws Exception {
        Path skillsRoot = tempDir.resolve("skills");
        Files.createDirectories(skillsRoot);
        Skill skill = writeSkill(skillsRoot, "test_skill_1", "skill description 1", "skill body 1");
        writeSkillReferenceFile(skillsRoot, "test_skill_1", "reference/temp_file.md",
                "test_skill_1 temp file content");
        SkillTool skillTool = new SkillTool((SysOperation) null, skills(List.of(skill)));

        ToolOutput skillRes = invoke(skillTool, "test_skill_1", "reference/temp_file.md");

        assertTrue(skillRes.isSuccess());
        assertTrue(dataContainsString(skillRes.getData(), "test_skill_1 temp file content"));
    }

    @Test
    void testSkillToolInvalidReferenceFile(@TempDir Path tempDir) throws Exception {
        Path skillsRoot = tempDir.resolve("skills");
        Files.createDirectories(skillsRoot);
        Skill skill = writeSkill(skillsRoot, "test_skill_1", "skill description 1", "skill body 1");
        writeSkillReferenceFile(skillsRoot, "test_skill_1", "reference/temp_file.md",
                "test_skill_1 temp file content");
        SkillTool skillTool = new SkillTool((SysOperation) null, skills(List.of(skill)));

        ToolOutput skillRes = invoke(skillTool, "test_skill_1", "reference/unknown_file.md");

        assertFalse(skillRes.isSuccess());
        assertNotNull(skillRes.getError());
    }

    private static Supplier<List<Skill>> skills(List<Skill> skillList) {
        return () -> skillList;
    }

    private static Skill writeSkill(Path root, String name, String description, String body) throws IOException {
        Path skillDir = root.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\n"
                        + "description: " + description + "\n"
                        + "---\n\n"
                        + "# " + name + "\n"
                        + body);
        return Skill.builder()
                .name(name)
                .description(description)
                .directory(skillDir.toString())
                .build();
    }

    private static void writeSkillReferenceFile(Path root, String skillName, String relativeFilePath, String body)
            throws IOException {
        Path filePath = root.resolve(skillName).resolve(relativeFilePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, body);
    }

    private static ToolOutput invoke(SkillTool skillTool, String skillName, String relativeFilePath) {
        return (ToolOutput) skillTool.invoke(Map.of(
                "skill_name", skillName,
                "relative_file_path", relativeFilePath
        ), Map.of());
    }

    private static boolean dataContainsString(Object data, String query) {
        if (!(data instanceof Map<?, ?> values)) {
            return false;
        }
        return values.values().stream().anyMatch(value -> String.valueOf(value).contains(query));
    }
}
