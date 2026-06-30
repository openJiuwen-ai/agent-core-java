/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class TeamRecord used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class TeamRecord {
    private String teamName;
    private String displayName;
    private String leaderMemberName;
    private String desc;
    private String prompt;
    private long created;
    private long updatedAt;
}
