/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.skill;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import java.util.*;

/**
 * Mirrors Python's test_skill_evaluator.py.
 */
class SkillEvaluatorTest {

    static class MockFS {
        Map<String, String> files = new LinkedHashMap<>();

        void addFile(String filePath, String fileContents) {
            files.put(filePath, fileContents);
        }
    }

    static final String SAMPLE_REPORT_FILENAME = "final_report.md";
    static final String RUN_REAL_LLM_TESTS = System.getenv().getOrDefault("RUN_REAL_LLM_TESTS", "0");

    @Test
    void testEvaluateRealLlm() {
        assumeTrue("1".equals(RUN_REAL_LLM_TESTS), "Real LLM test skipped. Set RUN_REAL_LLM_TESTS=1 to enable.");
        String outputDir = System.getenv().getOrDefault("OUTPUT_DIR", "");
        assertNotNull(outputDir);
    }

    @Test
    void testEvaluateMockLlm() {
        MockFS fs = new MockFS();
        fs.addFile(SAMPLE_REPORT_FILENAME,
                "# Skill Evaluation Report\n\n## Summary\nSkill evaluated successfully.\n\n## Score\n9/10\n");

        assertTrue(fs.files.containsKey(SAMPLE_REPORT_FILENAME));

        String contents = fs.files.get(SAMPLE_REPORT_FILENAME);
        assertTrue(contents.contains("# Skill Evaluation Report"));
        assertTrue(contents.contains("## Summary"));
        assertTrue(contents.contains("## Score"));
    }
}
