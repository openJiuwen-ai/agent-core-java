/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.Objects;

/**
 * Base class for message read status table (one per session).
 * <p>
 * Tracks which broadcast message each member has read up to.
 * Each member has one record per team, storing the timestamp of the broadcast message they have read.
 * </p>
 * <p>
 * Mirrors Python's {@code MessageReadStatusBase} in {@code openjiuwen.agent_teams.tools.models}.
 * </p>
 */
public class MessageReadStatus {

    private String memberName;
    private String teamName;
    private Long readAt;

    public MessageReadStatus() {
    }

    public MessageReadStatus(String memberName, String teamName, Long readAt) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.readAt = readAt;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public Long getReadAt() {
        return readAt;
    }

    public void setReadAt(Long readAt) {
        this.readAt = readAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageReadStatus that)) return false;
        return Objects.equals(memberName, that.memberName)
                && Objects.equals(teamName, that.teamName)
                && Objects.equals(readAt, that.readAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberName, teamName, readAt);
    }

    @Override
    public String toString() {
        return "MessageReadStatus{" +
                "memberName='" + memberName + '\'' +
                ", teamName='" + teamName + '\'' +
                ", readAt=" + readAt +
                '}';
    }
}