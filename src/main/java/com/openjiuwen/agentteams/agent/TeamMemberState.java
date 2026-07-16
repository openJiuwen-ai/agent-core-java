/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.status.ExecutionStatus;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.tools.database.MemberRecord;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages a TeamMember's state — status transitions with event publishing.
 * <p>
 * Mirrors Python TeamMember (agent/member.py): manages member status
 * query, status change, and event publishing via messager.
 * </p>
 * 
 * @since 0.1.7
 */
public class TeamMemberState {
    private final String memberName;
    private final String teamName;
    private final TeamDatabase db;
    private final Messager messager;

    /**
     * TeamMemberState.
     * 
     * @param memberName memberName
     * @param teamName teamName
     * @param db db
     * @param messager messager
     * @since 0.1.7
     */
    public TeamMemberState(String memberName, String teamName, TeamDatabase db, Messager messager) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.db = db;
        this.messager = messager;
    }

    /**
     * status.
     * 
     * @return the result
     * @since 0.1.7
     */
    public MemberStatus status() {
        MemberRecord record = db.member.getMember(memberName, teamName);
        if (record == null || record.getStatus() == null) {
            return null;
        }
        try {
            return MemberStatus.valueOf(record.getStatus());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * executionStatus.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ExecutionStatus executionStatus() {
        MemberRecord record = db.member.getMember(memberName, teamName);
        if (record == null || record.getExecutionStatus() == null) {
            return null;
        }
        try {
            return ExecutionStatus.valueOf(record.getExecutionStatus());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * updateStatus.
     * 
     * @param newStatus newStatus
     * @return the result
     * @since 0.1.7
     */
    public boolean updateStatus(MemberStatus newStatus) {
        MemberStatus oldStatus = status();
        if (oldStatus == newStatus) {
            return true;
        }

        boolean updated = db.member.updateMemberStatus(memberName, teamName, newStatus.name());
        if (updated && messager != null) {
            publishStatusChangedEvent(oldStatus, newStatus);
        }
        return updated;
    }

    /**
     * updateExecutionStatus.
     * 
     * @param newStatus newStatus
     * @return the result
     * @since 0.1.7
     */
    public boolean updateExecutionStatus(ExecutionStatus newStatus) {
        ExecutionStatus oldStatus = executionStatus();
        if (oldStatus == newStatus) {
            return true;
        }

        boolean updated = db.member.updateMemberExecutionStatus(memberName, teamName, newStatus.name());
        if (updated && messager != null) {
            publishExecutionChangedEvent(oldStatus, newStatus);
        }
        return updated;
    }

    /**
     * publishStatusChangedEvent.
     * 
     * @param oldStatus oldStatus
     * @param newStatus newStatus
     * @since 0.1.7
     */
    private void publishStatusChangedEvent(MemberStatus oldStatus, MemberStatus newStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("target_id", memberName);
        payload.put("old_status", oldStatus != null ? oldStatus.name() : "unknown");
        payload.put("new_status", newStatus.name());

        EventMessage event =
            EventMessage.builder().eventType("member_status_changed").payload(payload).senderId(memberName).build();

        try {
            messager.publish("team:" + teamName + ":team", event);
        } catch (Exception e) {
            Loggers.AGENT.warn("Failed to publish member status change event for {}", memberName, e);
        }
    }

    /**
     * publishExecutionChangedEvent.
     * 
     * @param oldStatus oldStatus
     * @param newStatus newStatus
     * @since 0.1.7
     */
    private void publishExecutionChangedEvent(ExecutionStatus oldStatus, ExecutionStatus newStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("target_id", memberName);
        payload.put("old_status", oldStatus != null ? oldStatus.name() : "unknown");
        payload.put("new_status", newStatus.name());

        EventMessage event =
            EventMessage.builder().eventType("member_execution_changed").payload(payload).senderId(memberName).build();

        try {
            messager.publish("team:" + teamName + ":team", event);
        } catch (Exception e) {
            Loggers.AGENT.warn("Failed to publish member execution change event for {}", memberName, e);
        }
    }
}
