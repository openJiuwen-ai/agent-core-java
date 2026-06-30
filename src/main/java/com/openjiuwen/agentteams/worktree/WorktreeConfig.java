/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class WorktreeConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class WorktreeConfig {
    @Builder.Default
    private boolean isEnabled = false;
    private String baseDir;
    private List<String> sparsePaths;
    private List<String> symlinkDirectories;
    private List<String> includePatterns;
    @Builder.Default
    private int cleanupAfterDays = 30;
    @Builder.Default
    private boolean isAutoCleanupOnShutdown = true;
    @Builder.Default
    private WorktreeLifecyclePolicy lifecyclePolicy = WorktreeLifecyclePolicy.AUTO;
}
