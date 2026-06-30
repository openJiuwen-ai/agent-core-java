/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class AutoHarnessConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class AutoHarnessConfig {
    private static final List<String> DEFAULT_IMMUTABLE_FILES = List.of(
            "openjiuwen/auto_harness/prompts/identity.md",
            "openjiuwen/auto_harness/resources/ci_gate.yaml",
            "openjiuwen/harness/rails/security/prompt_security_rail.py"
    );

    @Builder.Default
    private String workspace = "";
    private Object model;
    @Builder.Default
    private String language = "cn";
    @Builder.Default
    private String optimizationGoal = "";
    @Builder.Default
    private String competitor = "";
    @Builder.Default
    private String pipelineName = "meta_evolve_pipeline";
    @Builder.Default
    private boolean isConfigBootstrapped = false;
    @Builder.Default
    private double sessionBudgetSecs = 3600.0;
    @Builder.Default
    private double costLimitUsd = 10.0;
    @Builder.Default
    private double taskTimeoutSecs = 1200.0;
    @Builder.Default
    private double modelTimeoutSecs = 300.0;
    @Builder.Default
    private int maxTasksPerSession = 3;
    @Builder.Default
    private int selfDrivenSlots = 1;
    @Builder.Default
    private String dataDir = "";
    @Builder.Default
    private List<String> skillsDirs = new ArrayList<>();
    @Builder.Default
    private String localRepo = "";
    @Builder.Default
    private String suggestedLocalRepo = "";
    @Builder.Default
    private String repoUrl = "https://gitcode.com/openJiuwen/agent-core.git";
    @Builder.Default
    private String upstreamRepo = "agent-core";
    @Builder.Default
    private String gitBaseBranch = "develop";
    @Builder.Default
    private String gitRemote = "";
    @Builder.Default
    private String gitUserName = "";
    @Builder.Default
    private String gitUserEmail = "";
    @Builder.Default
    private String forkOwner = "";
    @Builder.Default
    private String upstreamOwner = "openJiuwen";
    @Builder.Default
    private String gitcodeUsername = "";
    @Builder.Default
    private String gitcodeToken = "";
    @Builder.Default
    private String gitcodeTokenEnv = "GITCODE_ACCESS_TOKEN";
    @Builder.Default
    private String ciGateConfig = "";
    @Builder.Default
    private String ciGatePythonExecutable = "";
    @Builder.Default
    private String ciGateInstallCommand = "";
    @Builder.Default
    private int fixPhase1MaxRetries = 10;
    @Builder.Default
    private int fixPhase2MaxRetries = 9;
    @Builder.Default
    private String experienceDir = "";
    @Builder.Default
    private String configPath = "";
    @Builder.Default
    private List<String> stageRegistrars = new ArrayList<>();
    @Builder.Default
    private List<String> pipelineRegistrars = new ArrayList<>();
    @Builder.Default
    private List<String> immutableFiles = new ArrayList<>();
    @Builder.Default
    private List<String> highImpactPrefixes = new ArrayList<>(List.of("openjiuwen/core/"));
    @Builder.Default
    private Map<String, Integer> agentIterations = new LinkedHashMap<>(Map.of(
            "implement", 30,
            "assess", 30,
            "plan", 15,
            "select_pipeline", 10,
            "eval", 10,
            "pr_draft", 5,
            "learnings", 5,
            "explore_subagent", 20,
            "browser_subagent", 20
    ));

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path workspacePath() {
        return normalizePath(hasText(workspace) ? workspace : ".");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path experiencePath() {
        if (hasText(experienceDir)) {
            return normalizePath(experienceDir);
        }
        if (hasText(dataDir)) {
            return normalizePath(Path.of(dataDir).resolve("experience").toString());
        }
        return normalizePath(Path.of(".auto_harness", "experience").toString());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path worktreesPath() {
        if (hasText(dataDir)) {
            return normalizePath(Path.of(dataDir).resolve("worktrees").toString());
        }
        return normalizePath(Path.of(".auto_harness", "worktrees").toString());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path runsPath() {
        if (hasText(dataDir)) {
            return normalizePath(Path.of(dataDir).resolve("runs").toString());
        }
        return normalizePath(Path.of(".auto_harness", "runs").toString());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path cacheRepoPath() {
        if (hasText(dataDir)) {
            return normalizePath(Path.of(dataDir).resolve("repo").resolve(resolveRepoName()).toString());
        }
        return normalizePath(Path.of(".auto_harness", "repo", resolveRepoName()).toString());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ProjectProfile buildProjectProfile() {
        return ProjectProfile.builder()
                .repoUrl(repoUrl)
                .immutableFiles(resolveImmutableFiles())
                .highImpactPrefixes(new ArrayList<>(highImpactPrefixes))
                .defaultBaseBranch(hasText(gitBaseBranch) ? gitBaseBranch.trim() : "develop")
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AutoHarnessPaths buildPaths() {
        return AutoHarnessPaths.builder()
                .dataDir(hasText(dataDir) ? normalizePath(dataDir).toString() : "")
                .experienceDir(experiencePath().toString())
                .worktreesDir(worktreesPath().toString())
                .runsDir(runsPath().toString())
                .cacheRepoDir(cacheRepoPath().toString())
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String resolveRepoName() {
        for (String candidate : new String[]{upstreamRepo, repoUrlStem()}) {
            if (hasText(candidate)) {
                return removeGitSuffix(candidate.trim());
            }
        }
        return "repository";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> resolveImmutableFiles() {
        if (immutableFiles != null && !immutableFiles.isEmpty()) {
            return new ArrayList<>(immutableFiles);
        }
        return new ArrayList<>(DEFAULT_IMMUTABLE_FILES);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String resolveGitcodeUsername() {
        if (hasText(gitcodeUsername)) {
            return gitcodeUsername.trim();
        }
        if (hasText(forkOwner)) {
            return forkOwner.trim();
        }
        return "";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String resolveGitcodeToken() {
        if (hasText(gitcodeToken)) {
            return gitcodeToken.trim();
        }
        return System.getenv().getOrDefault(
                hasText(gitcodeTokenEnv) ? gitcodeTokenEnv.trim() : "GITCODE_ACCESS_TOKEN",
                ""
        );
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String resolveCiGatePythonExecutable() {
        if (hasText(ciGatePythonExecutable)) {
            return ciGatePythonExecutable.trim();
        }
        Path workspacePython = venvPython(workspace);
        if (Files.isRegularFile(workspacePython)) {
            return workspacePython.toString();
        }
        Path localRepoPython = venvPython(localRepo);
        if (Files.isRegularFile(localRepoPython)) {
            return localRepoPython.toString();
        }
        return Path.of(System.getProperty("java.home"), "bin", executableName("java")).toString();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int resolveAgentIterations(String stageName, int defaultValue) {
        if (agentIterations == null || !hasText(stageName)) {
            return defaultValue;
        }
        Integer configured = agentIterations.get(stageName);
        if (configured != null) {
            return configured;
        }
        return defaultValue;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static AutoHarnessConfig loadFromDict(Map<String, ?> data) {
        AutoHarnessConfig cfg = AutoHarnessConfig.builder().build();
        if (data == null || data.isEmpty()) {
            return cfg;
        }

        putTopLevel(cfg, data);
        putGit(cfg, asMap(data.get("git")));
        putGitcode(cfg, asMap(data.get("gitcode")));
        putBudget(cfg, asMap(data.get("budget")));
        putCiGate(cfg, asMap(data.get("ci_gate")));
        putFixLoop(cfg, asMap(data.get("fix_loop")));
        putAgentIterations(cfg, asMap(data.get("agent")));
        putExtensions(cfg, asMap(data.get("extensions")));
        return cfg;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static AutoHarnessConfig loadAutoHarnessConfig(String configPath) {
        return loadAutoHarnessConfig(configPath, "");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static AutoHarnessConfig loadAutoHarnessConfig(String configPath, String workspaceHint) {
        Path path = Path.of(configPath);
        if (!Files.isRegularFile(path)) {
            boolean isBootstrapped = bootstrapConfigFile(path);
            AutoHarnessConfig cfg = AutoHarnessConfig.builder()
                    .configPath(path.toString())
                    .isConfigBootstrapped(isBootstrapped)
                    .suggestedLocalRepo(detectLocalRepo(workspaceHint))
                    .build();
            if (!hasText(cfg.getDataDir())) {
                cfg.setDataDir(path.getParent() == null ? "" : path.getParent().toString());
            }
            return cfg;
        }

        Object loaded;
        try (InputStream stream = Files.newInputStream(path)) {
            loaded = new Yaml().load(stream);
        } catch (IOException | RuntimeException e) {
            AutoHarnessConfig cfg = AutoHarnessConfig.builder()
                    .configPath(path.toString())
                    .suggestedLocalRepo(detectLocalRepo(workspaceHint))
                    .build();
            if (!hasText(cfg.getDataDir())) {
                cfg.setDataDir(path.getParent() == null ? "" : path.getParent().toString());
            }
            return cfg;
        }

        AutoHarnessConfig cfg = loadFromDict(asMap(loaded));
        cfg.setConfigPath(path.toString());
        cfg.setSuggestedLocalRepo(detectLocalRepo(workspaceHint));
        if (!hasText(cfg.getDataDir())) {
            cfg.setDataDir(path.getParent() == null ? "" : path.getParent().toString());
        }
        return cfg;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static boolean isPlaceholderLocalRepo(String path) {
        String normalized = path == null ? "" : path.trim();
        return normalized.equals("./agent-core") || normalized.equals("./repo");
    }

    private String repoUrlStem() {
        if (!hasText(repoUrl)) {
            return "";
        }
        String trimmed = repoUrl.trim();
        int slashIndex = trimmed.lastIndexOf('/');
        String stem = slashIndex >= 0 ? trimmed.substring(slashIndex + 1) : trimmed;
        return stem.trim();
    }

    private static String removeGitSuffix(String value) {
        return value.endsWith(".git") ? value.substring(0, value.length() - 4) : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Path normalizePath(String rawPath) {
        return Path.of(rawPath).toAbsolutePath().normalize();
    }

    private static Path venvPython(String root) {
        if (!hasText(root)) {
            return Path.of("");
        }
        return normalizePath(root).resolve(".venv").resolve("bin").resolve(executableName("python"));
    }

    private static void putTopLevel(AutoHarnessConfig cfg, Map<String, ?> data) {
        setString(data, "data_dir", cfg::setDataDir);
        setString(data, "local_repo", cfg::setLocalRepo);
        setString(data, "repo_url", cfg::setRepoUrl);
        setString(data, "language", cfg::setLanguage);
        setString(data, "optimization_goal", cfg::setOptimizationGoal);
        setString(data, "competitor", cfg::setCompetitor);
        setString(data, "pipeline_name", cfg::setPipelineName);
        setString(data, "workspace", cfg::setWorkspace);
        setString(data, "experience_dir", cfg::setExperienceDir);
        setStringList(data, "skills_dirs", cfg::setSkillsDirs);
        setStringList(data, "stage_registrars", cfg::setStageRegistrars);
        setStringList(data, "pipeline_registrars", cfg::setPipelineRegistrars);
        setStringList(data, "immutable_files", cfg::setImmutableFiles);
        setStringList(data, "high_impact_prefixes", cfg::setHighImpactPrefixes);
    }

    private static void putGit(AutoHarnessConfig cfg, Map<String, ?> git) {
        setString(git, "remote", cfg::setGitRemote);
        setString(git, "base_branch", cfg::setGitBaseBranch);
        setString(git, "user_name", cfg::setGitUserName);
        setString(git, "user_email", cfg::setGitUserEmail);
        setString(git, "fork_owner", cfg::setForkOwner);
        setString(git, "upstream_owner", cfg::setUpstreamOwner);
        setString(git, "upstream_repo", cfg::setUpstreamRepo);
    }

    private static void putGitcode(AutoHarnessConfig cfg, Map<String, ?> gitcode) {
        setString(gitcode, "username", cfg::setGitcodeUsername);
        setString(gitcode, "access_token", cfg::setGitcodeToken);
        setString(gitcode, "access_token_env", cfg::setGitcodeTokenEnv);
    }

    private static void putBudget(AutoHarnessConfig cfg, Map<String, ?> budget) {
        setDouble(budget, "session_secs", cfg::setSessionBudgetSecs);
        setDouble(budget, "cost_limit_usd", cfg::setCostLimitUsd);
        setDouble(budget, "task_timeout_secs", cfg::setTaskTimeoutSecs);
        setDouble(budget, "model_timeout_secs", cfg::setModelTimeoutSecs);
        setInt(budget, "max_tasks_per_session", cfg::setMaxTasksPerSession);
        setInt(budget, "self_driven_slots", cfg::setSelfDrivenSlots);
    }

    private static void putCiGate(AutoHarnessConfig cfg, Map<String, ?> ciGate) {
        setString(ciGate, "config_path", cfg::setCiGateConfig);
        setString(ciGate, "python_executable", cfg::setCiGatePythonExecutable);
        setString(ciGate, "install_command", cfg::setCiGateInstallCommand);
    }

    private static void putFixLoop(AutoHarnessConfig cfg, Map<String, ?> fixLoop) {
        setInt(fixLoop, "phase1_max_retries", cfg::setFixPhase1MaxRetries);
        setInt(fixLoop, "phase2_max_retries", cfg::setFixPhase2MaxRetries);
    }

    private static void putAgentIterations(AutoHarnessConfig cfg, Map<String, ?> agent) {
        if (agent.isEmpty()) {
            return;
        }
        Map<String, Integer> iterations = new LinkedHashMap<>(cfg.getAgentIterations());
        agent.forEach((key, value) -> {
            Integer parsed = toInteger(value);
            if (parsed != null) {
                iterations.put(key, parsed);
            }
        });
        cfg.setAgentIterations(iterations);
    }

    private static void putExtensions(AutoHarnessConfig cfg, Map<String, ?> extensions) {
        setStringList(extensions, "stage_registrars", cfg::setStageRegistrars);
        setStringList(extensions, "pipeline_registrars", cfg::setPipelineRegistrars);
    }

    private static Map<String, ?> asMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        raw.forEach((key, val) -> {
            if (key != null) {
                converted.put(String.valueOf(key), val);
            }
        });
        return converted;
    }

    private static void setString(Map<String, ?> data, String key, java.util.function.Consumer<String> setter) {
        if (data.containsKey(key) && data.get(key) != null) {
            setter.accept(String.valueOf(data.get(key)));
        }
    }

    private static void setStringList(
            Map<String, ?> data,
            String key,
            java.util.function.Consumer<List<String>> setter) {
        Object value = data.get(key);
        if (value instanceof List<?> list) {
            setter.accept(list.stream().map(String::valueOf).toList());
        }
    }

    private static void setDouble(Map<String, ?> data, String key, java.util.function.DoubleConsumer setter) {
        Double value = toDouble(data.get(key));
        if (value != null) {
            setter.accept(value);
        }
    }

    private static void setInt(Map<String, ?> data, String key, java.util.function.IntConsumer setter) {
        Integer value = toInteger(data.get(key));
        if (value != null) {
            setter.accept(value);
        }
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean bootstrapConfigFile(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            String template = readBundledConfigTemplate();
            Files.writeString(path, template, StandardCharsets.UTF_8);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static String readBundledConfigTemplate() throws IOException {
        try (InputStream stream = AutoHarnessConfig.class.getResourceAsStream(
                "/openjiuwen/auto_harness/resources/config.yaml")) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return "# Auto-Harness config\n# local_repo: \"./agent-core\"\n";
    }

    private static String detectLocalRepo(String workspaceHint) {
        List<Path> candidates = new ArrayList<>();
        if (hasText(workspaceHint)) {
            Path hint = Path.of(workspaceHint).toAbsolutePath().normalize();
            candidates.add(hint);
            candidates.add(hint.resolve("agent-core"));
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        candidates.add(cwd);
        candidates.add(cwd.resolve("agent-core"));

        LinkedHashSet<Path> seen = new LinkedHashSet<>(candidates);
        for (Path candidate : seen) {
            if (looksLikeRepoRoot(candidate)) {
                return candidate.toString();
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

    private static String executableName(String command) {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? command + ".exe" : command;
    }
}
