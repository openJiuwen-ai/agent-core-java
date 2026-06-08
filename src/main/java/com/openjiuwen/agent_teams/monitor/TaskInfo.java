/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Task information.
 *
 * <p>Mirrors Python's {@code TaskInfo} in
 * {@code openjiuwen/agent_teams/monitor/models.py}.</p>
 */
public record TaskInfo(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("team_name") String teamName,
        String title,
        String content,
        String status,
        String assignee,
        @JsonProperty("updated_at") Long updatedAt
) {

    public static TaskInfo fromInternal(Object task) {
        return new TaskInfo(
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(task, "task_id")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(task, "team_name", "team_id")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(task, "title")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(task, "content")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(task, "status")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(task, "assignee", "assignee_member_name")),
                MonitorModelSupport.longObjectValue(MonitorModelSupport.firstPresent(task, "updated_at"))
        );
    }
}
