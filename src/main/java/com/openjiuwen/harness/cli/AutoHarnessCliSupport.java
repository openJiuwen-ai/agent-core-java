/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Support helpers for the top-level auto-harness CLI commands.
 *
 * <p>Mirrors Python's module-level auto-harness helpers in
 * {@code openjiuwen/harness/cli/cli.py}.</p>
 */
public final class AutoHarnessCliSupport {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AutoHarnessCliSupport() {
    }

    public static void bootstrapLogging(boolean pytestMode) {
        if (pytestMode) {
            return;
        }
        Logger.getLogger("openjiuwen").setLevel(Level.OFF);
        Logger.getLogger("").setLevel(Level.WARNING);
    }

    public static String buildDataDir(String cliHome) {
        return Path.of(cliHome, "auto_harness").toString();
    }

    public static String buildConfigPath(String cliHome) {
        return Path.of(buildDataDir(cliHome), "config.yaml").toString();
    }

    public static AutoHarnessConfig applyRequest(
            AutoHarnessConfig config,
            AutoHarnessRunRequest request) {
        AutoHarnessConfig effectiveConfig = config == null ? new AutoHarnessConfig() : config;
        if (request == null) {
            return effectiveConfig;
        }
        if (request.getBudget() != null) {
            effectiveConfig.setSessionBudgetSecs(request.getBudget());
            effectiveConfig.setTaskTimeoutSecs(Math.min(
                    effectiveConfig.getTaskTimeoutSecs(),
                    request.getBudget() * 0.95D));
        }
        if (request.isNoPush()) {
            effectiveConfig.setGitRemote("");
        }
        if (!isBlank(request.getGoal())) {
            effectiveConfig.setOptimizationGoal(request.getGoal());
        }
        if (!isBlank(request.getCompetitor())) {
            effectiveConfig.setCompetitor(request.getCompetitor());
        }
        effectiveConfig.setPipelinePreference(AutoHarnessSchema.normalizePipelinePreference(
                isBlank(request.getPipeline()) ? AutoHarnessPipelineNames.META_EVOLVE_PIPELINE : request.getPipeline()));
        return effectiveConfig;
    }

    public static PreparedRun prepareRun(
            CLIOptions opts,
            AutoHarnessRunRequest request,
            Path currentDirectory) throws IOException {
        AutoHarnessRunRequest effectiveRequest = request == null ? new AutoHarnessRunRequest() : request;
        validateRunRequest(effectiveRequest);

        CLIOptions effectiveOpts = opts == null ? new CLIOptions() : opts;
        String workspace = valueOrEmpty(effectiveOpts.getWorkspace());
        String cliHome = workspace.isBlank()
                ? Path.of(System.getProperty("user.home"), ".openjiuwen").toString()
                : workspace;
        String dataDir = buildDataDir(cliHome);
        String configPath = buildConfigPath(cliHome);

        AutoHarnessConfig config = AutoHarnessSchema.loadAutoHarnessConfig(configPath, workspace);
        config.setDataDir(dataDir);
        if (!isBlank(config.getLocalRepo()) && isInvalidLocalRepo(config.getLocalRepo())) {
            config.setLocalRepo("");
        }
        if (isBlank(config.getLocalRepo()) && !isBlank(config.getSuggestedLocalRepo())) {
            config.setLocalRepo(config.getSuggestedLocalRepo());
        }
        if (isBlank(config.getLocalRepo())) {
            String detected = detectLocalRepo(workspace, currentDirectory);
            if (!detected.isBlank()) {
                config.setLocalRepo(detected);
                config.setSuggestedLocalRepo(detected);
            }
        }
        if (!isBlank(config.getLocalRepo())) {
            config.setWorkspace(config.getLocalRepo());
        } else if (isBlank(config.getWorkspace())) {
            config.setWorkspace(workspace);
        }

        applyRequest(config, effectiveRequest);
        List<OptimizationTask> tasks = resolveTasks(effectiveRequest);
        return new PreparedRun(
                config,
                tasks,
                effectiveRequest.isDryRun(),
                effectiveRequest.getStage(),
                shouldRunGitHubCliPreflight(effectiveRequest),
                Path.of(dataDir),
                Path.of(configPath));
    }

    public static GapAnalyzeRequest prepareGapAnalyze(String workspace, String competitor) {
        if (isBlank(competitor)) {
            throw new IllegalArgumentException("--competitor is required");
        }
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setWorkspace(isBlank(workspace) ? Path.of("").toAbsolutePath().toString() : workspace);
        config.setCompetitor(competitor);
        return new GapAnalyzeRequest(config, competitor);
    }

