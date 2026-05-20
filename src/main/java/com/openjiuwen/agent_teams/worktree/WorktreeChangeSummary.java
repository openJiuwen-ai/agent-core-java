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
 * Public class WorktreeChangeSummary used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class WorktreeChangeSummary {
    @Builder.Default
    private int changedFiles = 0;
    @Builder.Default
    private int commits = 0;
}
