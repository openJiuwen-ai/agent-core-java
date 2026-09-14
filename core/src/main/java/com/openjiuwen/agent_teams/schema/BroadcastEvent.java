/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Event published when a broadcast message is sent.
 * <p>
 * Mirrors Python's {@code BroadcastEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BroadcastEvent extends BaseEventMessage {

    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("from_member_name")
    private String fromMemberName;
}
