/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests.cli.unit.test_skill_slash_commands} in
 * {@code tests/cli/unit/test_skill_slash_commands.py}.</p>
 */
class SkillSlashCommandsMissingTest {

    @TempDir
    private Path tempDir;

    @TestFactory
    Collection<DynamicTest> pythonSkillSlashCommandCases() {
        return List.of(
                dynamic("TestScanSkillDirs::test_scan_returns_skills_from_existing_dir",
                        this::scanReturnsSkillsFromExistingDir),
                dynamic("TestScanSkillDirs::test_scan_skips_nonexistent_dirs",
                        this::scanSkipsNonexistentDirs),
                dynamic("TestScanSkillDirs::test_scan_dedup_by_priority",
                        this::scanDedupByPriority),
                dynamic("TestRegisterSkillCommands::test_registers_skill_in_slash_commands",
                        this::registersSkillInSlashCommands),
                dynamic("TestRegisterSkillCommands::test_does_not_shadow_builtin",
                        this::doesNotShadowBuiltin),
                dynamic("TestRegisterSkillCommands::test_description_extracted_from_yaml",
                        this::descriptionExtractedFromYaml),
                dynamic("TestReadSkillDescription::test_reads_description",
                        this::readsDescription),
                dynamic("TestReadSkillDescription::test_returns_empty_for_no_frontmatter",
                        this::returnsEmptyForNoFrontmatter),
                dynamic("TestBuildSkillQuery::test_query_includes_skill_content",
                        this::queryIncludesSkillContent),
                dynamic("TestBuildSkillQuery::test_query_without_args",
                        this::queryWithoutArgs),
                dynamic("TestBuildSkillQuery::test_query_with_missing_file",
                        this::queryWithMissingFile),
                dynamic("TestHandleSlashWithSkills::test_skill_command_returns_cmd_name",
                        this::skillCommandReturnsCommandName),
                dynamic("TestHandleSlashWithSkills::test_builtin_command_returns_none",
                        this::builtinCommandReturnsNone),
                dynamic("TestHandleSlashWithSkills::test_unknown_command_returns_none",
                        this::unknownCommandReturnsNone)
        );
    }

    private void scanReturnsSkillsFromExistingDir() throws IOException {
        Path root = caseDir("scan-existing");
        Path skillMd = writeSkill(root, "my-skill", "A test skill");

        Map<String, Path> result = CliRepl.scanSkillDirs(List.of(root));

        assertThat(result).containsEntry("my-skill", skillMd);
    }

    private void scanSkipsNonexistentDirs() {
        Map<String, Path> result = CliRepl.scanSkillDirs(List.of(tempDir.resolve("missing")));

        assertThat(result).isEmpty();
    }

    private void scanDedupByPriority() throws IOException {
        Path high = caseDir("scan-dedup").resolve("high");
        Path low = caseDir("scan-dedup").resolve("low");
        Path highSkill = writeSkill(high, "dup-skill", "High version");
        writeSkill(low, "dup-skill", "Low version");

        Map<String, Path> result = CliRepl.scanSkillDirs(List.of(high, low));

        assertThat(result).containsEntry("dup-skill", highSkill);
    }

    private void registersSkillInSlashCommands() throws IOException {
        Path skillMd = writeSkill(caseDir("register"), "test-sk", "Test skill");
        CliRepl.resetSkillCommands();
        try {
            CliRepl.registerSkillCommands(Map.of("test-sk", skillMd));

            assertThat(CliRepl.slashCommands()).containsEntry("/test-sk", "skill");
            assertThat(CliRepl.slashDescriptions()).containsKey("/test-sk");
            assertThat(CliRepl.skillCommands()).containsEntry("/test-sk", skillMd);
        } finally {
            CliRepl.resetSkillCommands();
        }
    }

