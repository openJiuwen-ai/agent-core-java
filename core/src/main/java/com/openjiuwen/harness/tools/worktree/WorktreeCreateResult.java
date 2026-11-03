/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Mirrors Python's {@code WorktreeCreateResult} in
 * {@code openjiuwen/harness/tools/worktree/models.py}.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class WorktreeCreateResult {

    @JsonProperty("worktree_path")
    private String worktreePath;

    @JsonProperty("worktree_branch")
    private String worktreeBranch;

    @JsonProperty("head_commit")
    private String headCommit;

    @JsonProperty("base_branch")
    private String baseBranch;

    private boolean existed = false;

    @JsonProperty("hook_based")
    private boolean hookBased = false;

    public WorktreeCreateResult(String worktreePath) {
        this.worktreePath = worktreePath;
    }

    public WorktreeCreateResult(
            String worktreePath,
            String worktreeBranch,
            String headCommit,
            String baseBranch,
            boolean existed,
            boolean hookBased
    ) {
        this.worktreePath = worktreePath;
        this.worktreeBranch = worktreeBranch;
        this.headCommit = headCommit;
        this.baseBranch = baseBranch;
        this.existed = existed;
        this.hookBased = hookBased;
    }
}
