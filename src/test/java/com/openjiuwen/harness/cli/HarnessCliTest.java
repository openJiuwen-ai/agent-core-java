/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for Python's top-level CLI module.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.cli.cli} in
 * {@code openjiuwen/harness/cli/cli.py}.</p>
 *
 * <p>Mirrors Python's {@code TestAutoHarnessCli} in
 * {@code tests/unit_tests/cli/test_auto_harness_cli.py}.</p>
 */
class HarnessCliTest {
    @Test
    void defaultCommandUsesChatForTtyAndRunForPipedInput() {
        assertThat(HarnessCli.defaultCommand(true)).isEqualTo(HarnessCli.COMMAND_CHAT);
        assertThat(HarnessCli.defaultCommand(false)).isEqualTo(HarnessCli.COMMAND_RUN);
    }

    @Test
    void resolveRunPromptSupportsDashAndNonTtyPipe() {
        assertThat(HarnessCli.resolveRunPrompt("-", true, () -> "  hello\n")).isEqualTo("hello");
        assertThat(HarnessCli.resolveRunPrompt(null, false, () -> " piped prompt "))
                .isEqualTo("piped prompt");
        assertThatThrownBy(() -> HarnessCli.resolveRunPrompt(null, true, () -> "ignored"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt argument is required");
    }

    @Test
    void requestFromMapKeepsCompetitorAndImplementRequiresTask() {
        AutoHarnessRunRequest request = AutoHarnessRunRequest.fromMap(Map.of(
                "stage", "assess",
                "no_push", true,
                "budget", 12,
                "goal", "close gap",
                "competitor", "Claude Code",
                "pipeline", "extended"
        ));

        assertThat(request.getCompetitor()).isEqualTo("Claude Code");
        assertThat(request.isNoPush()).isTrue();
        assertThat(request.getBudget()).isEqualTo(12.0);
        assertThat(request.getPipeline()).isEqualTo("extended");

        AutoHarnessRunRequest invalid = new AutoHarnessRunRequest();
        invalid.setStage("implement");
        assertThatThrownBy(() -> AutoHarnessCliSupport.validateRunRequest(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--task or --task-file");
    }

    @Test
    void prepareRunAppliesRequestAndDetectsLocalRepo(@TempDir Path workspace) throws Exception {
        Path repo = makeFakeRepo(workspace, "agent-core");
        CLIOptions opts = new CLIOptions();
        opts.setWorkspace(workspace.toString());

        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setGoal("compare with Cursor");
        request.setCompetitor("Cursor");
        request.setBudget(40.0);
        request.setNoPush(true);
        request.setTask("improve CLI");

        AutoHarnessCliSupport.PreparedRun prepared = AutoHarnessCliSupport.prepareRun(
                opts,
                request,
                workspace.resolve("neutral"));

        assertThat(prepared.config().getOptimizationGoal()).isEqualTo("compare with Cursor");
        assertThat(prepared.config().getCompetitor()).isEqualTo("Cursor");
        assertThat(prepared.config().getSessionBudgetSecs()).isEqualTo(40.0);
        assertThat(prepared.config().getTaskTimeoutSecs()).isEqualTo(38.0);
        assertThat(prepared.config().getGitRemote()).isEmpty();
        assertThat(prepared.config().getLocalRepo()).isEqualTo(repo.toAbsolutePath().normalize().toString());
        assertThat(prepared.config().getWorkspace()).isEqualTo(repo.toAbsolutePath().normalize().toString());
        assertThat(prepared.tasks()).singleElement()
                .extracting(OptimizationTask::getTopic)
                .isEqualTo("improve CLI");
        assertThat(prepared.githubCliPreflightRequired()).isTrue();
        assertThat(prepared.config().getPipelinePreference())
                .isEqualTo(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
    }

    @Test
    void pipelineOptionOverridesDefault(@TempDir Path workspace) throws Exception {
        makeFakeRepo(workspace, "agent-core");
        CLIOptions opts = new CLIOptions();
        opts.setWorkspace(workspace.toString());

        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setGoal("compare with Cursor");
        request.setPipeline("extended");

        AutoHarnessCliSupport.PreparedRun prepared = AutoHarnessCliSupport.prepareRun(
                opts,
                request,
                workspace.resolve("neutral"));

        assertThat(prepared.config().getPipelinePreference())
                .isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
    }

    @Test
    void taskFileLoadsSingleObjectAndList(@TempDir Path tempDir) throws IOException {
        Path one = tempDir.resolve("task.json");
        Files.writeString(one, """
                {"topic":"single","description":"desc","files":["a.py"]}
                """, StandardCharsets.UTF_8);
        AutoHarnessRunRequest single = new AutoHarnessRunRequest();
        single.setTaskFile(one.toString());
        assertThat(AutoHarnessCliSupport.resolveTasks(single)).singleElement()
                .satisfies(task -> {
                    assertThat(task.getTopic()).isEqualTo("single");
                    assertThat(task.getDescription()).isEqualTo("desc");
                    assertThat(task.getFiles()).containsExactly("a.py");
                });

        Path many = tempDir.resolve("tasks.json");
        Files.writeString(many, """
                [{"topic":"a"},{"topic":"b","files":["b.py"]}]
                """, StandardCharsets.UTF_8);
        AutoHarnessRunRequest list = new AutoHarnessRunRequest();
        list.setTaskFile(many.toString());
        assertThat(AutoHarnessCliSupport.resolveTasks(list))
                .extracting(OptimizationTask::getTopic)
                .containsExactly("a", "b");
    }

    @Test
    void gapAnalyzeRequiresCompetitor() {
        assertThatThrownBy(() -> AutoHarnessCliSupport.prepareGapAnalyze("", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--competitor is required");

        AutoHarnessCliSupport.GapAnalyzeRequest request =
                AutoHarnessCliSupport.prepareGapAnalyze("workspace", "Devin");
        assertThat(request.competitor()).isEqualTo("Devin");
        assertThat(request.config().getWorkspace()).isEqualTo("workspace");
        assertThat(request.config().getCompetitor()).isEqualTo("Devin");
    }

    private static Path makeFakeRepo(Path parent, String name) throws IOException {
        Path repo = Files.createDirectories(parent.resolve(name));
        Files.createDirectories(repo.resolve(".git"));
        Files.writeString(repo.resolve("pyproject.toml"), "[project]\nname='x'\n", StandardCharsets.UTF_8);
        Files.createDirectories(repo.resolve("openjiuwen"));
        return repo;
    }
}