    private void doesNotShadowBuiltin() throws IOException {
        Path skillMd = writeSkill(caseDir("builtin"), "help", "Not the real help");
        String originalHandler = CliRepl.slashCommands().get("/help");
        CliRepl.resetSkillCommands();
        try {
            CliRepl.registerSkillCommands(Map.of("help", skillMd));

            assertThat(CliRepl.slashCommands()).containsEntry("/help", originalHandler);
        } finally {
            CliRepl.resetSkillCommands();
        }
    }

    private void descriptionExtractedFromYaml() throws IOException {
        Path skillMd = writeSkill(caseDir("desc-yaml"), "desc-sk", "My description");
        CliRepl.resetSkillCommands();
        try {
            CliRepl.registerSkillCommands(Map.of("desc-sk", skillMd));

            assertThat(CliRepl.slashDescriptions()).containsEntry("/desc-sk", "My description");
        } finally {
            CliRepl.resetSkillCommands();
        }
    }

    private void readsDescription() throws IOException {
        Path skillMd = writeSkill(caseDir("read-desc"), "sk1", "Some description");

        assertThat(CliRepl.readSkillDescription(skillMd)).isEqualTo("Some description");
    }

    private void returnsEmptyForNoFrontmatter() throws IOException {
        Path skillMd = caseDir("no-frontmatter").resolve("no-fm").resolve("SKILL.md");
        Files.createDirectories(skillMd.getParent());
        Files.writeString(skillMd, "# No front matter\n", StandardCharsets.UTF_8);

        assertThat(CliRepl.readSkillDescription(skillMd)).isEmpty();
    }

    private void queryIncludesSkillContent() throws IOException {
        Path skillMd = writeSkill(caseDir("query-content"), "q-sk", "Query skill");
        String query = CliRepl.buildSkillQuery(skillMd, "do stuff");

        assertThat(query)
                .contains("<skill-instructions>")
                .contains("</skill-instructions>")
                .contains("Query skill")
                .contains("do stuff");
    }

    private void queryWithoutArgs() throws IOException {
        Path skillMd = writeSkill(caseDir("query-no-args"), "q-sk2", "No args skill");
        String query = CliRepl.buildSkillQuery(skillMd, "");

        assertThat(query.toLowerCase()).contains("follow the skill instructions");
    }

    private void queryWithMissingFile() {
        Path missing = tempDir.resolve("missing").resolve("SKILL.md");
        String query = CliRepl.buildSkillQuery(missing, "args");

        assertThat(query).contains("Error reading skill");
    }

    private void skillCommandReturnsCommandName() throws IOException {
        Path skillMd = writeSkill(caseDir("handle-skill"), "my-sk", "My skill");
        CliRepl.resetSkillCommands();
        try {
            CliRepl.registerSkillCommands(Map.of("my-sk", skillMd));

            assertThat(CliRepl.handleSlash("/my-sk do something", new StringBuilder())).isEqualTo("/my-sk");
        } finally {
            CliRepl.resetSkillCommands();
        }
    }

    private void builtinCommandReturnsNone() {
        StringBuilder output = new StringBuilder();

        String result = CliRepl.handleSlash("/help", output);

        assertThat(result).isNull();
    }

    private void unknownCommandReturnsNone() {
        StringBuilder output = new StringBuilder();

        String result = CliRepl.handleSlash("/totally_unknown_xyz", output);

        assertThat(result).isNull();
        assertThat(output.toString()).contains("Unknown command");
    }

    private Path writeSkill(Path root, String name, String description) throws IOException {
        Path skillDir = root.resolve(name);
        Files.createDirectories(skillDir);
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(
                skillMd,
                "---\n"
                        + "description: " + description + "\n"
                        + "---\n\n"
                        + "# " + name + "\n\n"
                        + "Instructions for " + name + ".\n",
                StandardCharsets.UTF_8
        );
        return skillMd;
    }

    private Path caseDir(String name) throws IOException {
        return Files.createDirectories(tempDir.resolve(name));
    }

    private static DynamicTest dynamic(String name, Executable executable) {
        return DynamicTest.dynamicTest(name, executable);
    }
}
