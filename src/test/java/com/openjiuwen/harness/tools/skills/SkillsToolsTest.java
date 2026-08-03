/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Mirrors Python's skill tools in {@code openjiuwen/harness/tools/skills/}.
 */
class SkillsToolsTest {

    @TempDir
    private Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void listSkillReturnsAllAndFallbackMessageWithoutRouter() throws Exception {
        SkillDescriptor skill = new SkillDescriptor("alpha", "Alpha skill", tempDir.toString(), Map.of("tag", "a"));
        ListSkillTool tool = new ListSkillTool(() -> List.of(skill));

        ToolOutput all = (ToolOutput) tool.invoke(Map.of(), Map.of());
        Map<String, Object> allData = (Map<String, Object>) all.getData();
        assertEquals("all", allData.get("mode"));

        ToolOutput fallback = (ToolOutput) tool.invoke(Map.of("query", "write tests"), Map.of());
        Map<String, Object> fallbackData = (Map<String, Object>) fallback.getData();
        assertEquals("all", fallbackData.get("mode"));
        assertTrue(String.valueOf(fallbackData.get("message")).contains("fallback to all skills"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSkillFiltersByRouterNames() throws Exception {
        SkillDescriptor alpha = new SkillDescriptor("alpha", "Alpha skill", tempDir.toString(), Map.of());
        SkillDescriptor beta = new SkillDescriptor("beta", "Beta skill", tempDir.toString(), Map.of());
        ListSkillTool tool = new ListSkillTool(() -> List.of(alpha, beta), (query, skills) -> List.of("beta"));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "beta only"), Map.of());

        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertEquals("filtered", data.get("mode"));
        assertEquals(List.of("beta"), data.get("selected_skill_names"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void skillToolReadsDefaultSkillMarkdownAndReportsMissingSkill() throws Exception {
        Files.writeString(tempDir.resolve("SKILL.md"), "# Alpha", StandardCharsets.UTF_8);
        SkillDescriptor skill = new SkillDescriptor("alpha", "Alpha skill", tempDir.toString(), Map.of());
        SkillTool tool = new SkillTool(() -> List.of(skill));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("skill_name", "alpha"), Map.of());

        assertTrue(output.isSuccess());
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertEquals("# Alpha", data.get("skill_content"));

        ToolOutput missing = (ToolOutput) tool.invoke(Map.of("skill_name", ""), Map.of());
        assertFalse(missing.isSuccess());
        assertEquals("Skill not found: ", missing.getError());
    }
}
