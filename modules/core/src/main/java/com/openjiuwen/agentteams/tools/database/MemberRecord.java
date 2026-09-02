/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Member record persisted in the team database.
 *
 * <p>Mirrors Python {@code team_member} table row. Fields map 1:1 to the
 * database columns. The {@code role} field defaults to null/blank when unset;
 * callers that create teammates pass {@code TeamRole.TEAMMATE.value()}.
 * Used by {@code MemberDao.isHumanAgent} / {@code listHumanAgentNames} to
 * probe role straight from the DB row so the answer is always current.
 *
 * @since 1.0
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

    // Mirrors Python team_member.role column. Defaults to null/blank when
    // unset; callers that create teammates pass TeamRole.TEAMMATE.value().
    // Used by MemberDao.isHumanAgent / listHumanAgentNames to probe role
    // straight from the DB row so the answer is always current.
    private String role;
}
