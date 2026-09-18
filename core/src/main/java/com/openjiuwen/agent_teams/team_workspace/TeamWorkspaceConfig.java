/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for a team shared workspace.
 *
 * <p>Mirrors Python's {@code TeamWorkspaceConfig} in
 * {@code openjiuwen/agent_teams/team_workspace/models.py}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamWorkspaceConfig {

    private boolean enabled = false;

    @JsonProperty("root_path")
    private String rootPath;

    @JsonProperty("artifact_dirs")
    private List<String> artifactDirs = new ArrayList<>(
            List.of("artifacts/code", "artifacts/docs", "artifacts/reports", "trajectories")
    );

    @JsonProperty("version_control")
    private boolean versionControl = true;

    @JsonProperty("conflict_strategy")
    private ConflictStrategy conflictStrategy = ConflictStrategy.LOCK;

    @JsonProperty("remote_url")
    private String remoteUrl;
}
