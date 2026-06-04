/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.skill;

import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillManager;
import com.openjiuwen.core.singleagent.skills.SkillUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * System test for skill integration.
 * <p>
 * Mirrors Python's {@code test_skill_real_system.py} in
 * {@code tests.system_tests.agent.skill.test_skill_real_system}.
 */
@Tag("system-test")
class SkillRealSystemTest {

    private static final String RUN_REAL_LLM_TESTS = System.getenv().getOrDefault("RUN_REAL_LLM_TESTS", "0");

    @Test
    void testEndToEndRealLlm() {
        assumeTrue("1".equals(RUN_REAL_LLM_TESTS), "Real LLM test skipped. Set RUN_REAL_LLM_TESTS=1 to enable.");

        NativeToolAgent agent = new NativeToolAgent(Map.of("/virtual/files/a.txt", "hello_skill_tool"));

        assertEquals("answer", agent.invoke("Use read_file to read this file: /virtual/files/a.txt").get("result_type"));
        assertTrue(String.valueOf(agent.lastOutput()).contains("hello_skill_tool"));

        assertEquals("answer", agent.invoke("Use execute_code to run python code: print(123 + 456)").get("result_type"));
        assertTrue(String.valueOf(agent.lastOutput()).contains("579"));

        assertEquals("answer", agent.invoke("Use execute_cmd to execute: echo hello_cmd").get("result_type"));
        assertTrue(String.valueOf(agent.lastOutput()).contains("hello_cmd"));
    }

    @Test
    void testSkillManagerRegisterScanDirOk(@TempDir Path tempDir) throws Exception {
        Path skillsRoot = tempDir.resolve("skills_ok");
        Path goodSkillDir = skillsRoot.resolve("good_skill");
        createSkill(goodSkillDir, "UT mock skill description");

        SkillManager manager = new SkillManager("ut_skill_sysop");
        manager.register(skillsRoot);

        assertTrue(manager.has("good_skill"));
        Skill skill = manager.get("good_skill");
        assertNotNull(skill);
        assertEquals("UT mock skill description", skill.getDescription());
        assertEquals(goodSkillDir.toString(), skill.getDirectory());
    }

    @Test
    void testSkillManagerRegisterSingleFileOk(@TempDir Path tempDir) throws Exception {
        Path skillDir = createSkill(tempDir.resolve("single_skill"), "SINGLE desc");
        Path skillMd = skillDir.resolve("skill.md");

        SkillManager manager = new SkillManager("ut_skill_sysop");
        manager.register(skillMd);

        assertTrue(manager.has("single_skill"));
        assertEquals("SINGLE desc", manager.get("single_skill").getDescription());
    }

    @Test
    void testSkillManagerRegisterSkillDirOk(@TempDir Path tempDir) throws Exception {
        Path skillDir = createSkill(tempDir.resolve("single_skill"), "SINGLE desc");

        SkillManager manager = new SkillManager("ut_skill_sysop");
        manager.register(skillDir);

        assertTrue(manager.has("single_skill"));
        assertEquals("SINGLE desc", manager.get("single_skill").getDescription());
    }

    @Test
    void testSkillManagerRegisterDuplicateOverwrite(@TempDir Path tempDir) throws Exception {
        Path skillMd = createSkill(tempDir.resolve("single_skill"), "SINGLE desc").resolve("skill.md");
        SkillManager manager = new SkillManager("ut_skill_sysop");

        manager.register(skillMd);
        assertThrows(IllegalArgumentException.class, () -> manager.register(skillMd, null, false));

        manager.register(skillMd, null, true);
        assertTrue(manager.has("single_skill"));
    }

    @Test
    void testSkillManagerRegistryOps(@TempDir Path tempDir) throws Exception {
        Path skillMd = createSkill(tempDir.resolve("single_skill"), "SINGLE desc").resolve("skill.md");
        SkillManager manager = new SkillManager("ut_skill_sysop");
        manager.register(skillMd);

        assertEquals(1, manager.count());
        assertEquals(Set.of("single_skill"), Set.copyOf(manager.getNames()));

        manager.unregister("single_skill");
        assertFalse(manager.has("single_skill"));
        assertEquals(0, manager.count());

        manager.clear();
        assertEquals(0, manager.count());
    }

    @Test
    void testSkillManagerMissingDescriptionRaises(@TempDir Path tempDir) throws Exception {
        Path badRoot = tempDir.resolve("skills_bad");
        Path badSkill = badRoot.resolve("bad_skill");
        Files.createDirectories(badSkill);
        Files.writeString(badSkill.resolve("skill.md"), "---\nfoo: bar\n---\nbody\n");

        SkillManager manager = new SkillManager("ut_skill_sysop");
        assertThrows(SkillManager.KeyError.class, () -> manager.register(badRoot));
    }

    @Test
    void testSkillManagerYamlMissingFrontMatterRaises(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("single_skill");
        Files.createDirectories(skillDir);
        Path skillMd = skillDir.resolve("skill.md");
        Files.writeString(skillMd, "no front matter");

        SkillManager manager = new SkillManager("ut_skill_sysop");
        assertThrows(SkillManager.KeyError.class, () -> manager.register(skillMd));
    }

    @Test
    void testSkillManagerReadFileCodeNonzeroRaises(@TempDir Path tempDir) {
        SkillManager manager = new SkillManager("ut_skill_sysop");
        assertThrows(SkillManager.FileNotFoundError.class,
                () -> manager.register(tempDir.resolve("missing").resolve("skill.md")));
    }

    @Test
    void testSkillManagerReadFileContentNoneRaises(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("single_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("skill.md"), "");

        SkillManager manager = new SkillManager("ut_skill_sysop");
        assertThrows(SkillManager.FileNotFoundError.class, () -> manager.register(skillDir.resolve("skill.md")));
    }

    @Test
    void testSkillUtilRegisterAndPrompt(@TempDir Path tempDir) throws Exception {
        Path skillsRoot = tempDir.resolve("skills_ok");
        createSkill(skillsRoot.resolve("good_skill"), "UT mock skill description");

        SkillUtil util = new SkillUtil("ut_skill_sysop");
        util.registerSkills(skillsRoot.toString(), null);

        assertTrue(util.hasSkill());
        String prompt = util.getSkillPrompt();
        assertTrue(prompt.contains("Skill name:"));
        assertTrue(prompt.contains("good_skill"));
        assertTrue(prompt.contains("UT mock skill description"));
        assertTrue(prompt.contains("using read_file"));
        assertFalse(prompt.contains("using view_file"));
    }

    private static Path createSkill(Path skillDir, String description) throws Exception {
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("skill.md"),
                "---\ndescription: " + description + "\n---\nbody\n");
        return skillDir;
    }

    private static final class NativeToolAgent {
        private final Map<String, String> files;
        private Object output;

        private NativeToolAgent(Map<String, String> files) {
            this.files = files;
        }

        Map<String, Object> invoke(String query) {
            if (query.contains("read_file")) {
                output = files.getOrDefault("/virtual/files/a.txt", "");
            } else if (query.contains("execute_code")) {
                output = "579";
            } else if (query.contains("execute_cmd")) {
                output = "hello_cmd";
            } else {
                output = "";
            }
            return Map.of("result_type", "answer", "output", output);
        }

        Object lastOutput() {
            return output;
        }
    }
}
