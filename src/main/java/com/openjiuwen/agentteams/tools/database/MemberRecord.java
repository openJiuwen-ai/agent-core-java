/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class MemberRecord used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberRecord {
    private String memberName;
    private String teamName;
    private String displayName;
    private String agentCard;
    private String status;
    private String desc;
    private String executionStatus;
    private String mode;
    private String prompt;
    private String modelRefJson;
    private long updatedAt;
}
