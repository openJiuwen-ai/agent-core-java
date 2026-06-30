/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.monitor;

/**
 * Event types emitted by team runtime and consumed by monitor subscribers.
 *
 * @since 1.0
 */
public enum MonitorEventType {
    TEAM_CREATED,
    TEAM_CLEANED,
    TEAM_STANDBY,
    MEMBER_SPAWNED,
    MEMBER_RESTARTED,
    MEMBER_STATUS_CHANGED,
    MEMBER_EXECUTION_CHANGED,
    MEMBER_SHUTDOWN,
    MEMBER_CANCELED,
    TASK_CREATED,
    TASK_UPDATED,
    TASK_CLAIMED,
    TASK_COMPLETED,
    TASK_CANCELLED,
    TASK_UNBLOCKED,
    MESSAGE,
    BROADCAST
}
