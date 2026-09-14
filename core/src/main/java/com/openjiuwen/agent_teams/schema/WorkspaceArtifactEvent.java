/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Published when a workspace artifact is created or updated.
 * <p>
 * Mirrors Python's {@code WorkspaceArtifactEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WorkspaceArtifactEvent extends BaseEventMessage {

    @JsonProperty("artifact_path")
    private String artifactPath;

    @JsonProperty("commit_sha")
    private String commitSha;
}
