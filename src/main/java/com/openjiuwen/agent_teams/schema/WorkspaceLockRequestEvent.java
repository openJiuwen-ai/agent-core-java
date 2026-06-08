/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Lock request sent from remote node to leader.
 * <p>
 * Mirrors Python's {@code WorkspaceLockRequestEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WorkspaceLockRequestEvent extends BaseEventMessage {

    private String action;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("holder_name")
    private String holderName;

    @JsonProperty("timeout_seconds")
    private Integer timeoutSeconds;
}
