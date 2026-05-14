/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.task;

/**
 * Minimal task status enum.
 *
 * <p>Mirrors Python's {@code TaskStatus} in
 * {@code openjiuwen.agent_teams.schema.status}.
 */
public enum TaskStatus {
    PENDING,
    BLOCKED,
    CLAIMED,
    PLAN_APPROVED,
    COMPLETED,
    CANCELLED
}
