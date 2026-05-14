/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal worktree configuration.
 *
 * <p>Mirrors Python's {@code WorktreeConfig} in
 * {@code openjiuwen.agent_teams.worktree.models}.</p>
 */
public class WorktreeConfig {

    private boolean enabled;
    private String baseDir;
    private List<String> sparsePaths;
    private List<String> symlinkDirectories;
    private List<String> includePatterns;
    private int cleanupAfterDays = 30;
    private boolean autoCleanupOnShutdown = true;
    private WorktreeLifecyclePolicy lifecyclePolicy = WorktreeLifecyclePolicy.AUTO;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public List<String> getSparsePaths() {
        return sparsePaths != null ? new ArrayList<>(sparsePaths) : null;
    }

    public void setSparsePaths(List<String> sparsePaths) {
        this.sparsePaths = sparsePaths != null ? new ArrayList<>(sparsePaths) : null;
    }

    public List<String> getSymlinkDirectories() {
        return symlinkDirectories != null ? new ArrayList<>(symlinkDirectories) : null;
    }

    public void setSymlinkDirectories(List<String> symlinkDirectories) {
        this.symlinkDirectories = symlinkDirectories != null ? new ArrayList<>(symlinkDirectories) : null;
    }

    public List<String> getIncludePatterns() {
        return includePatterns != null ? new ArrayList<>(includePatterns) : null;
    }

    public void setIncludePatterns(List<String> includePatterns) {
        this.includePatterns = includePatterns != null ? new ArrayList<>(includePatterns) : null;
    }

    public int getCleanupAfterDays() {
        return cleanupAfterDays;
    }

    public void setCleanupAfterDays(int cleanupAfterDays) {
        this.cleanupAfterDays = cleanupAfterDays;
    }

    public boolean isAutoCleanupOnShutdown() {
        return autoCleanupOnShutdown;
    }

    public void setAutoCleanupOnShutdown(boolean autoCleanupOnShutdown) {
        this.autoCleanupOnShutdown = autoCleanupOnShutdown;
    }

    public WorktreeLifecyclePolicy getLifecyclePolicy() {
        return lifecyclePolicy;
    }

    public void setLifecyclePolicy(WorktreeLifecyclePolicy lifecyclePolicy) {
        this.lifecyclePolicy = lifecyclePolicy;
    }
}
