/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * File-level lock entry for team shared workspace coordination.
 *
 * <p>Mirrors Python's {@code WorkspaceFileLock} in
 * {@code openjiuwen/agent_teams/team_workspace/models.py}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkspaceFileLock {

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("holder_id")
    private String holderId;

    @JsonProperty("holder_name")
    private String holderName;

    @JsonProperty("acquired_at")
    private String acquiredAt;

    @JsonProperty("timeout_seconds")
    private int timeoutSeconds = 300;

    public boolean isExpired() {
        OffsetDateTime acquired = OffsetDateTime.parse(acquiredAt);
        return OffsetDateTime.now().isAfter(acquired.plusSeconds(timeoutSeconds));
    }
}
