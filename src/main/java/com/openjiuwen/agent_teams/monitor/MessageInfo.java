/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

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
        // Placeholder: convert internal message model
        return new MessageInfo("", "", "", "", System.currentTimeMillis(), false);
    }
}