/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Published when a merge conflict or push failure is detected.
 * <p>
 * Mirrors Python's {@code WorkspaceConflictEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WorkspaceConflictEvent extends BaseEventMessage {

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("conflicting_commit")
    private String conflictingCommit;
}
