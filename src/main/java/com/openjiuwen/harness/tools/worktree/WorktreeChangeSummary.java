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
 * Mirrors Python's {@code WorktreeChangeSummary} in
 * {@code openjiuwen/harness/tools/worktree/models.py}.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class WorktreeChangeSummary {

    @JsonProperty("changed_files")
    private int changedFiles = 0;

    private int commits = 0;

    public WorktreeChangeSummary(int changedFiles, int commits) {
        this.changedFiles = changedFiles;
        this.commits = commits;
    }
}
