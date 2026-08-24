/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's evaluator-pipeline skill manager tests in
 * {@code tests/unit_tests/agent_evolving/evaluator/evaluator_pipeline/test_skill_manager.py}.
 */
class SkillManagerTest {

    @Test
    void testInitDefaultSkillRoot() {
        PipelineConfig config = new PipelineConfig();
        SkillManager manager = new SkillManager(config);
        Path expectedRoot = Path.of(System.getProperty("user.home"), ".jiuwenswarm", "agent", "workspace", "skills");
        assertEquals(expectedRoot, manager.getSkillRoot());
    }

    @Test
    void testInitCustomSkillRoot() {
        PipelineConfig config = configWithSkillRoot(Path.of("/custom/skills"));
        SkillManager manager = new SkillManager(config);
        assertEquals(Path.of("/custom/skills"), manager.getSkillRoot());
    }

    @Test
    void testInitEmptyState() {
        SkillManager manager = new SkillManager(new PipelineConfig());
        assertNull(manager.getCurrentSkill());
        assertNull(manager.getCurrentEvolutions());
        assertEquals(Map.of(), manager.getAllSkills());
        assertEquals(Map.of(), manager.getAllEvolutions());
        assertEquals(Map.of(), manager.getAllEvolutionFiles());
    }

    @Test
    void testInitForTaskCreatesDir(@TempDir Path tempDir) {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        manager.initForTask("test_task");

        assertEquals(tempDir.resolve("test_task"), manager.getSkillDir());
        assertTrue(Files.exists(manager.getSkillDir()));
    }

    @Test
    void testInitForTaskLoadsResolvedName(@TempDir Path tempDir) throws Exception {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        Path taskDir = tempDir.resolve("test_task");
        Files.createDirectories(taskDir);
        Files.writeString(taskDir.resolve(".resolved_skill_name"), "custom_skill_name");

        manager.initForTask("test_task");
        assertEquals("custom_skill_name", manager.getResolvedSkillName());
    }

    @Test
    void testInitForTaskDefaultName(@TempDir Path tempDir) {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        manager.initForTask("test_task");
        assertEquals("test_task", manager.getResolvedSkillName());
    }

    @Test
    void testSaveResolvedSkillName(@TempDir Path tempDir) throws Exception {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        manager.initForTask("test_task");
        manager.setResolvedSkillName("my_skill");

        manager.saveResolvedSkillName();

        Path nameFile = tempDir.resolve("test_task").resolve(".resolved_skill_name");
        assertTrue(Files.exists(nameFile));
        assertEquals("my_skill", Files.readString(nameFile));
    }

    @Test
    void testLoadAllSkillsEmptyDir(@TempDir Path tempDir) {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        manager.initForTask("test_task");

        Map<String, String> skills = manager.loadAllSkills(false);
        assertEquals(Map.of(), skills);
    }

    @Test
    void testLoadAllSkillsWithSkills(@TempDir Path tempDir) throws Exception {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        manager.initForTask("test_task");

        Path skillDir = tempDir.resolve("test_task").resolve("my_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "# My Skill\nContent");

        Map<String, String> skills = manager.loadAllSkills(false);
        assertEquals("# My Skill\nContent", skills.get("my_skill"));
    }

    @Test
    void testLoadAllSkillsWithEvolutions(@TempDir Path tempDir) throws Exception {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        manager.initForTask("test_task");

        Path skillDir = tempDir.resolve("test_task").resolve("my_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "# Skill");
        Files.writeString(skillDir.resolve("evolutions.json"), "{\"entries\": []}");

        manager.loadAllSkills(false);
        assertEquals("{\"entries\": []}", manager.getAllEvolutions().get("my_skill"));
    }

    @Test
    void testSaveAllSkillsCreatesFiles(@TempDir Path tempDir) throws Exception {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        manager.initForTask("test_task");

        var savedPaths = manager.saveAllSkills(Map.of("skill1", "# Skill1\nContent"), 1);

        assertEquals(1, savedPaths.size());
        Path skillPath = tempDir.resolve("test_task").resolve("skill1").resolve("iteration_001").resolve("SKILL.md");
        assertTrue(Files.exists(skillPath));
        assertEquals("# Skill1\nContent", Files.readString(skillPath));
    }

    @Test
    void testSaveAllSkillsCreatesLatestCopy(@TempDir Path tempDir) throws Exception {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        manager.initForTask("test_task");

        manager.saveAllSkills(Map.of("skill1", "# Skill1"), 1);

        Path latestPath = tempDir.resolve("test_task").resolve("skill1").resolve("latest").resolve("SKILL.md");
        assertTrue(Files.exists(latestPath));
        assertEquals("# Skill1", Files.readString(latestPath));
    }

    @Test
    void testSaveAllSkillsWithEvolutions(@TempDir Path tempDir) {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        manager.initForTask("test_task");

        manager.saveAllSkills(
                Map.of("skill1", "# Skill1"),
                1,
                Map.of("skill1", "{\"entries\": [{\"id\": \"ev001\"}]}"),
                Map.of());

        Path evolutionPath = tempDir.resolve("test_task").resolve("skill1").resolve("evolutions.json");
        assertTrue(Files.exists(evolutionPath));
    }

    @Test
    void testMergeEvolutionsEmptyExisting(@TempDir Path tempDir) throws Exception {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        manager.initForTask("test_task");

        String merged = manager.mergeEvolutionsForSkill("skill1", "{\"entries\": [{\"id\": \"ev001\", \"content\": \"test\"}]}");

        assertTrue(merged.contains("\"id\" : \"ev001\""));
    }

    @Test
    void testMergeEvolutionsWithExisting(@TempDir Path tempDir) throws Exception {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        manager.initForTask("test_task");

        Path skillDir = tempDir.resolve("test_task").resolve("skill1");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("evolutions.json"),
                "{\n  \"entries\": [{\"id\": \"ev001\", \"content\": \"original\"}],\n  \"skill_id\": \"skill1\"\n}");

        String merged = manager.mergeEvolutionsForSkill(
                "skill1",
                "{\n  \"entries\": [{\"id\": \"ev001\", \"content\": \"updated\"}, {\"id\": \"ev002\", \"content\": \"new\"}]\n}");

        assertTrue(merged.contains("\"content\" : \"updated\""));
        assertTrue(merged.contains("\"id\" : \"ev002\""));
    }

    @Test
    void testGetSkillDirPathLatest(@TempDir Path tempDir) {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        manager.initForTask("test_task");

        Path path = manager.getSkillDirPath("skill1");
        assertEquals(tempDir.resolve("test_task").resolve("skill1").resolve("latest"), path);
    }

    @Test
    void testGetSkillDirPathWithIteration(@TempDir Path tempDir) {
        SkillManager manager = new SkillManager(configWithSkillRoot(tempDir));
        manager.initForTask("test_task");

        Path path = manager.getSkillDirPath("skill1", 5);
        assertEquals(tempDir.resolve("test_task").resolve("skill1").resolve("iteration_005"), path);
    }

    private static PipelineConfig configWithSkillRoot(Path skillRoot) {
        PipelineConfig config = new PipelineConfig();
        config.setAgentConfig(Map.of("skill_persistence_dir", skillRoot.toString()));
        return config;
    }
}
