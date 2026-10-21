/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillSource;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.tools.test_skill_tool} in {@code
 * tests/unit_tests/harness/tools/test_skill_tool.py}.
 */
class SkillToolMissingTest {

    @TempDir private Path tempDir;

    @Test
    void skillToolReadsSkillMarkdown() throws Exception {
        SkillDescriptor skill = writeSkill("test_skill_1", "skill description 1", "skill body 1");
        SkillTool skillTool = new SkillTool(() -> List.of(skill));

        ToolOutput skillResult =
                invokeSkillTool(
                        skillTool,
                        Map.of("skill_name", "test_skill_1", "relative_file_path", ""));

        assertTrue(skillResult.isSuccess());
        assertDataContains(skillResult, skill.directory());
        assertDataContains(skillResult, "skill body 1");
    }

    @Test
    void skillToolSupportsCoreRelativePathContract() throws Exception {
        SkillDescriptor skill =
                writeSkill("core_contract_skill", "skill description", "skill body");
        SkillTool skillTool = new SkillTool(() -> List.of(skill));

        if (!(skillTool.invoke(
                        Map.of("skill_name", "core_contract_skill", "relative_path", ""), Map.of())
                instanceof ToolOutput mainResult)) {
            throw new IllegalStateException("skill_tool did not return ToolOutput");
        }

        assertTrue(mainResult.isSuccess());
        assertDataContains(mainResult, "skill body");
    }

    @Test
    void skillToolRejectsPathEscape() throws Exception {
        SkillDescriptor skill = writeSkill("safe_skill", "skill description", "skill body");
        SkillTool skillTool = new SkillTool(() -> List.of(skill));

        if (!(skillTool.invoke(
                        Map.of("skill_name", "safe_skill", "relative_path", "../outside.md"),
                        Map.of())
                instanceof ToolOutput result)) {
            throw new IllegalStateException("skill_tool did not return ToolOutput");
        }

        assertFalse(result.isSuccess());
    }

    @Test
    void skillToolInvalidSkillReturnsFailure() throws Exception {
        SkillDescriptor skill = writeSkill("test_skill_1", "skill description 1", "skill body 1");
        SkillTool skillTool = new SkillTool(() -> List.of(skill));

        ToolOutput skillResult =
                invokeSkillTool(
                        skillTool,
                        Map.of("skill_name", "test_skill_2", "relative_file_path", ""));

        assertFalse(skillResult.isSuccess());
        assertNotNull(skillResult.getError());
    }

    @Test
    void skillToolReadsReferenceFile() throws Exception {
        SkillDescriptor skill = writeSkill("test_skill_1", "skill description 1", "skill body 1");
        writeSkillReferenceFile(
                "test_skill_1", "reference/temp_file.md", "test_skill_1 temp file content");
        SkillTool skillTool = new SkillTool(() -> List.of(skill));

        ToolOutput skillResult =
                invokeSkillTool(
                        skillTool,
                        Map.of(
                                "skill_name",
                                "test_skill_1",
                                "relative_file_path",
                                "reference/temp_file.md"));

        assertTrue(skillResult.isSuccess());
        assertDataContains(skillResult, "test_skill_1 temp file content");
    }

    @Test
    void skillToolInvalidReferenceFileReturnsFailure() throws Exception {
        SkillDescriptor skill = writeSkill("test_skill_1", "skill description 1", "skill body 1");
        writeSkillReferenceFile(
                "test_skill_1", "reference/temp_file.md", "test_skill_1 temp file content");
        SkillTool skillTool = new SkillTool(() -> List.of(skill));

        ToolOutput skillResult =
                invokeSkillTool(
                        skillTool,
                        Map.of(
                                "skill_name",
                                "test_skill_1",
                                "relative_file_path",
                                "reference/unknown_file.md"));

        assertFalse(skillResult.isSuccess());
        assertNotNull(skillResult.getError());
    }

    @Test
    void skillToolReadsClasspathSkillAndPreservesMetadata() throws Exception {
        SkillDescriptor skill =
                new SkillDescriptor(
                        "builtin_demo",
                        "Classpath skill.",
                        "issue196/skills/builtin_demo",
                        Map.of(
                                "source", "classpath",
                                "id", "classpath/builtin_demo",
                                "protected", true));
        SkillTool skillTool = new SkillTool(() -> List.of(skill));

        if (!(skillTool.invoke(Map.of("skill_name", "builtin_demo", "relative_path", ""), Map.of())
                instanceof ToolOutput result)) {
            throw new IllegalStateException("skill_tool did not return ToolOutput");
        }

        assertTrue(result.isSuccess());
        assertDataContains(
                result, "This classpath fixture is used to verify three-level Skill loading.");
        Skill skillModel = SkillTool.toSkillModel(skill);
        assertThat(skillModel.getSource()).isEqualTo(SkillSource.CLASSPATH);
        assertThat(skillModel.getId()).isEqualTo("classpath/builtin_demo");
        assertThat(skillModel.isProtectedSkill()).isTrue();
    }

    @Test
    void skillToolSelectsExplicitPathWithoutName() throws Exception {
        SkillDescriptor skill =
                writeSkill("explicit_path_skill", "Explicit path skill.", "Explicit skill body");
        SkillTool skillTool = new SkillTool(() -> List.of(skill));

        if (!(skillTool.invoke(
                        Map.of("skill_path", skill.directory(), "relative_path", ""), Map.of())
                instanceof ToolOutput result)) {
            throw new IllegalStateException("skill_tool did not return ToolOutput");
        }

        assertTrue(result.isSuccess());
        assertDataContains(result, "Explicit skill body");
    }

    private static ToolOutput invokeSkillTool(
            SkillTool skillTool, Map<String, Object> inputs) throws Exception {
        if (!(skillTool.invoke(inputs, Map.of()) instanceof ToolOutput result)) {
            throw new IllegalStateException("skill_tool did not return ToolOutput");
        }
        return result;
    }

    private SkillDescriptor writeSkill(String name, String description, String body)
            throws Exception {
        Path skillDir = tempDir.resolve("skills").resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                "---\n"
                        + "description: "
                        + description
                        + "\n"
                        + "---\n\n"
                        + "# "
                        + name
                        + "\n"
                        + body,
                StandardCharsets.UTF_8);
        return new SkillDescriptor(name, description, skillDir.toString(), Map.of());
    }

    private void writeSkillReferenceFile(String skillName, String relativePath, String body)
            throws Exception {
        Path file = tempDir.resolve("skills").resolve(skillName).resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private void assertDataContains(ToolOutput output, String expected) {
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertTrue(
                data.values().stream()
                        .map(String::valueOf)
                        .anyMatch(value -> value.contains(expected)));
    }
}
