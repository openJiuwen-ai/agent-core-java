/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.status;

/**
 * Minimal execution status enum.
 *
 * <p>Mirrors Python's {@code ExecutionStatus} in
 * {@code openjiuwen.agent_teams.schema.status}.
 */
public enum ExecutionStatus {
    IDLE,
    RUNNING,
    WAITING_APPROVAL,
    INTERRUPTED,
    COMPLETED
}
