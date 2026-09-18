/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Lock response from leader to requesting node.
 * <p>
 * Mirrors Python's {@code WorkspaceLockResponseEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WorkspaceLockResponseEvent extends BaseEventMessage {

    @JsonProperty("file_path")
    private String filePath;

    private boolean granted;
    private Map<String, Object> holder;
}
