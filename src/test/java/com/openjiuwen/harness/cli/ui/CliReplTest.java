/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema;
import com.openjiuwen.harness.cli.rails.ToolTrackingRail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code openjiuwen.harness.cli.ui.repl} in
 * {@code openjiuwen/harness/cli/ui/repl.py}.
 */
class CliReplTest {

    @TempDir
    private Path tempDir;

    @Test
    void autoHarnessRunDefaultsToMetaPipelineAndBuildsTask(@TempDir Path workspace) throws IOException {
        Path repo = makeFakeRepo(workspace, "agent-core");
        Path cwd = Files.createDirectories(workspace.resolve("cwd"));

        CliRepl.PreparedRun prepared = new CliRepl().subcmdRun(
                List.of("--task", "improve repl", "--goal", "ship change", "--budget", "42", "--no-push"),
                workspace.toString(),
                cwd
        );

        assertThat(prepared.tasks()).hasSize(1);
        assertThat(prepared.tasks().get(0).getTopic()).isEqualTo("improve repl");
        assertThat(prepared.config().getOptimizationGoal()).isEqualTo("ship change");
        assertThat(prepared.config().getSessionBudgetSecs()).isEqualTo(42.0);
        assertThat(prepared.config().getGitRemote()).isEmpty();
        assertThat(prepared.config().getPipelinePreference())
                .isEqualTo(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
        assertThat(prepared.config().getLocalRepo())
                .isEqualTo(repo.toAbsolutePath().normalize().toString());
        assertThat(prepared.streamRails()).contains(ToolTrackingRail.class);
    }

    @Test
    void autoHarnessRunAcceptsOnlySupportedPipelineFlags() {
        assertThat(CliRepl.parseRunArgs(List.of("--pipeline", "extended")).pipeline()).isEqualTo("extended");
        assertThat(CliRepl.parseRunArgs(List.of("--pipeline", "auto")).pipeline()).isEqualTo("auto");
        assertThatThrownBy(() -> CliRepl.parseRunArgs(List.of("--pipeline", "custom")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--pipeline supports only");
    }

    @Test
    void explicitPipelineIsNormalizedThroughSchema(@TempDir Path workspace) throws IOException {
        makeFakeRepo(workspace, "agent-core");

        CliRepl.PreparedRun prepared = new CliRepl().subcmdRun(
                List.of("--pipeline", "extended", "--dry-run", "--task", "extension"),
                workspace.toString(),
                workspace
        );

        assertThat(prepared.dryRun()).isTrue();
        assertThat(prepared.pipelinePreference())
                .isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
        assertThat(prepared.dryRunPayload()).singleElement()
                .extracting(item -> item.get("topic"))
                .isEqualTo("extension");
    }

    @Test
    void gapAnalyzeRejectsLegacyCompetitorFlag() {
        assertThatThrownBy(() -> CliRepl.validateGapAnalyzeArgs(List.of("--competitor", "claude")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown argument: --competitor");
    }

    @Test
    void activateInteractionBuildsResumeMessageAndAcceptsDefaultChoice() {
        CliRepl.ActivationInteractionResult result = CliRepl.handleActivateInteraction(
                "iid-1",
                Map.of(
                        "interaction_type", "activate_confirm",
                        "extension_name", "sample_ext",
                        "runtime_path", "/tmp/ext",
                        "components_summary", Map.of("rails", 1, "tools", 2, "skills", 3)
                ),
                ""
        );

        assertThat(result.handled()).isTrue();
        assertThat(result.action()).isEqualTo("accept");
        assertThat(result.message())
                .containsEntry("interaction_id", "iid-1")
                .containsEntry("action", "accept");
        assertThat(result.displayLines()).anySatisfy(line -> assertThat(line).contains("1 rails, 2 tools, 3 skills"));
    }

    @Test
    void activateInteractionRejectsRChoiceAndIgnoresOtherInteractionTypes() {
        assertThat(CliRepl.handleActivateInteraction(
                "iid-1",
                Map.of("interaction_type", "activate_confirm"),
                "r"
        ).action()).isEqualTo("reject");
        assertThat(CliRepl.handleActivateInteraction(
                "iid-2",
                Map.of("interaction_type", "ask_user"),
                "a"
        ).handled()).isFalse();
    }

    @Test
    void helpRowsReflectCurrentPipelineAndGapAnalyzeSyntax() {
        List<String> commands = CliRepl.autoHarnessHelpRows().stream()
                .map(CliRepl.HelpRow::command)
                .toList();

        assertThat(commands.get(0)).contains("[--pipeline meta|extended|auto]");
        assertThat(commands).contains("/auto-harness gap-analyze");
        assertThat(commands).noneMatch(command -> command.contains("--competitor"));
    }

    @Test
    void skillScanningAndQueryMatchPythonFacade(@TempDir Path skillsRoot) throws IOException {
        Path skillDir = Files.createDirectories(skillsRoot.resolve("demo"));
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, """
                ---
                name: demo-skill
                description: Demo skill description
                ---
                Use the demo skill.
                """, StandardCharsets.UTF_8);

        Map<String, Path> skills = CliRepl.scanSkillDirs(List.of(skillsRoot));
        CliRepl.resetSkillCommands();
        CliRepl.registerSkillCommands(skills);

        assertThat(skills).containsEntry("demo-skill", skillMd);
        assertThat(CliRepl.readSkillDescription(skillMd)).isEqualTo("Demo skill description");
        assertThat(CliRepl.handleSlash("/demo-skill run it", new StringBuilder())).isEqualTo("/demo-skill");
        assertThat(CliRepl.buildSkillQuery(skillMd, "run it"))
                .contains("<skill-instructions>")
                .contains("User request: run it");
    }

    @Test
    void commandParsingKeepsQuotedGoal() {
        CliRepl.PreparedRun prepared = new CliRepl().cmdAutoHarness(
                "/auto-harness run --goal \"analyze gap\" --pipeline auto",
                tempDir.toString(),
                tempDir
        );

        assertThat(prepared.config().getOptimizationGoal()).isEqualTo("analyze gap");
        assertThat(prepared.pipelinePreference()).isEqualTo(AutoHarnessSchema.PIPELINE_PREFERENCE_AUTO);
    }

    private static Path makeFakeRepo(Path parent, String name) throws IOException {
        Path repo = Files.createDirectories(parent.resolve(name));
        Files.createDirectories(repo.resolve(".git"));
        Files.writeString(repo.resolve("pyproject.toml"), "[project]\nname='x'\n", StandardCharsets.UTF_8);
        Files.createDirectories(repo.resolve("openjiuwen"));
        return repo;
    }
}
