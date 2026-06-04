package com.openjiuwen.auto_harness.schema;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors Python's {@code AutoHarnessConfig} in {@code openjiuwen.auto_harness.schema}.
 */
public class AutoHarnessConfig {

    private Object model;
    private String dataDir = "";
    private String localRepo = "";
    private String repoUrl = "https://gitcode.com/openJiuwen/agent-core.git";
    private List<String> skillsDirs = new ArrayList<>();
    private List<String> stageRegistrars = new ArrayList<>();
    private List<String> pipelineRegistrars = new ArrayList<>();
    private String language = "cn";
    private String optimizationGoal = "";
    private String competitor = "";
    private double sessionBudgetSecs = 3600.0;
    private double costLimitUsd = 10.0;
    private double taskTimeoutSecs = 1200.0;
    private double modelTimeoutSecs = 300.0;
    private int maxTasksPerSession = 3;
    private int selfDrivenSlots = 1;
    private String gitRemote = "";
    private String gitBaseBranch = "develop";
    private String gitUserName = "";
    private String gitUserEmail = "";
    private String forkOwner = "";
    private String upstreamOwner = "openJiuwen";
    private String upstreamRepo = "agent-core";
    private String gitcodeUsername = "";
    private String gitcodeToken = "";
    private String gitcodeTokenEnv = "GITCODE_ACCESS_TOKEN";
    private String ciGateConfig = "";
    private String ciGatePythonExecutable = "";
    private String ciGateInstallCommand = "";
    private int fixPhase1MaxRetries = 10;
    private int fixPhase2MaxRetries = 9;
    private List<String> immutableFiles = new ArrayList<>();
    private List<String> highImpactPrefixes = new ArrayList<>(List.of("openjiuwen/core/"));
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
    private String workspace = "";
    private String configPath = "";
    private boolean configBootstrapped;
    private String suggestedLocalRepo = "";
    private String experienceDir = "";

    public static AutoHarnessConfig loadAutoHarnessConfig(Path configPath) {
        return loadAutoHarnessConfig(configPath, "");
    }

    public static AutoHarnessConfig loadAutoHarnessConfig(Path configPath, String workspaceHint) {
        AutoHarnessConfig cfg;
        if (configPath == null || !Files.isRegularFile(configPath)) {
            cfg = new AutoHarnessConfig();
            if (configPath != null) {
                cfg.configPath = configPath.toString();
                Path parent = configPath.toAbsolutePath().getParent();
                cfg.dataDir = parent != null ? parent.toString() : "";
                cfg.configBootstrapped = bootstrapConfigFile(configPath);
                cfg.suggestedLocalRepo = detectLocalRepo(workspaceHint);
            }
            return cfg;
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            Object loaded = new Yaml().load(reader);
            if (loaded instanceof Map<?, ?> map) {
                cfg = loadFromMap(map);
            } else {
                cfg = new AutoHarnessConfig();
            }
        } catch (IOException | RuntimeException ignored) {
            cfg = new AutoHarnessConfig();
        }
        cfg.configPath = configPath.toString();
        cfg.suggestedLocalRepo = detectLocalRepo(workspaceHint);
        if (cfg.dataDir == null || cfg.dataDir.isBlank()) {
            cfg.dataDir = configPath.toAbsolutePath().getParent().toString();
        }
        return cfg;
    }

    public static AutoHarnessConfig loadAutoHarnessConfig(String configPath) {
        return loadAutoHarnessConfig(Path.of(configPath));
    }

    public static AutoHarnessConfig loadAutoHarnessConfig(String configPath, String workspaceHint) {
        return loadAutoHarnessConfig(Path.of(configPath), workspaceHint);
    }

