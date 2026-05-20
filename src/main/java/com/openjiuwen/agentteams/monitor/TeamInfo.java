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
 * Summary metadata for a monitored team instance.
 *
 * @since 1.0
 */
public class TeamInfo {
    private String teamId;
    private String name;
    private String leaderId;
    private String desc;
    private long created;
}
