/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Event published when a plan is approved or rejected.
 * <p>
 * Mirrors Python's {@code PlanApprovalEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PlanApprovalEvent extends BaseEventMessage {

    private boolean approved;
}
