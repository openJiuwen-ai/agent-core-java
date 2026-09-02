/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class TeamRuntimeContext used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamRuntimeContext {
    private String teamId;
    private String sessionId;
    private String memberName;
    @Builder.Default
    private TeamRole role = TeamRole.LEADER;
    @Builder.Default
    private TeamLifecycle lifecycle = TeamLifecycle.CREATED;
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
