/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.message;

/**
 * Minimal in-memory message record.
 *
 * <p>Mirrors the core runtime message shape used by Python's
 * {@code TeamMessageManager} and message database models.
 */
public class MessageRecord {

    private final String messageId;
    private final String teamName;
    private final String fromMemberName;
    private final String toMemberName;
    private final String content;
    private final boolean broadcast;
    private boolean read;
    private final long createdAt;

    public MessageRecord(
            String messageId,
            String teamName,
            String fromMemberName,
            String toMemberName,
            String content,
            boolean broadcast,
            boolean read
    ) {
        this.messageId = messageId;
        this.teamName = teamName;
        this.fromMemberName = fromMemberName;
        this.toMemberName = toMemberName;
        this.content = content;
        this.broadcast = broadcast;
        this.read = read;
        this.createdAt = System.currentTimeMillis();
    }

    public String getMessageId() {
        return messageId;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getFromMemberName() {
        return fromMemberName;
    }

    public String getToMemberName() {
        return toMemberName;
    }

    public String getContent() {
        return content;
    }

    public boolean isBroadcast() {
        return broadcast;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
