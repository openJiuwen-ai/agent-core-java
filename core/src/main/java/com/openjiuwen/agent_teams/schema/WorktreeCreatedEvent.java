/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Published when a worktree is created or recovered.
 * <p>
 * Mirrors Python's {@code WorktreeCreatedEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WorktreeCreatedEvent extends BaseEventMessage {

    @JsonProperty("worktree_name")
    private String worktreeName;

    @JsonProperty("worktree_path")
    private String worktreePath;

    private boolean existed = false;
}
