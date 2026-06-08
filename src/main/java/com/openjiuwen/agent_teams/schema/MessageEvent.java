/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Event published when a point-to-point message is sent.
 * <p>
 * Mirrors Python's {@code MessageEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MessageEvent extends BaseEventMessage {

    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("from_member_name")
    private String fromMemberName;

    @JsonProperty("to_member_name")
    private String toMemberName;
}
