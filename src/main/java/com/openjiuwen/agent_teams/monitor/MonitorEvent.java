/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.schema.events.EventMessage;

import java.util.LinkedHashMap;
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
    private final String memberId;
    private final String sessionId;
    private final Object data;
    private final long timestamp;
    private final String name;
    private final String leaderId;
    private final Long created;
    private final String oldStatus;
    private final String newStatus;
    private final String reason;
    private final Integer restartCount;
    private final Boolean force;
    private final String taskId;
    private final String status;
    private final String messageId;
    private final String fromMember;
    private final String toMember;

    public MonitorEvent(String eventType, String teamId, String sessionId, Object data) {
        this(
                eventType,
                teamId,
                nullableStringValue(firstPresent(asMap(data), "member_name", "memberName", "member_id", "memberId")),
                sessionId,
                data,
                System.currentTimeMillis(),
                nullableStringValue(firstPresent(asMap(data), "display_name", "displayName", "name")),
                nullableStringValue(firstPresent(asMap(data), "leader_member_name", "leaderMemberName", "leader_id", "leaderId")),
                longObjectValue(firstPresent(asMap(data), "created", "created_at", "createdAt")),
                nullableStringValue(firstPresent(asMap(data), "old_status", "oldStatus")),
                nullableStringValue(firstPresent(asMap(data), "new_status", "newStatus")),
                nullableStringValue(firstPresent(asMap(data), "reason")),
                integerValue(firstPresent(asMap(data), "restart_count", "restartCount")),
                booleanObjectValue(firstPresent(asMap(data), "force")),
                nullableStringValue(firstPresent(asMap(data), "task_id", "taskId")),
                nullableStringValue(firstPresent(asMap(data), "status")),
                nullableStringValue(firstPresent(asMap(data), "message_id", "messageId")),
                nullableStringValue(firstPresent(asMap(data), "from_member_name", "fromMemberName", "from_member", "fromMember")),
                nullableStringValue(firstPresent(asMap(data), "to_member_name", "toMemberName", "to_member", "toMember"))
        );
    }

    private MonitorEvent(
            String eventType,
            String teamId,
            String memberId,
            String sessionId,
            Object data,
            long timestamp,
            String name,
            String leaderId,
            Long created,
            String oldStatus,
            String newStatus,
            String reason,
            Integer restartCount,
            Boolean force,
            String taskId,
            String status,
            String messageId,
            String fromMember,
            String toMember
    ) {
        this.eventType = eventType;
        this.teamId = teamId;
        this.memberId = memberId;
        this.sessionId = sessionId;
        this.data = data;
        this.timestamp = timestamp;
        this.name = name;
        this.leaderId = leaderId;
        this.created = created;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.restartCount = restartCount;
        this.force = force;
        this.taskId = taskId;
        this.status = status;
        this.messageId = messageId;
        this.fromMember = fromMember;
        this.toMember = toMember;
    }

    public String getEventType() { return eventType; }
    public String getTeamId() { return teamId; }
    public String getMemberId() { return memberId; }
    public String getSessionId() { return sessionId; }
    public Object getData() { return data; }
    public long getTimestamp() { return timestamp; }
    public String getName() { return name; }
    public String getLeaderId() { return leaderId; }
    public Long getCreated() { return created; }
    public String getOldStatus() { return oldStatus; }
    public String getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public Integer getRestartCount() { return restartCount; }
    public Boolean getForce() { return force; }
    public String getTaskId() { return taskId; }
    public String getStatus() { return status; }
    public String getMessageId() { return messageId; }
    public String getFromMember() { return fromMember; }
    public String getToMember() { return toMember; }

    public static MonitorEvent fromEventMessage(Object eventMessage) {
        if (eventMessage instanceof EventMessage message) {
            String eventType = normalizeEventType(message.getEventType());
            if (!MONITOR_EVENT_TYPES.contains(eventType)) {
                return null;
            }
            Map<String, Object> payload = message.getPayload();
            return fromPayload(eventType, payload, stringValue(firstPresent(payload, "session_id", "sessionId")), payload);
        }
        if (eventMessage instanceof Map<?, ?> envelope) {
            String eventType = normalizeEventType(stringValue(firstPresent(envelope, "event_type", "eventType", "type")));
            if (!MONITOR_EVENT_TYPES.contains(eventType)) {
                return null;
            }
            Object payloadObject = firstPresent(envelope, "payload", "data");
            Map<?, ?> payload = payloadObject instanceof Map<?, ?> map ? map : envelope;
            String sessionId = stringValue(firstPresent(envelope, "session_id", "sessionId"));
            if (sessionId.isEmpty()) {
                sessionId = stringValue(firstPresent(payload, "session_id", "sessionId"));
            }
            Object data = payloadObject instanceof Map<?, ?> ? copyMap(payload) : copyMap(envelope);
            return fromPayload(eventType, payload, sessionId, data);
        }
        return null;
    }

    private static MonitorEvent fromPayload(String eventType, Map<?, ?> payload, String sessionId, Object data) {
        return new MonitorEvent(
                eventType,
                stringValue(firstPresent(payload, "team_name", "teamName", "team_id", "teamId")),
                nullableStringValue(firstPresent(payload, "member_name", "memberName", "member_id", "memberId")),
                sessionId,
                data,
                System.currentTimeMillis(),
                nullableStringValue(firstPresent(payload, "display_name", "displayName", "name")),
                nullableStringValue(firstPresent(payload, "leader_member_name", "leaderMemberName", "leader_id", "leaderId")),
                longObjectValue(firstPresent(payload, "created", "created_at", "createdAt")),
                nullableStringValue(firstPresent(payload, "old_status", "oldStatus")),
                nullableStringValue(firstPresent(payload, "new_status", "newStatus")),
                nullableStringValue(firstPresent(payload, "reason")),
                integerValue(firstPresent(payload, "restart_count", "restartCount")),
                booleanObjectValue(firstPresent(payload, "force")),
                nullableStringValue(firstPresent(payload, "task_id", "taskId")),
                nullableStringValue(firstPresent(payload, "status")),
                nullableStringValue(firstPresent(payload, "message_id", "messageId")),
                nullableStringValue(firstPresent(payload, "from_member_name", "fromMemberName", "from_member", "fromMember")),
                nullableStringValue(firstPresent(payload, "to_member_name", "toMemberName", "to_member", "toMember"))
        );
    }

    private static String normalizeEventType(String eventType) {
        return switch (eventType) {
            case "direct_message" -> "message";
            case "broadcast_message" -> "broadcast";
            default -> eventType;
        };
    }

    private static Object firstPresent(Map<?, ?> map, String... keys) {
        if (map == null) {
            return null;
        }
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

    private static String nullableStringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static Long longObjectValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Boolean booleanObjectValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null ? Boolean.parseBoolean(String.valueOf(value)) : null;
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static Map<String, Object> copyMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (map != null) {
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        }
        return copy;
    }
}
