/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Event published when a team is cleaned up.
 * <p>
 * Mirrors Python's {@code TeamCleanedEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TeamCleanedEvent extends BaseEventMessage {
}
