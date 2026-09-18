/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

/**
 * Observable event types exposed by the monitor.
 *
 * <p>Mirrors Python's {@code MonitorEventType} in
 * {@code openjiuwen/agent_teams/monitor/models.py}.</p>
 */
public enum MonitorEventType {
    TEAM_CREATED("team_created"),
    TEAM_CLEANED("team_cleaned"),
    TEAM_STANDBY("team_standby"),
    MEMBER_SPAWNED("member_spawned"),
    MEMBER_RESTARTED("member_restarted"),
    MEMBER_STATUS_CHANGED("member_status_changed"),
    MEMBER_EXECUTION_CHANGED("member_execution_changed"),
    MEMBER_SHUTDOWN("member_shutdown"),
    MEMBER_CANCELED("member_canceled"),
    TASK_CREATED("task_created"),
    TASK_PLAN_REQUEST("task_plan_request"),
    TASK_PLAN_RESPONSE("task_plan_response"),
    TASK_UPDATED("task_updated"),
    TASK_CLAIMED("task_claimed"),
    TASK_COMPLETED("task_completed"),
    TASK_CANCELLED("task_cancelled"),
    TASK_UNBLOCKED("task_unblocked"),
    MESSAGE("message"),
    BROADCAST("broadcast");

    private final String value;

    MonitorEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static MonitorEventType fromValue(String value) {
        for (MonitorEventType eventType : values()) {
            if (eventType.value.equals(value)) {
                return eventType;
            }
        }
        return null;
    }
}
