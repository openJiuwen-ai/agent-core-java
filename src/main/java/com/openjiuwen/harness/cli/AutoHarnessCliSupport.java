/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Minimal Java support seam for auto-harness CLI integration.
 * <p>
 * Mirrors the highest-value request/config/task resolution behavior used by
 * Python's CLI helpers in {@code openjiuwen.harness.cli.cli} and REPL entrypoints.
 */
public final class AutoHarnessCliSupport {

    private AutoHarnessCliSupport() {
    }

    public static String buildDataDir(String cliHome) {
        return Path.of(cliHome, "auto_harness").toString();
    }

    public static String buildConfigPath(String cliHome) {
        return Path.of(buildDataDir(cliHome), "config.yaml").toString();
    }

    public static List<String> resolveTasks(AutoHarnessRunRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getTask() == null || request.getTask().isBlank()) {
            return null;
        }
        return Collections.singletonList(request.getTask());
    }

    public static AutoHarnessConfig applyRequest(AutoHarnessConfig config, AutoHarnessRunRequest request) {
        if (config == null) {
            config = new AutoHarnessConfig();
        }
        if (request == null) {
            return config;
        }
        if (request.getBudget() != null) {
            config.setSessionBudgetSecs(request.getBudget());
            config.setTaskTimeoutSecs(Math.min(config.getTaskTimeoutSecs(), request.getBudget() * 0.95));
        }
        if (request.isNoPush()) {
            config.setGitRemote("");
        }
        if (request.getGoal() != null && !request.getGoal().isBlank()) {
            config.setOptimizationGoal(request.getGoal());
        }
        if (request.getCompetitor() != null && !request.getCompetitor().isBlank()) {
            config.setCompetitor(request.getCompetitor());
        }
        return config;
    }

    public static PreparedRun prepareRun(CliOptions opts, AutoHarnessRunRequest request, Path currentDirectory) {
        CliOptions effectiveOpts = opts != null ? opts : new CliOptions();
        Path cwd = currentDirectory != null ? currentDirectory.toAbsolutePath().normalize() : Path.of("").toAbsolutePath().normalize();
        String cliHome = effectiveOpts.getWorkspace() == null || effectiveOpts.getWorkspace().isBlank()
                ? Path.of(System.getProperty("user.home"), ".openjiuwen").toString()
                : effectiveOpts.getWorkspace();
        String dataDir = buildDataDir(cliHome);
        String configPath = buildConfigPath(cliHome);

        AutoHarnessConfig config = AutoHarnessConfig.loadAutoHarnessConfig(configPath, effectiveOpts.getWorkspace());
        config.setDataDir(dataDir);
        if (config.getLocalRepo() != null
                && !config.getLocalRepo().isBlank()
                && (AutoHarnessConfig.isPlaceholderLocalRepo(config.getLocalRepo())
                || !Files.exists(Path.of(config.getLocalRepo())))) {
            config.setLocalRepo("");
        }

        String detectedRepo = detectLocalRepo(effectiveOpts.getWorkspace(), cwd);
        if ((config.getLocalRepo() == null || config.getLocalRepo().isBlank()) && !detectedRepo.isBlank()) {
            config.setLocalRepo(detectedRepo);
            config.setSuggestedLocalRepo(detectedRepo);
        }
        if (config.getLocalRepo() != null && !config.getLocalRepo().isBlank()) {
            config.setWorkspace(config.getLocalRepo());
        } else if (config.getWorkspace() == null || config.getWorkspace().isBlank()) {
            config.setWorkspace(effectiveOpts.getWorkspace() != null ? effectiveOpts.getWorkspace() : "");
        }

        applyRequest(config, request);
        return new PreparedRun(config, resolveTasks(request), shouldRunGitHubCliPreflight(request));
    }

    public static boolean shouldRunGitHubCliPreflight(AutoHarnessRunRequest request) {
        String stage = request != null ? request.getStage() : null;
        return stage == null || "assess".equals(stage) || "plan".equals(stage);
    }

    public static String detectLocalRepo(String workspaceHint, Path currentDirectory) {
        List<Path> candidates = new ArrayList<>();
        if (workspaceHint != null && !workspaceHint.isBlank()) {
            Path hint = Path.of(workspaceHint).toAbsolutePath().normalize();
            candidates.add(hint);
            candidates.add(hint.resolve("agent-core"));
        }
        if (currentDirectory != null) {
            Path cwd = currentDirectory.toAbsolutePath().normalize();
            candidates.add(cwd);
            candidates.add(cwd.resolve("agent-core"));
        }

        Set<String> seen = new HashSet<>();
        for (Path candidate : candidates) {
            Path resolved = candidate.toAbsolutePath().normalize();
            String key = resolved.toString();
            if (!seen.add(key)) {
                continue;
            }
            if (looksLikeRepoRoot(resolved)) {
                return key;
            }
        }
        return "";
    }

    private static boolean looksLikeRepoRoot(Path path) {
        return Files.isDirectory(path)
                && Files.exists(path.resolve(".git"))
                && Files.isRegularFile(path.resolve("pyproject.toml"))
                && Files.isDirectory(path.resolve("openjiuwen"));
    }

    public static final class CliOptions {
        private String workspace = "";
        private String provider = "";
        private String model = "";
        private String apiKey = "";
        private String apiBase = "";
        private boolean verbose;

        public String getWorkspace() { return workspace; }
        public void setWorkspace(String workspace) { this.workspace = workspace; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiBase() { return apiBase; }
        public void setApiBase(String apiBase) { this.apiBase = apiBase; }
        public boolean isVerbose() { return verbose; }
        public void setVerbose(boolean verbose) { this.verbose = verbose; }
    }

    public static final class PreparedRun {
        private final AutoHarnessConfig config;
        private final List<String> tasks;
        private final boolean githubCliPreflightRequired;

        private PreparedRun(AutoHarnessConfig config, List<String> tasks, boolean githubCliPreflightRequired) {
            this.config = config;
            this.tasks = tasks;
            this.githubCliPreflightRequired = githubCliPreflightRequired;
        }

        public AutoHarnessConfig getConfig() { return config; }
        public List<String> getTasks() { return tasks; }
        public boolean isGithubCliPreflightRequired() { return githubCliPreflightRequired; }
    }
}
