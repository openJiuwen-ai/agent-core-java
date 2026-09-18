/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.Map;

/**
 * Real-time event emitted by the monitor.
 *
 * <p>Mirrors Python's {@code MonitorEvent} in
 * {@code openjiuwen/agent_teams/monitor/models.py}.</p>
 */
public record MonitorEvent(
        @JsonProperty("event_type") MonitorEventType eventType,
        @JsonProperty("team_name") String teamName,
        @JsonProperty("member_name") String memberName,
        long timestamp,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("leader_member_name") String leaderMemberName,
        Long created,
        @JsonProperty("old_status") String oldStatus,
        @JsonProperty("new_status") String newStatus,
        String reason,
        @JsonProperty("restart_count") Integer restartCount,
        Boolean force,
        @JsonProperty("task_id") String taskId,
        String status,
        @JsonProperty("plan_id") String planId,
        @JsonProperty("member_plan_md") String memberPlanMd,
        Boolean approved,
        @JsonProperty("message_id") String messageId,
        @JsonProperty("from_member_name") String fromMemberName,
        @JsonProperty("to_member_name") String toMemberName
) {

    public static MonitorEvent fromEventMessage(Object eventMessage) {
        String rawType;
        Map<?, ?> payload;
        if (eventMessage instanceof EventMessage message) {
            rawType = message.getEventType();
            payload = message.getPayloadData();
        } else if (eventMessage instanceof Map<?, ?> map) {
            rawType = MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(map, "event_type", "eventType", "type"));
            Object payloadValue = MonitorModelSupport.firstPresent(map, "payload", "payloadData", "data");
            payload = payloadValue instanceof Map<?, ?> nested ? nested : map;
        } else {
            return null;
        }

        MonitorEventType eventType = MonitorEventType.fromValue(rawType);
        if (eventType == null) {
            return null;
        }

        return new MonitorEvent(
                eventType,
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "team_name", "team_id")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "member_name", "member_id")),
                System.currentTimeMillis(),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "display_name")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "leader_member_name")),
                MonitorModelSupport.longObjectValue(MonitorModelSupport.firstPresent(payload, "created")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "old_status")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "new_status")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "reason")),
                MonitorModelSupport.integerValue(MonitorModelSupport.firstPresent(payload, "restart_count")),
                MonitorModelSupport.booleanValue(MonitorModelSupport.firstPresent(payload, "force")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "task_id")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "status")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "plan_id")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "member_plan_md")),
                MonitorModelSupport.booleanValue(MonitorModelSupport.firstPresent(payload, "approved")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "message_id")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "from_member_name")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(payload, "to_member_name"))
        );
    }
}
