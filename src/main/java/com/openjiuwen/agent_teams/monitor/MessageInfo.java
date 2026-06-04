/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.schema.message.MessageRecord;
import com.openjiuwen.agent_teams.tools.TeamMessage;

import java.util.Map;

/**
 * Message info model.
 * <p>
 * Mirrors Python's {@code MessageInfo} in
 * {@code openjiuwen.agent_teams.monitor.models}.
 */
public class MessageInfo {

    private final String messageId;
    private final String teamId;
    private final String fromMember;
    private final String toMember;
    private final String content;
    private final long timestamp;
    private final boolean broadcast;
    private final boolean read;

    public MessageInfo(String messageId, String fromMember, String toMember, String content, long sentAt, boolean read) {
        this(messageId, "", fromMember, toMember, content, sentAt, false, read);
    }

    public MessageInfo(
            String messageId,
            String teamId,
            String fromMember,
            String toMember,
            String content,
            long timestamp,
            boolean broadcast,
            boolean read
    ) {
        this.messageId = messageId;
        this.teamId = teamId;
        this.fromMember = fromMember;
        this.toMember = toMember;
        this.content = content;
        this.timestamp = timestamp;
        this.broadcast = broadcast;
        this.read = read;
    }

    public String getMessageId() { return messageId; }
    public String getTeamId() { return teamId; }
    public String getFromMember() { return fromMember; }
    public String getToMember() { return toMember; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public long getSentAt() { return timestamp; }
    public boolean isBroadcast() { return broadcast; }
    public boolean isRead() { return read; }

    public static MessageInfo fromInternal(Object message) {
        if (message instanceof MessageRecord row) {
            return new MessageInfo(
                    row.getMessageId(),
                    row.getTeamName(),
                    row.getFromMemberName(),
                    row.getToMemberName(),
                    row.getContent(),
                    row.getCreatedAt(),
                    row.isBroadcast(),
                    row.isRead()
            );
        }
        if (message instanceof TeamMessage row) {
            return new MessageInfo(
                    row.getMessageId(),
                    row.getTeamName(),
                    row.getFromMemberName(),
                    row.getToMemberName(),
                    row.getContent(),
                    longValue(row.getTimestamp()),
                    booleanValue(row.getBroadcast()),
                    booleanValue(row.getIsRead())
            );
        }
        if (message instanceof Map<?, ?> map) {
            return new MessageInfo(
                    stringValue(firstPresent(map, "message_id", "messageId")),
                    stringValue(firstPresent(map, "team_name", "teamName", "team_id", "teamId")),
                    stringValue(firstPresent(map, "from_member_name", "fromMemberName", "from_member", "fromMember")),
                    stringValue(firstPresent(map, "to_member_name", "toMemberName", "to_member", "toMember")),
                    stringValue(firstPresent(map, "content")),
                    longValue(firstPresent(map, "timestamp", "sent_at", "sentAt", "created_at", "createdAt")),
                    booleanValue(firstPresent(map, "broadcast", "is_broadcast", "isBroadcast")),
                    booleanValue(firstPresent(map, "read", "is_read", "isRead"))
            );
        }
        return null;
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

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
