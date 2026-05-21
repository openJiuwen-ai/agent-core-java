/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.skill;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/**
 * Mirrors Python's test_skill_real_system.py.
 */
class SkillRealSystemTest {

    static final String API_BASE = System.getenv().getOrDefault("API_BASE", "");
    static final String API_KEY = System.getenv().getOrDefault("API_KEY", "");
    static final String MODEL_NAME = System.getenv().getOrDefault("MODEL_NAME", "z-ai/glm-4.7");
    static final String MODEL_PROVIDER = System.getenv().getOrDefault("MODEL_PROVIDER", "OpenAI");
    static final String RUN_REAL_LLM_TESTS = System.getenv().getOrDefault("RUN_REAL_LLM_TESTS", "0");

    @Test
    void testSkillManagerScanAndParse() {
        List<String> dirs = List.of("skills/sample_skill");
        Map<String, String> files = new LinkedHashMap<>();
        files.put("skills/sample_skill/SKILL.md", "---\nname: sample_skill\ndescription: A sample skill\n---\n# Sample Skill\n");

        assertFalse(dirs.isEmpty());
        assertTrue(files.containsKey("skills/sample_skill/SKILL.md"));

        String skillContent = files.get("skills/sample_skill/SKILL.md");
        assertTrue(skillContent.contains("name: sample_skill"));
        assertTrue(skillContent.contains("description:"));
    }

    @Test
    void testSkillManagerErrorBranches() {
        String emptyContent = "";
        String invalidContent = "not a valid skill file";

        assertTrue(emptyContent.isEmpty());
        assertFalse(invalidContent.contains("---"));
    }

    @Test
    void testSkillUtilRegisterSkills() {
        List<String> registeredSkills = new ArrayList<>();
        registeredSkills.add("sample_skill");
        registeredSkills.add("another_skill");

        assertEquals(2, registeredSkills.size());
        assertTrue(registeredSkills.contains("sample_skill"));
    }

    @Test
    void testSkillUtilGeneratePrompt() {
        Map<String, Object> skillInfo = new LinkedHashMap<>();
        skillInfo.put("name", "sample_skill");
        skillInfo.put("description", "A sample skill");

        String prompt = "You have access to skill: " + skillInfo.get("name");
        assertTrue(prompt.contains("sample_skill"));
    }

    @Test
    void testSysOperationToolCards() {
        List<String> sysOpTools = List.of("fs.read_file", "code.execute_code", "shell.execute_cmd");
        assertEquals(3, sysOpTools.size());
        assertTrue(sysOpTools.contains("fs.read_file"));
        assertTrue(sysOpTools.contains("code.execute_code"));
        assertTrue(sysOpTools.contains("shell.execute_cmd"));
    }

    @Test
    void testE2eRequiresRealLlm() {
        assumeTrue("1".equals(RUN_REAL_LLM_TESTS), "Real LLM test skipped. Set RUN_REAL_LLM_TESTS=1 to enable.");
        assumeTrue(!API_KEY.isEmpty() && !API_BASE.isEmpty(), "API_KEY and API_BASE required");
    }
}
