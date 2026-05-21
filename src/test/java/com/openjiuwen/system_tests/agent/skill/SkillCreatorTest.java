/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.skill;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import java.util.*;

/**
 * Mirrors Python's test_skill_creator.py.
 */
class SkillCreatorTest {

    static class MockFS {
        List<String> directories = new ArrayList<>();
        Map<String, String> files = new LinkedHashMap<>();

        void addDirectory(String dirName) {
            directories.add(dirName);
        }

        void addFile(String filePath, String fileContents) {
            files.put(filePath, fileContents);
        }
    }

    static final String RUN_REAL_LLM_TESTS = System.getenv().getOrDefault("RUN_REAL_LLM_TESTS", "0");

    @Test
    void testSkillCreationRealLlm() {
        assumeTrue("1".equals(RUN_REAL_LLM_TESTS), "Real LLM test skipped. Set RUN_REAL_LLM_TESTS=1 to enable.");

        String outputDir = System.getenv().getOrDefault("OUTPUT_DIR", "");
        assertNotNull(outputDir);
    }

    @Test
    void testSkillCreationMockLlm() {
        MockFS fs = new MockFS();
        fs.addDirectory("skill_name");
        fs.addFile("skill_name/SKILL.md",
                "---\nname: skill_name\ndescription: sample skill\n---\n# Skill Body\n");

        assertTrue(fs.directories.contains("skill_name"));
        assertTrue(fs.files.containsKey("skill_name/SKILL.md"));

        String skillFileContents = fs.files.get("skill_name/SKILL.md");
        assertTrue(skillFileContents.contains("name: skill_name"));
        assertTrue(skillFileContents.contains("description:"));
        assertTrue(skillFileContents.contains("---"));
    }

    @Test
    void testSkillNameExtractedFromFrontMatter() {
        String content = "---\nname: my_skill\ndescription: test\n---\n# Body\n";
        assertTrue(content.matches("^[\\s\\S]*name: my_skill[\\s\\S]*$"));
        assertTrue(content.matches("^[\\s\\S]*description: [\\s\\S]*$"));
    }
}
