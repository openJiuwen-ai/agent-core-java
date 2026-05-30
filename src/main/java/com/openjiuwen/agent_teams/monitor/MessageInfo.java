/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.schema.message.MessageRecord;

import java.util.Map;

/**
 * Message info model.
 * <p>
 * Mirrors Python's {@code MessageInfo} in
 * {@code openjiuwen.agent_teams.monitor.models}.
 */
public class MessageInfo {

    private final String messageId;
    private final String fromMember;
    private final String toMember;
    private final String content;
    private final long sentAt;
    private final boolean read;

    public MessageInfo(String messageId, String fromMember, String toMember, String content, long sentAt, boolean read) {
        this.messageId = messageId;
        this.fromMember = fromMember;
        this.toMember = toMember;
        this.content = content;
        this.sentAt = sentAt;
        this.read = read;
    }

    public String getMessageId() { return messageId; }
    public String getFromMember() { return fromMember; }
    public String getToMember() { return toMember; }
    public String getContent() { return content; }
    public long getSentAt() { return sentAt; }
    public boolean isRead() { return read; }

    public static MessageInfo fromInternal(Object message) {
        if (message instanceof MessageRecord row) {
            return new MessageInfo(
                    row.getMessageId(),
                    row.getFromMemberName(),
                    row.getToMemberName(),
                    row.getContent(),
                    row.getCreatedAt(),
                    row.isRead()
            );
        }
        if (message instanceof Map<?, ?> map) {
            return new MessageInfo(
                    stringValue(firstPresent(map, "message_id", "messageId")),
                    stringValue(firstPresent(map, "from_member_name", "fromMemberName", "from_member", "fromMember")),
                    stringValue(firstPresent(map, "to_member_name", "toMemberName", "to_member", "toMember")),
                    stringValue(firstPresent(map, "content")),
                    longValue(firstPresent(map, "sent_at", "sentAt", "created_at", "createdAt")),
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
