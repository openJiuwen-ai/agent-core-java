/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Event published when a member process is restarted after failure.
 * <p>
 * Mirrors Python's {@code MemberRestartedEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MemberRestartedEvent extends BaseEventMessage {

    private String reason = "health_check_failure";

    @JsonProperty("restart_count")
    private int restartCount = 1;
}
