/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class WorktreeSession used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorktreeSession {
    private String originalCwd;
    private String worktreePath;
    private String worktreeName;
    private String worktreeBranch;
    private String originalBranch;
    private String originalHeadCommit;
    private String memberName;
    private String teamName;
    @Builder.Default
    private boolean isHookBased = false;
    @Builder.Default
    private WorktreeLifecyclePolicy lifecyclePolicy = WorktreeLifecyclePolicy.AUTO;
    private String teamLifecycle;
    private Double creationDurationMs;
    @Builder.Default
    private boolean isUsedSparsePaths = false;
}
