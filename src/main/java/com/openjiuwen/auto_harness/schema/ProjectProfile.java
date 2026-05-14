package com.openjiuwen.auto_harness.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code ProjectProfile} in {@code openjiuwen.auto_harness.schema}.
 */
public class ProjectProfile {

    private String name = "agent-core";
    private String repoUrl = "https://gitcode.com/openJiuwen/agent-core.git";
    private String repoSlug = "openJiuwen/agent-core";
    private String platform = "gitcode";
    private List<String> immutableFiles = new ArrayList<>(List.of(
            "openjiuwen/auto_harness/prompts/identity.md",
            "openjiuwen/auto_harness/resources/ci_gate.yaml",
            "openjiuwen/harness/rails/security/prompt_security_rail.py"
    ));
    private List<String> highImpactPrefixes = new ArrayList<>(List.of("openjiuwen/core/"));
    private String defaultBaseBranch = "develop";
    private String defaultCiProfile = "default";

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getRepoSlug() { return repoSlug; }
    public void setRepoSlug(String repoSlug) { this.repoSlug = repoSlug; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public List<String> getImmutableFiles() { return immutableFiles; }
    public void setImmutableFiles(List<String> immutableFiles) { this.immutableFiles = immutableFiles != null ? new ArrayList<>(immutableFiles) : new ArrayList<>(); }
    public List<String> getHighImpactPrefixes() { return highImpactPrefixes; }
    public void setHighImpactPrefixes(List<String> highImpactPrefixes) { this.highImpactPrefixes = highImpactPrefixes != null ? new ArrayList<>(highImpactPrefixes) : new ArrayList<>(); }
    public String getDefaultBaseBranch() { return defaultBaseBranch; }
    public void setDefaultBaseBranch(String defaultBaseBranch) { this.defaultBaseBranch = defaultBaseBranch; }
    public String getDefaultCiProfile() { return defaultCiProfile; }
    public void setDefaultCiProfile(String defaultCiProfile) { this.defaultCiProfile = defaultCiProfile; }
}
