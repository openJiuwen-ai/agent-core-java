/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.schema.events.EventMessage;

import java.util.Map;
import java.util.Set;

/**
 * Monitor event types.
 * <p>
 * Mirrors Python's {@code MonitorEvent} in
 * {@code openjiuwen.agent_teams.monitor.models}.
 */
public class MonitorEvent {

    private static final Set<String> MONITOR_EVENT_TYPES = Set.of(
            "team_created",
            "team_cleaned",
            "team_standby",
            "member_spawned",
            "member_restarted",
            "member_status_changed",
            "member_execution_changed",
            "member_shutdown",
            "member_canceled",
            "task_created",
            "task_updated",
            "task_claimed",
            "task_completed",
            "task_cancelled",
            "task_unblocked",
            "message",
            "broadcast"
    );

    private final String eventType;
    private final String teamId;
    private final String sessionId;
    private final Object data;
    private final long timestamp;

    public MonitorEvent(String eventType, String teamId, String sessionId, Object data) {
        this.eventType = eventType;
        this.teamId = teamId;
        this.sessionId = sessionId;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public String getEventType() { return eventType; }
    public String getTeamId() { return teamId; }
    public String getSessionId() { return sessionId; }
    public Object getData() { return data; }
    public long getTimestamp() { return timestamp; }

    public static MonitorEvent fromEventMessage(Object eventMessage) {
        if (eventMessage instanceof EventMessage message) {
            if (!MONITOR_EVENT_TYPES.contains(message.getEventType())) {
                return null;
            }
            Map<String, Object> payload = message.getPayload();
            return new MonitorEvent(
                    message.getEventType(),
                    stringValue(firstPresent(payload, "team_name", "teamName", "team_id", "teamId")),
                    stringValue(firstPresent(payload, "session_id", "sessionId")),
                    payload
            );
        }
        if (eventMessage instanceof Map<?, ?> payload) {
            String eventType = stringValue(firstPresent(payload, "event_type", "eventType", "type"));
            if (!MONITOR_EVENT_TYPES.contains(eventType)) {
                return null;
            }
            return new MonitorEvent(
                    eventType,
                    stringValue(firstPresent(payload, "team_name", "teamName", "team_id", "teamId")),
                    stringValue(firstPresent(payload, "session_id", "sessionId")),
                    payload
            );
        }
        return new MonitorEvent("unknown", "", "", eventMessage);
    }

    private static Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }
}
