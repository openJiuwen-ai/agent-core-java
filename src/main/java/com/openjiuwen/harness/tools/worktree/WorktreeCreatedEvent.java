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
 * Mirrors Python's {@code WorktreeCreatedEvent} in
 * {@code openjiuwen/harness/tools/worktree/events.py}.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class WorktreeCreatedEvent implements WorktreeEvent {

    @JsonProperty("worktree_name")
    private String worktreeName;

    @JsonProperty("worktree_path")
    private String worktreePath;

    @JsonProperty("owner_id")
    private String ownerId;

    private String tag;

    private boolean existed = false;

    public WorktreeCreatedEvent(
            String worktreeName,
            String worktreePath,
            String ownerId,
            String tag,
            boolean existed
    ) {
        this.worktreeName = worktreeName;
        this.worktreePath = worktreePath;
        this.ownerId = ownerId;
        this.tag = tag;
        this.existed = existed;
    }
}
