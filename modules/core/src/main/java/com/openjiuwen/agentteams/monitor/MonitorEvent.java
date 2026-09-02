/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.monitor;

import com.openjiuwen.agentteams.schema.events.EventMessage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized monitor event payload converted from runtime event messages.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorEvent {
    private MonitorEventType eventType;
    private String teamId;
    private String memberId;
    private long timestamp;
    private String name;
    private String leaderId;
    private Long created;
    private String oldStatus;
    private String newStatus;
    private String reason;
    private Integer restartCount;
    private Boolean force;
    private String taskId;
    private String status;
    private String messageId;
    private String fromMember;
    private String toMember;

    /**
     * fromEventMessage.
     * 
     * @param message message
     * @param teamId teamId
     * @return the result
     * @since 0.1.7
     */
    public static MonitorEvent fromEventMessage(EventMessage message, String teamId) {
        if (message == null || message.getEventType() == null) {
            return nullValue();
        }
        MonitorEventType type = switch (message.getEventType()) {
            case "team_created" -> MonitorEventType.TEAM_CREATED;
            case "team_cleaned" -> MonitorEventType.TEAM_CLEANED;
            case "team_standby" -> MonitorEventType.TEAM_STANDBY;
            case "member_spawned" -> MonitorEventType.MEMBER_SPAWNED;
            case "member_restarted" -> MonitorEventType.MEMBER_RESTARTED;
            case "member_status_changed" -> MonitorEventType.MEMBER_STATUS_CHANGED;
            case "member_execution_changed" -> MonitorEventType.MEMBER_EXECUTION_CHANGED;
            case "member_shutdown" -> MonitorEventType.MEMBER_SHUTDOWN;
            case "member_canceled" -> MonitorEventType.MEMBER_CANCELED;
            case "task_created" -> MonitorEventType.TASK_CREATED;
            case "task_updated" -> MonitorEventType.TASK_UPDATED;
            case "task_claimed" -> MonitorEventType.TASK_CLAIMED;
            case "task_completed" -> MonitorEventType.TASK_COMPLETED;
            case "task_cancelled" -> MonitorEventType.TASK_CANCELLED;
            case "task_unblocked" -> MonitorEventType.TASK_UNBLOCKED;
            case "message" -> MonitorEventType.MESSAGE;
            case "broadcast" -> MonitorEventType.BROADCAST;
            default -> null;
        };
        if (type == null) {
            return nullValue();
        }
        java.util.Map<String, Object> payload =
            message.getPayload() != null ? message.getPayload() : java.util.Map.of();
        return MonitorEvent.builder().eventType(type).teamId(stringValue(payload.get("team_name"), teamId))
                .memberId(stringValue(payload.get("member_name"), null)).timestamp(System.currentTimeMillis())
                .name(stringValue(payload.get("display_name"), null))
                .leaderId(stringValue(payload.get("leader_member_name"), null))
                .created(longValue(payload.get("created"))).oldStatus(stringValue(payload.get("old_status"), null))
                .newStatus(stringValue(payload.get("new_status"), null))
                .reason(stringValue(payload.get("reason"), null)).restartCount(intValue(payload.get("restart_count")))
                .force(booleanValue(payload.get("force"))).taskId(stringValue(payload.get("task_id"), null))
                .status(stringValue(payload.get("status"), null))
                .messageId(stringValue(payload.get("message_id"), null))
                .fromMember(stringValue(payload.get("from_member_name"), null))
                .toMember(stringValue(payload.get("to_member_name"), null)).build();
    }

    /**
     * stringValue.
     * 
     * @param value value
     * @param fallback fallback
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(Object value, String fallback) {
        return value != null ? String.valueOf(value) : fallback;
    }

    /**
     * longValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return nullValue();
    }

    /**
     * intValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return nullValue();
    }

    /**
     * booleanValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return nullValue();
    }

    /**
     * nullValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static <T> T nullValue() {
        return null;
    }
}
