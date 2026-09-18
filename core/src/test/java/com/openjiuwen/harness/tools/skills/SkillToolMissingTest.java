/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.skills;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.harness.tools.ToolOutput;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.tools.test_skill_tool} in
 * {@code tests/unit_tests/harness/tools/test_skill_tool.py}.
 */
class SkillToolMissingTest {

    @TempDir
    private Path tempDir;

    @Test
    void skillToolReadsSkillMarkdown() throws Exception {
        SkillDescriptor skill = writeSkill("test_skill_1", "skill description 1", "skill body 1");
        SkillTool skillTool = new SkillTool(() -> List.of(skill));

        ToolOutput skillResult = (ToolOutput) skillTool.invoke(
                Map.of("skill_name", "test_skill_1", "relative_file_path", ""),
                Map.of()
        );

        assertTrue(skillResult.isSuccess());
        assertDataContains(skillResult, skill.directory());
        assertDataContains(skillResult, "skill body 1");
    }

    @Test
    void skillToolInvalidSkillReturnsFailure() throws Exception {
        SkillDescriptor skill = writeSkill("test_skill_1", "skill description 1", "skill body 1");
        SkillTool skillTool = new SkillTool(() -> List.of(skill));

        ToolOutput skillResult = (ToolOutput) skillTool.invoke(
                Map.of("skill_name", "test_skill_2", "relative_file_path", ""),
                Map.of()
        );

        assertFalse(skillResult.isSuccess());
        assertNotNull(skillResult.getError());
    }

    @Test
    void skillToolReadsReferenceFile() throws Exception {
        SkillDescriptor skill = writeSkill("test_skill_1", "skill description 1", "skill body 1");
        writeSkillReferenceFile("test_skill_1", "reference/temp_file.md", "test_skill_1 temp file content");
        SkillTool skillTool = new SkillTool(() -> List.of(skill));

        ToolOutput skillResult = (ToolOutput) skillTool.invoke(
                Map.of("skill_name", "test_skill_1", "relative_file_path", "reference/temp_file.md"),
                Map.of()
        );

        assertTrue(skillResult.isSuccess());
        assertDataContains(skillResult, "test_skill_1 temp file content");
    }

    @Test
    void skillToolInvalidReferenceFileReturnsFailure() throws Exception {
        SkillDescriptor skill = writeSkill("test_skill_1", "skill description 1", "skill body 1");
        writeSkillReferenceFile("test_skill_1", "reference/temp_file.md", "test_skill_1 temp file content");
        SkillTool skillTool = new SkillTool(() -> List.of(skill));

        ToolOutput skillResult = (ToolOutput) skillTool.invoke(
                Map.of("skill_name", "test_skill_1", "relative_file_path", "reference/unknown_file.md"),
                Map.of()
        );

        assertFalse(skillResult.isSuccess());
        assertNotNull(skillResult.getError());
    }

    private SkillDescriptor writeSkill(String name, String description, String body) throws Exception {
        Path skillDir = tempDir.resolve("skills").resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                "---\n"
                        + "description: " + description + "\n"
                        + "---\n\n"
                        + "# " + name + "\n"
                        + body,
                StandardCharsets.UTF_8
        );
        return new SkillDescriptor(name, description, skillDir.toString(), Map.of());
    }

    private void writeSkillReferenceFile(String skillName, String relativePath, String body) throws Exception {
        Path file = tempDir.resolve("skills").resolve(skillName).resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private void assertDataContains(ToolOutput output, String expected) {
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertTrue(data.values().stream().map(String::valueOf).anyMatch(value -> value.contains(expected)));
    }
}
