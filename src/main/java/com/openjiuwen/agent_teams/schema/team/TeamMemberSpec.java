/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class TeamMemberSpec used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class TeamMemberSpec {
    private String name;
    @Builder.Default
    private TeamRole role = TeamRole.MEMBER;
    @Builder.Default
    private String description = "";
    @Builder.Default
    private String agentId = "";
    @Builder.Default
    private String modelId = "";
    @Builder.Default
    private String modelName = "";
}
