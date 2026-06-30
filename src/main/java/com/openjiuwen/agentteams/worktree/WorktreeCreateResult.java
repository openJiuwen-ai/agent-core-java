/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class WorktreeCreateResult used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class WorktreeCreateResult {
    private String worktreePath;
    private String worktreeBranch;
    private String headCommit;
    private String baseBranch;
    @Builder.Default
    private boolean isExisted = false;
    @Builder.Default
    private boolean isHookBased = false;
}
