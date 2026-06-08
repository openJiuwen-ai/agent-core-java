/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mailbox message information.
 *
 * <p>Mirrors Python's {@code MessageInfo} in
 * {@code openjiuwen/agent_teams/monitor/models.py}.</p>
 */
public record MessageInfo(
        @JsonProperty("message_id") String messageId,
        @JsonProperty("team_name") String teamName,
        @JsonProperty("from_member_name") String fromMemberName,
        @JsonProperty("to_member_name") String toMemberName,
        String content,
        long timestamp,
        boolean broadcast,
        @JsonProperty("is_read") boolean isRead
) {

    public static MessageInfo fromInternal(Object message) {
        return new MessageInfo(
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(message, "message_id")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(message, "team_name", "team_id")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(message, "from_member_name", "from_member")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(message, "to_member_name", "to_member")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(message, "content")),
                MonitorModelSupport.longValue(MonitorModelSupport.firstPresent(message, "timestamp", "created_at"), 0L),
                Boolean.TRUE.equals(MonitorModelSupport.booleanValue(MonitorModelSupport.firstPresent(message, "broadcast"))),
                Boolean.TRUE.equals(MonitorModelSupport.booleanValue(MonitorModelSupport.firstPresent(message, "is_read", "read")))
        );
    }
}
