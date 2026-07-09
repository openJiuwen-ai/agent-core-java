/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.TeamRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class TeamMember used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMember {
    private String memberName;
    private String displayName;
    private String description;
    private String prompt;
    @Builder.Default
    private TeamRole role = TeamRole.MEMBER;
    @Builder.Default
    private MemberStatus status = MemberStatus.READY;
}
