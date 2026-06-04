/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.skill;

import com.openjiuwen.dev_tools.skill_creator.SkillCreator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Mirrors Python's {@code test_skill_creator.py}.
 */
class SkillCreatorTest {

    static class MockFS {
        final List<String> directories = new ArrayList<>();
        final Map<String, String> files = new LinkedHashMap<>();

        void addDirectory(String dirName) {
            directories.add(dirName);
        }

        void addFile(String filePath, String fileContents) {
            files.put(filePath, fileContents);
        }
    }

    static class MockReActAgent {
        final MockFS fs = new MockFS();

        void invoke() {
            fs.addDirectory("skill_name");
            fs.addFile("skill_name/SKILL.md",
                    "---\nname: skill_name\ndescription: sample skill\n---\n# Skill Body\n");
        }
    }

    static final String RUN_REAL_LLM_TESTS = System.getenv().getOrDefault("RUN_REAL_LLM_TESTS", "0");

    @Test
    void testSkillCreationRealLlm(@TempDir Path outputDir) throws Exception {
        assumeTrue("1".equals(RUN_REAL_LLM_TESTS), "Real LLM test skipped. Set RUN_REAL_LLM_TESTS=1 to enable.");

        SkillCreator skillCreator = new SkillCreator();
        skillCreator.createAgent().join();
        skillCreator.generate("Create a skeleton skill directory nammed 'skill_name'", outputDir).join();

        Path skillDir = outputDir.resolve("skill_name");
        assertTrue(Files.isDirectory(skillDir));

        Path skillFile = skillDir.resolve("SKILL.md");
        assertTrue(Files.exists(skillFile));
        String skillFileContents = Files.readString(skillFile);
        assertTrue(skillFileContents.matches("(?s)^---.*name: skill_name.*---.*$"));
        assertTrue(skillFileContents.matches("(?s)^---.*description: .*---.*$"));
    }

    @Test
    void testSkillCreationMockLlm() {
        MockReActAgent agent = new MockReActAgent();
        agent.invoke();
        MockFS fs = agent.fs;

        assertTrue(fs.directories.contains("skill_name"));
        assertTrue(fs.files.containsKey("skill_name/SKILL.md"));

        String skillFileContents = fs.files.get("skill_name/SKILL.md");
        assertTrue(skillFileContents.matches("(?s)^---.*name: skill_name.*---.*$"));
        assertTrue(skillFileContents.matches("(?s)^---.*description: .*---.*$"));
    }

    @Test
    void testSkillNameExtractedFromFrontMatter() {
        String content = "---\nname: my_skill\ndescription: test\n---\n# Body\n";
        assertTrue(content.matches("(?s)^---.*name: my_skill.*---.*$"));
        assertTrue(content.matches("(?s)^---.*description: .*---.*$"));
    }
}
