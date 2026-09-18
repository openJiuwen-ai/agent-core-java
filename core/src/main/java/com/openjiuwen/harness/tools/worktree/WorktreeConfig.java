/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Mirrors Python's {@code WorktreeConfig} in
 * {@code openjiuwen/harness/tools/worktree/models.py}.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class WorktreeConfig {

    private boolean enabled = false;

    @JsonProperty("base_dir")
    private String baseDir;

    @JsonProperty("sparse_paths")
    private List<String> sparsePaths;

    @JsonProperty("symlink_directories")
    private List<String> symlinkDirectories;

    @JsonProperty("include_patterns")
    private List<String> includePatterns;

    @JsonProperty("cleanup_after_days")
    private int cleanupAfterDays = 30;

    @JsonProperty("auto_cleanup_on_shutdown")
    private boolean autoCleanupOnShutdown = true;

    @JsonProperty("lifecycle_policy")
    private WorktreeLifecyclePolicy lifecyclePolicy = WorktreeLifecyclePolicy.AUTO;

    public WorktreeConfig(
            boolean enabled,
            String baseDir,
            List<String> sparsePaths,
            List<String> symlinkDirectories,
            List<String> includePatterns,
            int cleanupAfterDays,
            boolean autoCleanupOnShutdown,
            WorktreeLifecyclePolicy lifecyclePolicy
    ) {
        this.enabled = enabled;
        this.baseDir = baseDir;
        this.sparsePaths = sparsePaths;
        this.symlinkDirectories = symlinkDirectories;
        this.includePatterns = includePatterns;
        this.cleanupAfterDays = cleanupAfterDays;
        this.autoCleanupOnShutdown = autoCleanupOnShutdown;
        this.lifecyclePolicy = lifecyclePolicy;
    }
}
