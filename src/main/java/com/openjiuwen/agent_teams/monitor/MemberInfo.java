/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Snapshot of a team member status used by team-monitor query and event views.
 *
 * @since 1.0
 */
public class MemberInfo {
    private String memberId;
    private String teamId;
    private String name;
    private String desc;
    private String status;
    private String executionStatus;
    private String mode;
}
