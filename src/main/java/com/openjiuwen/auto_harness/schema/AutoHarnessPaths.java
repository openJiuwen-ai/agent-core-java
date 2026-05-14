package com.openjiuwen.auto_harness.schema;

/**
 * Mirrors Python's {@code AutoHarnessPaths} in {@code openjiuwen.auto_harness.schema}.
 */
public class AutoHarnessPaths {
    private String dataDir = "";
    private String experienceDir = "";
    private String worktreesDir = "";
    private String runsDir = "";
    private String cacheRepoDir = "";

    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public String getExperienceDir() { return experienceDir; }
    public void setExperienceDir(String experienceDir) { this.experienceDir = experienceDir; }
    public String getWorktreesDir() { return worktreesDir; }
    public void setWorktreesDir(String worktreesDir) { this.worktreesDir = worktreesDir; }
    public String getRunsDir() { return runsDir; }
    public void setRunsDir(String runsDir) { this.runsDir = runsDir; }
    public String getCacheRepoDir() { return cacheRepoDir; }
    public void setCacheRepoDir(String cacheRepoDir) { this.cacheRepoDir = cacheRepoDir; }
}
