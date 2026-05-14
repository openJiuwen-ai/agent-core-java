/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.status;

/**
 * Minimal member status enum.
 *
 * <p>Mirrors Python's {@code MemberStatus} in
 * {@code openjiuwen.agent_teams.schema.status}.
 */
public enum MemberStatus {
    UNSTARTED,
    READY,
    BUSY,
    RESTARTING,
    SHUTDOWN,
    ERROR
}