    public static AutoHarnessConfig loadFromMap(Map<?, ?> data) {
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        if (data == null) {
            return cfg;
        }

        cfg.dataDir = stringValue(data.get("data_dir"), cfg.dataDir);
        cfg.localRepo = stringValue(data.get("local_repo"), cfg.localRepo);
        cfg.repoUrl = stringValue(data.get("repo_url"), cfg.repoUrl);
        cfg.skillsDirs = stringList(data.get("skills_dirs"), cfg.skillsDirs);
        cfg.stageRegistrars = stringList(data.get("stage_registrars"), cfg.stageRegistrars);
        cfg.pipelineRegistrars = stringList(data.get("pipeline_registrars"), cfg.pipelineRegistrars);
        cfg.immutableFiles = stringList(data.get("immutable_files"), cfg.immutableFiles);
        cfg.language = stringValue(data.get("language"), cfg.language);
        cfg.optimizationGoal = stringValue(data.get("optimization_goal"), cfg.optimizationGoal);
        cfg.competitor = stringValue(data.get("competitor"), cfg.competitor);
        cfg.workspace = stringValue(data.get("workspace"), cfg.workspace);
        cfg.experienceDir = stringValue(data.get("experience_dir"), cfg.experienceDir);

        Map<?, ?> git = mapValue(data.get("git"));
        cfg.gitRemote = stringValue(git.get("remote"), cfg.gitRemote);
        cfg.gitBaseBranch = stringValue(git.get("base_branch"), cfg.gitBaseBranch);
        cfg.gitUserName = stringValue(git.get("user_name"), cfg.gitUserName);
        cfg.gitUserEmail = stringValue(git.get("user_email"), cfg.gitUserEmail);
        cfg.forkOwner = stringValue(git.get("fork_owner"), cfg.forkOwner);
        cfg.upstreamOwner = stringValue(git.get("upstream_owner"), cfg.upstreamOwner);
        cfg.upstreamRepo = stringValue(git.get("upstream_repo"), cfg.upstreamRepo);

        Map<?, ?> gitcode = mapValue(data.get("gitcode"));
        cfg.gitcodeUsername = stringValue(gitcode.get("username"), cfg.gitcodeUsername);
        cfg.gitcodeTokenEnv = stringValue(gitcode.get("access_token_env"), cfg.gitcodeTokenEnv);
        cfg.gitcodeToken = stringValue(gitcode.get("access_token"), cfg.gitcodeToken);

        Map<?, ?> budget = mapValue(data.get("budget"));
        cfg.sessionBudgetSecs = doubleValue(budget.get("session_secs"), cfg.sessionBudgetSecs);
        cfg.costLimitUsd = doubleValue(budget.get("cost_limit_usd"), cfg.costLimitUsd);
        cfg.taskTimeoutSecs = doubleValue(budget.get("task_timeout_secs"), cfg.taskTimeoutSecs);
        cfg.modelTimeoutSecs = doubleValue(budget.get("model_timeout_secs"), cfg.modelTimeoutSecs);
        cfg.maxTasksPerSession = intValue(budget.get("max_tasks_per_session"), cfg.maxTasksPerSession);
        cfg.selfDrivenSlots = intValue(budget.get("self_driven_slots"), cfg.selfDrivenSlots);

        Map<?, ?> ciGate = mapValue(data.get("ci_gate"));
        cfg.ciGateConfig = stringValue(ciGate.get("config_path"), cfg.ciGateConfig);
        cfg.ciGatePythonExecutable = stringValue(ciGate.get("python_executable"), cfg.ciGatePythonExecutable);
        cfg.ciGateInstallCommand = stringValue(ciGate.get("install_command"), cfg.ciGateInstallCommand);

        Map<?, ?> fixLoop = mapValue(data.get("fix_loop"));
        cfg.fixPhase1MaxRetries = intValue(fixLoop.get("phase1_max_retries"), cfg.fixPhase1MaxRetries);
        cfg.fixPhase2MaxRetries = intValue(fixLoop.get("phase2_max_retries"), cfg.fixPhase2MaxRetries);

        Map<?, ?> agent = mapValue(data.get("agent"));
        for (Map.Entry<?, ?> entry : agent.entrySet()) {
            String key = String.valueOf(entry.getKey());
            cfg.agentIterations.put(key, intValue(entry.getValue(), cfg.agentIterations.getOrDefault(key, 0)));
        }

        Map<?, ?> extensions = mapValue(data.get("extensions"));
        cfg.stageRegistrars = stringList(extensions.get("stage_registrars"), cfg.stageRegistrars);
        cfg.pipelineRegistrars = stringList(extensions.get("pipeline_registrars"), cfg.pipelineRegistrars);

        return cfg;
    }

