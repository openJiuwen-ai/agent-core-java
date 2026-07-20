/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;
import com.openjiuwen.agentteams.schema.events.TeamTopic;
import com.openjiuwen.agentteams.schema.status.ExecutionStatus;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.spawn.SpawnContext;
import com.openjiuwen.agentteams.tools.database.MemberRecord;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Manages a TeamMember's state — status transitions with event publishing.
 *
 * <p>Mirrors Python TeamMember (agent/member.py): manages member status
 * query, status change, and event publishing via messager.</p>
 *
 * @since 2026/7/9
 */
public class TeamMemberState {

    private final String memberName;
    private final String teamName;
    private final TeamDatabase db;
    private final Messager messager;
    private final String teamSessionId;

    /**
     * Construct a TeamMemberState bound to a specific member and team.
     *
     * @param memberName the member name this state tracks
     * @param teamName the team name the member belongs to
     * @param db the team database used to read and write member status
     * @param messager the messager used to publish status-change events; may be {@code null}
     */
    public TeamMemberState(String memberName, String teamName, TeamDatabase db, Messager messager) {
        this(memberName, teamName, db, messager, SpawnContext.getSessionId());
    }

    /**
     * Construct a TeamMemberState bound to a pinned team-level session id.
     *
     * @param memberName the member name this state tracks
     * @param teamName the team name the member belongs to
     * @param db the team database used to read and write member status
     * @param messager the messager used to publish status-change events; may be {@code null}
     * @param teamSessionId team-level session id pinned at construction time
     * @since 0.1.13
     */
    public TeamMemberState(String memberName, String teamName, TeamDatabase db, Messager messager,
                           String teamSessionId) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.db = db;
        this.messager = messager;
        this.teamSessionId = teamSessionId != null ? teamSessionId : "";
    }

    /**
     * Read the member's current persisted status from the database.
     *
     * @return an {@link Optional} containing the current {@link MemberStatus},
     *     or {@link Optional#empty()} when the record is missing or the value is unparseable
     */
    public Optional<MemberStatus> status() {
        MemberRecord record = db.member.getMember(memberName, teamName);
        if (record == null || record.getStatus() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MemberStatus.valueOf(record.getStatus()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Read the member's current persisted execution status from the database.
     *
     * @return an {@link Optional} containing the current {@link ExecutionStatus},
     *     or {@link Optional#empty()} when the record is missing or the value is unparseable
     */
    public Optional<ExecutionStatus> executionStatus() {
        MemberRecord record = db.member.getMember(memberName, teamName);
        if (record == null || record.getExecutionStatus() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(ExecutionStatus.valueOf(record.getExecutionStatus()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Update the member's status in the database and publish a status-changed event when the value actually changes.
     *
     * @param newStatus the new member status
     * @return true if the status actually changed
     */
    public boolean updateStatus(MemberStatus newStatus) {
        Optional<MemberStatus> oldStatusOpt = status();
        MemberStatus oldStatus = oldStatusOpt.orElse(null);
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
     * Update the member's execution status in the database and publish
     * an execution-changed event when the value actually changes.
     *
     * @param newStatus the new execution status
     * @return true if the status actually changed
     */
    public boolean updateExecutionStatus(ExecutionStatus newStatus) {
        Optional<ExecutionStatus> oldStatusOpt = executionStatus();
        ExecutionStatus oldStatus = oldStatusOpt.orElse(null);
        if (oldStatus == newStatus) {
            return true;
        }

        boolean updated = db.member.updateMemberExecutionStatus(
                memberName, teamName, newStatus.name());
        if (updated && messager != null) {
            publishExecutionChangedEvent(oldStatus, newStatus);
        }
        return updated;
    }

    private void publishStatusChangedEvent(MemberStatus oldStatus, MemberStatus newStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("target_id", memberName);
        payload.put("old_status", oldStatus != null ? oldStatus.name() : "unknown");
        payload.put("new_status", newStatus.name());

        EventMessage event = EventMessage.builder()
                .eventType(TeamEvent.MEMBER_STATUS_CHANGED)
                .payload(payload)
                .senderId(memberName)
                .build();

        try {
            // Mirrors Python member.py: TeamTopic.TEAM.build(get_session_id(), team_name).
            // Use the pinned team session so the event reaches subscribers on the team topic
            // regardless of which ReAct-stream session is current on this thread.
            messager.publish(TeamTopic.TEAM.build(teamSessionId, teamName), event);
        } catch (IllegalStateException | NullPointerException e) {
            Loggers.AGENT.warn("Failed to publish member status change event for {}", memberName, e);
        }
    }

    private void publishExecutionChangedEvent(ExecutionStatus oldStatus, ExecutionStatus newStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("target_id", memberName);
        payload.put("old_status", oldStatus != null ? oldStatus.name() : "unknown");
        payload.put("new_status", newStatus.name());

        EventMessage event = EventMessage.builder()
                .eventType(TeamEvent.MEMBER_EXECUTION_CHANGED)
                .payload(payload)
                .senderId(memberName)
                .build();

        try {
            messager.publish(TeamTopic.TEAM.build(teamSessionId, teamName), event);
        } catch (IllegalStateException | NullPointerException e) {
            Loggers.AGENT.warn("Failed to publish member execution change event for {}", memberName, e);
        }
    }
}
