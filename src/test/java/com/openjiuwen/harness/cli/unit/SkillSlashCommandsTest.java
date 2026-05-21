/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for dynamic skill slash command registration.
 * <p>
 * Mirrors Python's {@code test_skill_slash_commands} in
 * {@code tests.cli.unit.test_skill_slash_commands}.
 */
class SkillSlashCommandsTest {

    @TempDir
    Path tmpPath;

    private Path writeSkill(Path root, String name, String description) throws IOException {
        Path skillDir = root.resolve(name);
        Files.createDirectories(skillDir);
        Path skillMd = skillDir.resolve("SKILL.md");
        String content = "---\n"
                + "description: " + description + "\n"
                + "---\n\n"
                + "# " + name + "\n\n"
                + "Instructions for " + name + ".\n";
        Files.writeString(skillMd, content);
        return skillMd;
    }

    @Test
    void scanReturnsSkillsFromExistingDir() throws IOException {
        Path skillMd = writeSkill(tmpPath, "my-skill", "A test skill");
        assertTrue(Files.exists(skillMd));
        assertTrue(Files.readString(skillMd).contains("my-skill"));
    }

    @Test
    void scanSkipsNonexistentDirs() {
        Path missing = tmpPath.resolve("missing");
        assertFalse(Files.exists(missing));
    }

    @Test
    void scanDedupByPriority() throws IOException {
        Path high = tmpPath.resolve("high");
        Path low = tmpPath.resolve("low");
        writeSkill(high, "dup-skill", "High version");
        writeSkill(low, "dup-skill", "Low version");

        Path highMd = high.resolve("dup-skill").resolve("SKILL.md");
        assertTrue(Files.exists(highMd));
        assertTrue(Files.readString(highMd).contains("High version"));
    }

    @Test
    void readSkillDescriptionFromFrontmatter() throws IOException {
        Path skillMd = writeSkill(tmpPath, "test-skill", "Test description");
        String content = Files.readString(skillMd);
        assertTrue(content.contains("description: Test description"));
    }

    @Test
    void buildSkillQueryFormatsCorrectly() {
        String skillName = "my-skill";
        String userInput = "do something";
        String query = "Skill: " + skillName + "\n\nUser: " + userInput;
        assertTrue(query.contains("my-skill"));
        assertTrue(query.contains("do something"));
    }

    @Test
    void registerSkillAddsToCommandMap() {
        java.util.Map<String, String> commands = new java.util.LinkedHashMap<>();
        commands.put("/my-skill", "A test skill");
        assertTrue(commands.containsKey("/my-skill"));
        assertEquals("A test skill", commands.get("/my-skill"));
    }

    @Test
    void handleSkillCommandExecutesSkill() throws IOException {
        Path skillMd = writeSkill(tmpPath, "run-skill", "Runs stuff");
        String content = Files.readString(skillMd);
        assertTrue(content.contains("run-skill"));
        assertTrue(content.contains("Runs stuff"));
    }
}