    private static Map<?, ?> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static double doubleValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static List<String> stringList(Object value, List<String> defaultValue) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>(defaultValue);
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private static boolean bootstrapConfigFile(Path path) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String content = "# Auto Harness config\n"
                    + "# local_repo: \"/home/user/code/agent-core\"\n"
                    + "repo_url: https://gitcode.com/openJiuwen/agent-core.git\n";
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static String detectLocalRepo(String workspaceHint) {
        List<Path> candidates = new ArrayList<>();
        if (workspaceHint != null && !workspaceHint.isBlank()) {
            Path hint = Path.of(workspaceHint).toAbsolutePath().normalize();
            candidates.add(hint);
            candidates.add(hint.resolve("agent-core"));
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        candidates.add(cwd);
        candidates.add(cwd.resolve("agent-core"));

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

    public static boolean isPlaceholderLocalRepo(String path) {
        String normalized = path == null ? "" : path.strip();
        return "/home/user/code/agent-core".equals(normalized)
                || "/home/user/repo".equals(normalized);
    }

    public String getResolvedExperienceDir() {
        if (!experienceDir.isBlank()) {
            return experienceDir;
        }
        if (!dataDir.isBlank()) {
            return joinPath(dataDir, "experience");
        }
        return ".auto_harness/experience/";
    }

    public String getWorktreesDir() {
        return dataDir.isBlank() ? ".auto_harness/worktrees/" : joinPath(dataDir, "worktrees");
    }

    public String getRunsDir() {
        return dataDir.isBlank() ? ".auto_harness/runs/" : joinPath(dataDir, "runs");
    }

    public String getCacheRepoDir() {
        String repoName = resolveRepoName();
        return dataDir.isBlank() ? ".auto_harness/repo/" + repoName : joinPath(dataDir, "repo", repoName);
    }

    public String resolveRepoName() {
        String upstream = upstreamRepo != null ? upstreamRepo.trim() : "";
        if (!upstream.isBlank()) {
            return upstream;
        }
        String normalizedUrl = repoUrl == null ? "" : repoUrl.trim().replaceAll("[/\\\\]+$", "");
        int slash = Math.max(normalizedUrl.lastIndexOf('/'), normalizedUrl.lastIndexOf('\\'));
        String name = slash >= 0 ? normalizedUrl.substring(slash + 1) : normalizedUrl;
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.isBlank() ? "repository" : name;
    }

    private static String joinPath(String first, String... more) {
        return Path.of(first, more).toString().replace('\\', '/');
    }

    public String resolveGitcodeUsername() {
        if (!gitcodeUsername.isBlank()) {
            return gitcodeUsername;
        }
        if (!forkOwner.isBlank()) {
            return forkOwner;
        }
        return "";
    }

    public int resolveAgentIterations(String stageName, int defaultValue) {
        try {
            return agentIterations.getOrDefault(stageName, defaultValue);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public List<String> resolveImmutableFiles() {
        if (!immutableFiles.isEmpty()) {
            return new ArrayList<>(immutableFiles);
        }
        return List.of(
                "openjiuwen/auto_harness/prompts/identity.md",
                "openjiuwen/auto_harness/resources/ci_gate.yaml",
                "openjiuwen/harness/rails/security/prompt_security_rail.py"
        );
    }

    public AutoHarnessPaths buildPaths() {
        AutoHarnessPaths paths = new AutoHarnessPaths();
        paths.setDataDir(dataDir);
        paths.setExperienceDir(getResolvedExperienceDir());
        paths.setWorktreesDir(getWorktreesDir());
        paths.setRunsDir(getRunsDir());
        paths.setCacheRepoDir(getCacheRepoDir());
        return paths;
    }

    public ProjectProfile buildProjectProfile() {
        ProjectProfile profile = new ProjectProfile();
        profile.setRepoUrl(repoUrl);
        profile.setImmutableFiles(resolveImmutableFiles());
        profile.setHighImpactPrefixes(new ArrayList<>(highImpactPrefixes));
        profile.setDefaultBaseBranch(gitBaseBranch != null && !gitBaseBranch.isBlank() ? gitBaseBranch : "develop");
        return profile;
    }

    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public Object getModel() { return model; }
    public void setModel(Object model) { this.model = model; }
    public String getLocalRepo() { return localRepo; }
    public void setLocalRepo(String localRepo) { this.localRepo = localRepo; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public List<String> getSkillsDirs() { return new ArrayList<>(skillsDirs); }
    public void setSkillsDirs(List<String> skillsDirs) { this.skillsDirs = skillsDirs != null ? new ArrayList<>(skillsDirs) : new ArrayList<>(); }
    public double getSessionBudgetSecs() { return sessionBudgetSecs; }
    public void setSessionBudgetSecs(double sessionBudgetSecs) { this.sessionBudgetSecs = sessionBudgetSecs; }
    public double getModelTimeoutSecs() { return modelTimeoutSecs; }
    public void setModelTimeoutSecs(double modelTimeoutSecs) { this.modelTimeoutSecs = modelTimeoutSecs; }
    public int getMaxTasksPerSession() { return maxTasksPerSession; }
    public void setMaxTasksPerSession(int maxTasksPerSession) { this.maxTasksPerSession = maxTasksPerSession; }
    public String getGitRemote() { return gitRemote; }
    public void setGitRemote(String gitRemote) { this.gitRemote = gitRemote; }
    public String getForkOwner() { return forkOwner; }
    public void setForkOwner(String forkOwner) { this.forkOwner = forkOwner; }
    public String getGitUserName() { return gitUserName; }
    public void setGitUserName(String gitUserName) { this.gitUserName = gitUserName; }
    public String getGitcodeUsername() { return gitcodeUsername; }
    public void setGitcodeUsername(String gitcodeUsername) { this.gitcodeUsername = gitcodeUsername; }
    public String getGitcodeToken() { return gitcodeToken; }
    public void setGitcodeToken(String gitcodeToken) { this.gitcodeToken = gitcodeToken; }
    public String getGitcodeTokenEnv() { return gitcodeTokenEnv; }
    public void setGitcodeTokenEnv(String gitcodeTokenEnv) { this.gitcodeTokenEnv = gitcodeTokenEnv; }
    public String getCiGatePythonExecutable() { return ciGatePythonExecutable; }
    public void setCiGatePythonExecutable(String ciGatePythonExecutable) { this.ciGatePythonExecutable = ciGatePythonExecutable; }
    public String getCiGateInstallCommand() { return ciGateInstallCommand; }
    public void setCiGateInstallCommand(String ciGateInstallCommand) { this.ciGateInstallCommand = ciGateInstallCommand; }
    public List<String> getImmutableFiles() { return immutableFiles; }
    public void setImmutableFiles(List<String> immutableFiles) { this.immutableFiles = immutableFiles != null ? new ArrayList<>(immutableFiles) : new ArrayList<>(); }
    public List<String> getHighImpactPrefixes() { return new ArrayList<>(highImpactPrefixes); }
    public void setHighImpactPrefixes(List<String> highImpactPrefixes) { this.highImpactPrefixes = highImpactPrefixes != null ? new ArrayList<>(highImpactPrefixes) : new ArrayList<>(); }
    public List<String> getStageRegistrars() { return stageRegistrars; }
    public void setStageRegistrars(List<String> stageRegistrars) { this.stageRegistrars = stageRegistrars != null ? new ArrayList<>(stageRegistrars) : new ArrayList<>(); }
    public List<String> getPipelineRegistrars() { return pipelineRegistrars; }
    public void setPipelineRegistrars(List<String> pipelineRegistrars) { this.pipelineRegistrars = pipelineRegistrars != null ? new ArrayList<>(pipelineRegistrars) : new ArrayList<>(); }
    public String getWorkspace() { return workspace; }
    public void setWorkspace(String workspace) { this.workspace = workspace; }
    public String getConfigPath() { return configPath; }
    public void setConfigPath(String configPath) { this.configPath = configPath; }
    public boolean isConfigBootstrapped() { return configBootstrapped; }
    public void setConfigBootstrapped(boolean configBootstrapped) { this.configBootstrapped = configBootstrapped; }
    public String getSuggestedLocalRepo() { return suggestedLocalRepo; }
    public void setSuggestedLocalRepo(String suggestedLocalRepo) { this.suggestedLocalRepo = suggestedLocalRepo; }
    public double getTaskTimeoutSecs() { return taskTimeoutSecs; }
    public void setTaskTimeoutSecs(double taskTimeoutSecs) { this.taskTimeoutSecs = taskTimeoutSecs; }
    public String getOptimizationGoal() { return optimizationGoal; }
    public void setOptimizationGoal(String optimizationGoal) { this.optimizationGoal = optimizationGoal; }
    public String getCompetitor() { return competitor; }
    public void setCompetitor(String competitor) { this.competitor = competitor; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public int getSelfDrivenSlots() { return selfDrivenSlots; }
    public void setSelfDrivenSlots(int selfDrivenSlots) { this.selfDrivenSlots = selfDrivenSlots; }
    
    // Additional getters for orchestrator dependencies
    public double getCostLimitUsd() { return costLimitUsd; }
    public void setCostLimitUsd(double costLimitUsd) { this.costLimitUsd = costLimitUsd; }
    
    public int getFixPhase1MaxRetries() { return fixPhase1MaxRetries; }
    public void setFixPhase1MaxRetries(int fixPhase1MaxRetries) { this.fixPhase1MaxRetries = fixPhase1MaxRetries; }
    
    public int getFixPhase2MaxRetries() { return fixPhase2MaxRetries; }
    public void setFixPhase2MaxRetries(int fixPhase2MaxRetries) { this.fixPhase2MaxRetries = fixPhase2MaxRetries; }
    
    public String getGitBaseBranch() { return gitBaseBranch; }
    public void setGitBaseBranch(String gitBaseBranch) { this.gitBaseBranch = gitBaseBranch; }
    
    public String getUpstreamOwner() { return upstreamOwner; }
    public void setUpstreamOwner(String upstreamOwner) { this.upstreamOwner = upstreamOwner; }
    
    public String getUpstreamRepo() { return upstreamRepo; }
    public void setUpstreamRepo(String upstreamRepo) { this.upstreamRepo = upstreamRepo; }
    
    public String getGitUserEmail() { return gitUserEmail; }
    public void setGitUserEmail(String gitUserEmail) { this.gitUserEmail = gitUserEmail; }
    
    public String getCiGateConfig() { return ciGateConfig; }
    public void setCiGateConfig(String ciGateConfig) { this.ciGateConfig = ciGateConfig; }
    
    public String resolveGitcodeToken() {
        return resolveGitcodeToken(System.getenv());
    }

    public String resolveGitcodeToken(Map<String, String> env) {
        if (!gitcodeToken.isBlank()) {
            return gitcodeToken;
        }
        String envValue = env != null ? env.get(gitcodeTokenEnv) : null;
        return envValue != null ? envValue : "";
    }
    
    public String resolveCiGatePythonExecutable() {
        if (!ciGatePythonExecutable.isBlank()) {
            return ciGatePythonExecutable;
        }
        return "python";
    }
    
    public String getExperienceDir() { return experienceDir; }
    public void setExperienceDir(String experienceDir) { this.experienceDir = experienceDir; }
}
