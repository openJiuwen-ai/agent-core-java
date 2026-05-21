package com.openjiuwen.auto_harness.schema;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors the minimal configuration surface of Python's {@code AutoHarnessConfig}.
 */
public class AutoHarnessConfig {

    private String dataDir = "";
    private String localRepo = "";
    private String repoUrl = "https://gitcode.com/openJiuwen/agent-core.git";
    private List<String> skillsDirs = new ArrayList<>();
    private List<String> stageRegistrars = new ArrayList<>();
    private List<String> pipelineRegistrars = new ArrayList<>();
    private String language = "cn";
    private double sessionBudgetSecs = 3600.0;
    private double costLimitUsd = 10.0;
    private double taskTimeoutSecs = 1200.0;
    private double modelTimeoutSecs = 300.0;
    private int maxTasksPerSession = 3;
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

    public String getResolvedExperienceDir() {
        if (!experienceDir.isBlank()) {
            return experienceDir;
        }
        if (!dataDir.isBlank()) {
            return Path.of(dataDir, "experience").toString();
        }
        return ".auto_harness/experience/";
    }

    public String getWorktreesDir() {
        return dataDir.isBlank() ? ".auto_harness/worktrees/" : Path.of(dataDir, "worktrees").toString();
    }

    public String getRunsDir() {
        return dataDir.isBlank() ? ".auto_harness/runs/" : Path.of(dataDir, "runs").toString();
    }

    public String getCacheRepoDir() {
        String repoName = resolveRepoName();
        return dataDir.isBlank() ? ".auto_harness/repo/" + repoName : Path.of(dataDir, "repo", repoName).toString();
    }

    public String resolveRepoName() {
        String upstream = upstreamRepo != null ? upstreamRepo.trim() : "";
        if (!upstream.isBlank()) {
            return upstream;
        }
        String stem = Path.of(repoUrl.replaceAll("/+$", "")).getFileName().toString();
        return stem.endsWith(".git") ? stem.substring(0, stem.length() - 4) : stem;
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
    public String getLocalRepo() { return localRepo; }
    public void setLocalRepo(String localRepo) { this.localRepo = localRepo; }
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
    public String getGitcodeTokenEnv() { return gitcodeTokenEnv; }
    public void setGitcodeTokenEnv(String gitcodeTokenEnv) { this.gitcodeTokenEnv = gitcodeTokenEnv; }
    public String getCiGatePythonExecutable() { return ciGatePythonExecutable; }
    public void setCiGatePythonExecutable(String ciGatePythonExecutable) { this.ciGatePythonExecutable = ciGatePythonExecutable; }
    public String getCiGateInstallCommand() { return ciGateInstallCommand; }
    public void setCiGateInstallCommand(String ciGateInstallCommand) { this.ciGateInstallCommand = ciGateInstallCommand; }
    public List<String> getImmutableFiles() { return immutableFiles; }
    public void setImmutableFiles(List<String> immutableFiles) { this.immutableFiles = immutableFiles != null ? new ArrayList<>(immutableFiles) : new ArrayList<>(); }
    public List<String> getStageRegistrars() { return stageRegistrars; }
    public void setStageRegistrars(List<String> stageRegistrars) { this.stageRegistrars = stageRegistrars != null ? new ArrayList<>(stageRegistrars) : new ArrayList<>(); }
    public List<String> getPipelineRegistrars() { return pipelineRegistrars; }
    public void setPipelineRegistrars(List<String> pipelineRegistrars) { this.pipelineRegistrars = pipelineRegistrars != null ? new ArrayList<>(pipelineRegistrars) : new ArrayList<>(); }
    public String getWorkspace() { return workspace; }
    public void setWorkspace(String workspace) { this.workspace = workspace; }
    public boolean isConfigBootstrapped() { return configBootstrapped; }
    public void setConfigBootstrapped(boolean configBootstrapped) { this.configBootstrapped = configBootstrapped; }
    public String getSuggestedLocalRepo() { return suggestedLocalRepo; }
    public void setSuggestedLocalRepo(String suggestedLocalRepo) { this.suggestedLocalRepo = suggestedLocalRepo; }
    public double getTaskTimeoutSecs() { return taskTimeoutSecs; }
    
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
        if (!gitcodeToken.isBlank()) {
            return gitcodeToken;
        }
        String envValue = System.getenv(gitcodeTokenEnv);
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
