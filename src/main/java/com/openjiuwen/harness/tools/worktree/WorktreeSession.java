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
 * Mirrors Python's {@code WorktreeSession} in
 * {@code openjiuwen/harness/tools/worktree/models.py}.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class WorktreeSession {

    @JsonProperty("original_cwd")
    private String originalCwd;

    @JsonProperty("worktree_path")
    private String worktreePath;

    @JsonProperty("worktree_name")
    private String worktreeName;

    @JsonProperty("worktree_branch")
    private String worktreeBranch;

    @JsonProperty("original_branch")
    private String originalBranch;

    @JsonProperty("original_head_commit")
    private String originalHeadCommit;

    @JsonProperty("member_name")
    private String memberName;

    @JsonProperty("team_name")
    private String teamName;

    @JsonProperty("hook_based")
    private boolean hookBased = false;

    @JsonProperty("lifecycle_policy")
    private WorktreeLifecyclePolicy lifecyclePolicy = WorktreeLifecyclePolicy.AUTO;

    @JsonProperty("team_lifecycle")
    private String teamLifecycle;

    @JsonProperty("creation_duration_ms")
    private Double creationDurationMs;

    @JsonProperty("used_sparse_paths")
    private boolean usedSparsePaths = false;

    public WorktreeSession(String originalCwd, String worktreePath, String worktreeName) {
        this.originalCwd = originalCwd;
        this.worktreePath = worktreePath;
        this.worktreeName = worktreeName;
    }

    public WorktreeSession(
            String originalCwd,
            String worktreePath,
            String worktreeName,
            String worktreeBranch,
            String originalBranch,
            String originalHeadCommit,
            String memberName,
            String teamName,
            boolean hookBased,
            WorktreeLifecyclePolicy lifecyclePolicy,
            String teamLifecycle,
            Double creationDurationMs,
            boolean usedSparsePaths
    ) {
        this.originalCwd = originalCwd;
        this.worktreePath = worktreePath;
        this.worktreeName = worktreeName;
        this.worktreeBranch = worktreeBranch;
        this.originalBranch = originalBranch;
        this.originalHeadCommit = originalHeadCommit;
        this.memberName = memberName;
        this.teamName = teamName;
        this.hookBased = hookBased;
        this.lifecyclePolicy = lifecyclePolicy;
        this.teamLifecycle = teamLifecycle;
        this.creationDurationMs = creationDurationMs;
        this.usedSparsePaths = usedSparsePaths;
    }
}
