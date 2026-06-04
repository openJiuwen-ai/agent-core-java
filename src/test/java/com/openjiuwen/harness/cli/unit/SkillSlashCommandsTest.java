/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.ui.CliRepl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for dynamic skill slash command registration.
 * <p>
 * Mirrors Python's {@code test_skill_slash_commands} in
 * {@code tests.cli.unit.test_skill_slash_commands}.
 */
class SkillSlashCommandsTest {

    @AfterEach
    void resetSkills() {
        CliRepl.resetSkillCommands();
    }

    @Nested
    class TestScanSkillDirs {
        @Test
        void scanReturnsSkillsFromExistingDir(@TempDir Path tempDir) throws IOException {
            Path skillMd = writeSkill(tempDir, "my-skill", "A test skill");

            Map<String, Path> result = CliRepl.scanSkillDirs(List.of(tempDir));

            assertTrue(result.containsKey("my-skill"));
            assertEquals(skillMd, result.get("my-skill"));
        }

        @Test
        void scanSkipsNonexistentDirs(@TempDir Path tempDir) {
            Map<String, Path> result = CliRepl.scanSkillDirs(List.of(tempDir.resolve("missing")));
            assertTrue(result.isEmpty());
        }

        @Test
        void scanDedupByPriority(@TempDir Path tempDir) throws IOException {
            Path high = tempDir.resolve("high");
            Path low = tempDir.resolve("low");
            writeSkill(high, "dup-skill", "High version");
            writeSkill(low, "dup-skill", "Low version");

            Map<String, Path> result = CliRepl.scanSkillDirs(List.of(high, low));

            assertTrue(result.containsKey("dup-skill"));
            assertTrue(result.get("dup-skill").startsWith(high));
        }
    }

    @Nested
    class TestRegisterSkillCommands {
        @Test
        void registersSkillInSlashCommands(@TempDir Path tempDir) throws IOException {
            Path skillMd = writeSkill(tempDir, "test-sk", "Test skill");

            CliRepl.registerSkillCommands(Map.of("test-sk", skillMd));

            assertTrue(CliRepl.slashCommands().containsKey("/test-sk"));
            assertTrue(CliRepl.slashDescriptions().containsKey("/test-sk"));
            assertTrue(CliRepl.skillCommands().containsKey("/test-sk"));
        }

        @Test
        void doesNotShadowBuiltin(@TempDir Path tempDir) throws IOException {
            Path skillMd = writeSkill(tempDir, "help", "Not the real help");
            String originalHandler = CliRepl.slashCommands().get("/help");

            CliRepl.registerSkillCommands(Map.of("help", skillMd));

            assertEquals(originalHandler, CliRepl.slashCommands().get("/help"));
        }

        @Test
        void descriptionExtractedFromYaml(@TempDir Path tempDir) throws IOException {
            Path skillMd = writeSkill(tempDir, "desc-sk", "My description");

            CliRepl.registerSkillCommands(Map.of("desc-sk", skillMd));

            assertEquals("My description", CliRepl.slashDescriptions().get("/desc-sk"));
        }
    }

    @Nested
    class TestReadSkillDescription {
        @Test
        void readsDescription(@TempDir Path tempDir) throws IOException {
            Path skillMd = writeSkill(tempDir, "sk1", "Some description");
            assertEquals("Some description", CliRepl.readSkillDescription(skillMd));
        }

        @Test
        void returnsEmptyForNoFrontmatter(@TempDir Path tempDir) throws IOException {
            Path skillMd = tempDir.resolve("no-fm").resolve("SKILL.md");
            Files.createDirectories(skillMd.getParent());
            Files.writeString(skillMd, "# No front matter\n");
            assertEquals("", CliRepl.readSkillDescription(skillMd));
        }
    }

    @Nested
    class TestBuildSkillQuery {
        @Test
        void queryIncludesSkillContent(@TempDir Path tempDir) throws IOException {
            Path skillMd = writeSkill(tempDir, "q-sk", "Query skill");

            String query = CliRepl.buildSkillQuery(skillMd, "do stuff");

            assertTrue(query.contains("<skill-instructions>"));
            assertTrue(query.contains("</skill-instructions>"));
            assertTrue(query.contains("Query skill"));
            assertTrue(query.contains("do stuff"));
        }

        @Test
        void queryWithoutArgs(@TempDir Path tempDir) throws IOException {
            Path skillMd = writeSkill(tempDir, "q-sk2", "No args skill");

            String query = CliRepl.buildSkillQuery(skillMd, "");

            assertTrue(query.toLowerCase().contains("follow the skill instructions"));
        }

        @Test
        void queryWithMissingFile(@TempDir Path tempDir) {
            Path missing = tempDir.resolve("missing").resolve("SKILL.md");
            String query = CliRepl.buildSkillQuery(missing, "args");
            assertTrue(query.contains("Error reading skill"));
        }
    }

    @Nested
    class TestHandleSlashWithSkills {
        @Test
        void skillCommandReturnsCmdName(@TempDir Path tempDir) throws IOException {
            Path skillMd = writeSkill(tempDir, "my-sk", "My skill");
            CliRepl.registerSkillCommands(Map.of("my-sk", skillMd));
            StringBuilder output = new StringBuilder();

            String result = CliRepl.handleSlash("/my-sk do something", output);

            assertEquals("/my-sk", result);
        }

        @Test
        void builtinCommandReturnsNone() {
            StringBuilder output = new StringBuilder();
            String result = CliRepl.handleSlash("/help", output);
            assertNull(result);
        }

        @Test
        void unknownCommandReturnsNone() {
            StringBuilder output = new StringBuilder();

            String result = CliRepl.handleSlash("/totally_unknown_xyz", output);

            assertNull(result);
            assertTrue(output.toString().contains("Unknown command"));
        }
    }

    private static Path writeSkill(Path root, String name, String description) throws IOException {
        Path skillDir = root.resolve(name);
        Files.createDirectories(skillDir);
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd,
                "---\n"
                        + "description: " + description + "\n"
                        + "---\n\n"
                        + "# " + name + "\n\n"
                        + "Instructions for " + name + ".\n");
        return skillMd;
    }
}
