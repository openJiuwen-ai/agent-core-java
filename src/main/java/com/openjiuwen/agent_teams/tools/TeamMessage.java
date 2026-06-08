/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.Objects;

/**
 * Base class for team message tables (one per session).
 * <p>
 * Mirrors Python's {@code TeamMessageBase} in
 * {@code openjiuwen/agent_teams/tools/models.py}.
 */
public class TeamMessage {

    private String messageId;
    private String teamName;
    private String fromMemberName;
    private String toMemberName;
    private String content;
    private Long timestamp;
    private Boolean broadcast;
    private Boolean isRead;

    public TeamMessage() {
    }

    public TeamMessage(String messageId, String teamName, String fromMemberName,
            String toMemberName, String content, Long timestamp,
            Boolean broadcast, Boolean isRead) {
        this.messageId = messageId;
        this.teamName = teamName;
        this.fromMemberName = fromMemberName;
        this.toMemberName = toMemberName;
        this.content = content;
        this.timestamp = timestamp;
        this.broadcast = broadcast;
        this.isRead = isRead;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getFromMemberName() {
        return fromMemberName;
    }

    public void setFromMemberName(String fromMemberName) {
        this.fromMemberName = fromMemberName;
    }

    public String getToMemberName() {
        return toMemberName;
    }

    public void setToMemberName(String toMemberName) {
        this.toMemberName = toMemberName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Boolean getBroadcast() {
        return broadcast;
    }

    public void setBroadcast(Boolean broadcast) {
        this.broadcast = broadcast;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TeamMessage that)) {
            return false;
        }
        return Objects.equals(messageId, that.messageId)
                && Objects.equals(teamName, that.teamName)
                && Objects.equals(fromMemberName, that.fromMemberName)
                && Objects.equals(toMemberName, that.toMemberName)
                && Objects.equals(content, that.content)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(broadcast, that.broadcast)
                && Objects.equals(isRead, that.isRead);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, teamName, fromMemberName, toMemberName,
                content, timestamp, broadcast, isRead);
    }

    @Override
    public String toString() {
        return "TeamMessage{"
                + "messageId='" + messageId + '\''
                + ", teamName='" + teamName + '\''
                + ", fromMemberName='" + fromMemberName + '\''
                + ", toMemberName='" + toMemberName + '\''
                + ", content='" + content + '\''
                + ", timestamp=" + timestamp
                + ", broadcast=" + broadcast
                + ", isRead=" + isRead
                + '}';
    }
}
