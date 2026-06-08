/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Event published when a team is created.
 * <p>
 * Mirrors Python's {@code TeamCreatedEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TeamCreatedEvent extends BaseEventMessage {

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("leader_member_name")
    private String leaderMemberName;

    private int created;
}