    public static void validateRunRequest(AutoHarnessRunRequest request) {
        if (request == null) {
            return;
        }
        if ("implement".equals(request.getStage()) && !request.hasManualTasks()) {
            throw new IllegalArgumentException("--stage implement requires --task or --task-file");
        }
    }

    public static boolean shouldRunGitHubCliPreflight(AutoHarnessRunRequest request) {
        String stage = request == null ? null : request.getStage();
        return stage == null || "assess".equals(stage) || "plan".equals(stage);
    }

    public static List<OptimizationTask> resolveTasks(AutoHarnessRunRequest request) throws IOException {
        if (request == null) {
            return null;
        }
        if (!isBlank(request.getTask())) {
            OptimizationTask task = new OptimizationTask();
            task.setTopic(request.getTask());
            return List.of(task);
        }
        if (isBlank(request.getTaskFile())) {
            return null;
        }
        Object raw = MAPPER.readValue(Path.of(request.getTaskFile()).toFile(), Object.class);
        List<?> rows = raw instanceof List<?> list ? list : List.of(raw);
        List<OptimizationTask> tasks = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Task file entries must be JSON objects");
            }
            tasks.add(taskFromMap(map));
        }
        return tasks;
    }

    public static List<Map<String, Object>> dryRunPayload(List<OptimizationTask> tasks) {
        if (tasks == null) {
            return List.of();
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        for (OptimizationTask task : tasks) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("topic", task.getTopic());
            row.put("description", task.getDescription());
            row.put("files", task.getFiles());
            payload.add(row);
        }
        return payload;
    }

    public static String detectLocalRepo(String workspaceHint, Path currentDirectory) {
        List<Path> candidates = new ArrayList<>();
        if (!isBlank(workspaceHint)) {
            Path hint = Path.of(workspaceHint);
            candidates.add(hint);
            candidates.add(hint.resolve("agent-core"));
        }
        if (currentDirectory != null) {
            Path cwd = currentDirectory.toAbsolutePath().normalize();
            candidates.add(cwd);
            candidates.add(cwd.resolve("agent-core"));
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Path candidate : candidates) {
            Path resolved = candidate.toAbsolutePath().normalize();
            if (!seen.add(resolved.toString())) {
                continue;
            }
            if (looksLikeRepoRoot(resolved)) {
                return resolved.toString();
            }
        }
        return "";
    }

    private static OptimizationTask taskFromMap(Map<?, ?> map) {
        Object topic = map.get("topic");
        if (topic == null || String.valueOf(topic).isBlank()) {
            throw new IllegalArgumentException("Task file entry requires topic");
        }
        OptimizationTask task = new OptimizationTask();
        task.setTopic(String.valueOf(topic));
        task.setDescription(valueOrEmpty(map.get("description")));
        task.setFiles(stringList(map.get("files")));
        return task;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private static boolean isInvalidLocalRepo(String localRepo) {
        return AutoHarnessSchema.isPlaceholderLocalRepo(localRepo) || !safeExists(Path.of(localRepo));
    }

    private static boolean safeExists(Path path) {
        try {
            return Files.exists(path);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean looksLikeRepoRoot(Path path) {
        return Files.isDirectory(path)
                && Files.exists(path.resolve(".git"))
                && Files.isRegularFile(path.resolve("pyproject.toml"))
                && Files.isDirectory(path.resolve("openjiuwen"));
    }

    private static String valueOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Prepared auto-harness run state.
     *
     * <p>Mirrors Python's local auto-harness run setup in
     * {@code openjiuwen/harness/cli/cli.py}.</p>
     */
    public record PreparedRun(
            AutoHarnessConfig config,
            List<OptimizationTask> tasks,
            boolean dryRun,
            String stage,
            boolean githubCliPreflightRequired,
            Path dataDir,
            Path configPath) {

        public List<Map<String, Object>> dryRunPayload() {
            return AutoHarnessCliSupport.dryRunPayload(tasks);
        }
    }

    /**
     * Prepared gap-analysis request.
     *
     * <p>Mirrors Python's {@code gap_analyze} command setup in
     * {@code openjiuwen/harness/cli/cli.py}.</p>
     */
    public record GapAnalyzeRequest(AutoHarnessConfig config, String competitor) {
    }
}
