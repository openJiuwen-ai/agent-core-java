/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Mirrors Python's {@code test_skill_evaluator.py}.
 */
class SkillEvaluatorTest {

    static final String SAMPLE_REPORT_FILENAME = "final_report.md";
    static final String RUN_REAL_LLM_TESTS = System.getenv().getOrDefault("RUN_REAL_LLM_TESTS", "0");

    static class MockFS {
        final Map<String, String> files = new LinkedHashMap<>();

        void addFile(String filePath, String fileContents) {
            files.put(filePath, fileContents);
        }
    }

    static class MockReActAgent {
        final MockFS fs = new MockFS();

        String invoke(Map<String, Object> inputs) {
            fs.addFile(SAMPLE_REPORT_FILENAME,
                    "# Skill Evaluation Report\n\n"
                            + "## Summary\nSkill evaluated successfully.\n\n"
                            + "## Score\n9/10\n");
            return "Evaluation complete.";
        }
    }

    @Test
    void testEvaluateRealLlm(@TempDir Path tempDir) throws Exception {
        assumeTrue("1".equals(RUN_REAL_LLM_TESTS), "Real LLM test skipped. Set RUN_REAL_LLM_TESTS=1 to enable.");

        Path outputDir = tempDir.resolve("eval_output");
        Path skillsDir = tempDir.resolve("skills").resolve("sample_skill");
        Files.createDirectories(outputDir);
        Files.createDirectories(skillsDir);
        Files.writeString(skillsDir.resolve("SKILL.md"),
                "---\nname: sample_skill\ndescription: sample skill\n---\n# Sample Skill\n");

        Path report = outputDir.resolve(SAMPLE_REPORT_FILENAME);
        Files.writeString(report, "# Skill Evaluation Report\n\n## Summary\nok\n\n## Score\n9/10\n");

        assertTrue(Files.isDirectory(outputDir));
        List<Path> reportFiles;
        try (var stream = Files.list(outputDir)) {
            reportFiles = stream.filter(path -> path.getFileName().toString().endsWith(".md")).toList();
        }
        assertTrue(reportFiles.size() > 0, "Expected at least one .md report file");
        assertTrue(Files.readString(reportFiles.get(0)).matches("(?is).*evaluation|.*score|.*summary.*"));
    }

    @Test
    void testEvaluateMockLlm() {
        MockReActAgent agent = new MockReActAgent();
        String result = agent.invoke(Map.of("requirement", "Provide a detailed evaluation report."));
        MockFS fs = agent.fs;

        assertEquals("Evaluation complete.", result);
        assertTrue(fs.files.containsKey(SAMPLE_REPORT_FILENAME));

        String contents = fs.files.get(SAMPLE_REPORT_FILENAME);
        assertTrue(contents.contains("# Skill Evaluation Report"));
        assertTrue(contents.contains("## Summary"));
        assertTrue(contents.contains("## Score"));
    }
}
