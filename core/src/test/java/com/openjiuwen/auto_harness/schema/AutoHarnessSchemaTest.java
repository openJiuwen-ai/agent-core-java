/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.schema;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessPaths;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Gap;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ProjectProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for Auto Harness schema helpers.
 * <p>
 * Mirrors Python's config and DTO behavior in
 * {@code openjiuwen/auto_harness/schema.py}.
 */
class AutoHarnessSchemaTest {

    @TempDir
    private Path tempDir;

    @BeforeEach
    void muteSchemaLogger() {
        Logger.getLogger(AutoHarnessSchema.class.getName()).setLevel(Level.OFF);
    }

    @Test
    void normalizePipelinePreferencePreservesAliasesAndFallback() {
        assertThat(AutoHarnessSchema.normalizePipelinePreference(""))
                .isEqualTo(AutoHarnessSchema.PIPELINE_PREFERENCE_AUTO);
        assertThat(AutoHarnessSchema.normalizePipelinePreference("meta"))
                .isEqualTo(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
        assertThat(AutoHarnessSchema.normalizePipelinePreference("extended_harness_pipeline"))
                .isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
        assertThat(AutoHarnessSchema.normalizePipelinePreference("unknown"))
                .isEqualTo(AutoHarnessSchema.PIPELINE_PREFERENCE_AUTO);
    }

    @Test
    void loadFromDictResolvesNestedConfigSectionsAndDerivedPaths() throws Exception {
        Path venvPython = tempDir.resolve("repo").resolve(".venv").resolve("Scripts").resolve("python.exe");
        Files.createDirectories(venvPython.getParent());
        Files.createFile(venvPython);

        AutoHarnessConfig cfg = AutoHarnessConfig.loadFromDict(Map.ofEntries(
                Map.entry("data_dir", tempDir.resolve("data").toString()),
                Map.entry("local_repo", tempDir.resolve("repo").toString()),
                Map.entry("repo_url", "https://gitcode.com/example/custom.git"),
                Map.entry("pipeline", "extended"),
                Map.entry("skills_dirs", List.of("skills-a", "skills-b")),
                Map.entry("immutable_files", List.of("custom/immutable.py")),
                Map.entry("git", Map.of(
                        "base_branch", "develop-auto",
                        "fork_owner", "alice",
                        "upstream_repo", ""
                )),
                Map.entry("gitcode", Map.of(
                        "access_token", "token-value"
                )),
                Map.entry("budget", Map.of(
                        "session_secs", 12,
                        "max_tasks_per_session", 7
                )),
                Map.entry("ci_gate", Map.of(
                        "python_executable", "python-fixed",
                        "install_command", "uv sync"
                )),
                Map.entry("fix_loop", Map.of(
                        "phase1_max_retries", 3
                )),
                Map.entry("agent", Map.of(
                        "implement", 42
                )),
                Map.entry("extensions", Map.of(
                        "stage_registrars", List.of("pkg:register_stage"),
                        "pipeline_registrars", List.of("pkg:register_pipeline")
                ))
        ));

        AutoHarnessPaths paths = cfg.buildPaths();
        ProjectProfile profile = cfg.buildProjectProfile();

        assertThat(cfg.getPipelinePreference()).isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
        assertThat(cfg.getSkillsDirs()).containsExactly("skills-a", "skills-b");
        assertThat(cfg.getGitBaseBranch()).isEqualTo("develop-auto");
        assertThat(cfg.resolveGitcodeUsername()).isEqualTo("alice");
        assertThat(cfg.resolveGitcodeToken()).isEqualTo("token-value");
        assertThat(cfg.getSessionBudgetSecs()).isEqualTo(12.0);
        assertThat(cfg.getMaxTasksPerSession()).isEqualTo(7);
        assertThat(cfg.getCiGatePythonExecutable()).isEqualTo("python-fixed");
        assertThat(cfg.getCiGateInstallCommand()).isEqualTo("uv sync");
        assertThat(cfg.getFixPhase1MaxRetries()).isEqualTo(3);
        assertThat(cfg.resolveAgentIterations("implement", 1)).isEqualTo(42);
        assertThat(cfg.getStageRegistrars()).containsExactly("pkg:register_stage");
        assertThat(cfg.getPipelineRegistrars()).containsExactly("pkg:register_pipeline");
        assertThat(cfg.resolveImmutableFiles()).containsExactly("custom/immutable.py");
        assertThat(profile.getRepoUrl()).isEqualTo("https://gitcode.com/example/custom.git");
        assertThat(profile.getDefaultBaseBranch()).isEqualTo("develop-auto");
        assertThat(paths.getExperienceDir()).endsWith("data" + java.io.File.separator + "experience");
    }

    @Test
    void loadAutoHarnessConfigBootstrapsMissingFileAndDetectsRepo() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace.resolve(".git"));
        Files.createDirectories(workspace.resolve("openjiuwen"));
        Files.createFile(workspace.resolve("pyproject.toml"));
        Path configPath = tempDir.resolve("config").resolve("config.yaml");

        AutoHarnessConfig cfg = AutoHarnessSchema.loadAutoHarnessConfig(configPath.toString(), workspace.toString());

        assertThat(cfg.isConfigBootstrapped()).isTrue();
        assertThat(Files.isRegularFile(configPath)).isTrue();
        assertThat(cfg.getDataDir()).isEqualTo(configPath.getParent().toString());
        assertThat(cfg.getSuggestedLocalRepo()).isEqualTo(workspace.toAbsolutePath().normalize().toString());
    }

    @Test
    void loadAutoHarnessConfigReadsYamlAndKeepsConfigPathDefaults() throws Exception {
        Path configPath = tempDir.resolve("config.yaml");
        Files.writeString(configPath, """
                data_dir: "custom-data"
                pipeline_preference: "pr_pipeline"
                git:
                  upstream_repo: "agent-core-java"
                budget:
                  task_timeout_secs: 99
                """);

        AutoHarnessConfig cfg = AutoHarnessSchema.loadAutoHarnessConfig(configPath.toString(), "");

        assertThat(cfg.getPipelinePreference()).isEqualTo(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
        assertThat(cfg.getDataDir()).isEqualTo("custom-data");
        assertThat(cfg.resolveRepoName()).isEqualTo("agent-core-java");
        assertThat(cfg.getTaskTimeoutSecs()).isEqualTo(99.0);
        assertThat(cfg.getConfigPath()).isEqualTo(configPath.toString());
    }

    @Test
    void dtoDefaultsMirrorPythonDataclasses() {
        Gap gap = Gap.builder().impact(0.5).feasibility(0.8).build();
        AutoHarnessConfig cfg = new AutoHarnessConfig();

        assertThat(gap.getPriority()).isEqualTo(0.4);
        assertThat(AutoHarnessSchema.defaultImmutableFiles())
                .contains("openjiuwen/auto_harness/resources/ci_gate.yaml");
        assertThat(cfg.resolveRepoName()).isEqualTo("agent-core");
        assertThat(AutoHarnessSchema.isPlaceholderLocalRepo("/home/user/repo")).isTrue();
        assertThat(AutoHarnessSchema.isPlaceholderLocalRepo("/real/repo")).isFalse();
    }
}
