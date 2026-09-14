/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Event published when the whole team has reached a completed state.
 * <p>
 * Mirrors Python's {@code TeamCompletedEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TeamCompletedEvent extends BaseEventMessage {

    @JsonProperty("member_count")
    private int memberCount;

    @JsonProperty("task_count")
    private int taskCount;
}
