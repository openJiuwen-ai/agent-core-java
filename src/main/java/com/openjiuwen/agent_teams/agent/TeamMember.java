/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.events.TeamTopic;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.spawn.SpawnContext;
import com.openjiuwen.agent_teams.tools.TeamDatabase;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Map;

/**
 * Team member state facade.
 *
 * <p>Mirrors Python's {@code TeamMember} in
 * {@code openjiuwen.agent_teams.agent.member}.
 */
public class TeamMember {

    private final String memberName;
    private final String teamName;
    private final String displayName;
    private final AgentCard agentCard;
    private final TeamDatabase db;
    private final Messager messager;
    private final String prompt;
    private final String desc;
    private final long updatedAt;
    private MemberStatus status;
    private ExecutionStatus executionStatus;

    public TeamMember(
            String memberName,
            String teamName,
            String displayName,
            AgentCard agentCard,
            String prompt,
            String desc,
            MemberStatus status,
            ExecutionStatus executionStatus
    ) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.displayName = displayName != null ? displayName : memberName;
        this.agentCard = agentCard;
        this.db = null;
        this.messager = null;
        this.prompt = prompt;
        this.desc = desc;
        this.status = status != null ? status : MemberStatus.UNSTARTED;
        this.executionStatus = executionStatus != null ? executionStatus : ExecutionStatus.IDLE;
        this.updatedAt = System.currentTimeMillis();
    }

    public TeamMember(
            String memberName,
            String teamName,
            AgentCard agentCard,
            TeamDatabase db,
            Messager messager,
            String displayName,
            String prompt,
            String desc
    ) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.displayName = displayName != null ? displayName : memberName;
        this.agentCard = agentCard;
        this.db = db;
        this.messager = messager;
        this.prompt = prompt;
        this.desc = desc;
        this.status = MemberStatus.UNSTARTED;
        this.executionStatus = ExecutionStatus.IDLE;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getMemberName() {
        return memberName;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AgentCard getAgentCard() {
        return agentCard;
    }

    public TeamDatabase getDb() {
        return db;
    }

    public Messager getMessager() {
        return messager;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getDesc() {
        return desc;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public MemberStatus getStatus() {
        return status();
    }

    public MemberStatus status() {
        if (db == null || db.getMemberDao() == null) {
            return status;
        }
        return db.getMemberDao().getMember(memberName, teamName)
                .join()
                .map(member -> MemberStatus.fromValue(member.getStatus()))
                .orElse(null);
    }

    public void setStatus(MemberStatus status) {
        this.status = status;
    }

    public boolean updateStatus(MemberStatus newStatus) {
        MemberStatus oldStatus = status();
        if (oldStatus == newStatus) {
            return true;
        }
        if (db == null || db.getMemberDao() == null) {
            this.status = newStatus;
            return true;
        }
        boolean success = db.getMemberDao().updateMemberStatus(memberName, teamName, newStatus.value()).join();
        if (!success) {
            return false;
        }
        publishEvent("member_status_changed", Map.of(
                "team_name", teamName,
                "member_name", memberName,
                "old_status", oldStatus != null ? oldStatus.value() : "",
                "new_status", newStatus.value()
        ));
        this.status = newStatus;
        return true;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus();
    }

    public ExecutionStatus executionStatus() {
        if (db == null || db.getMemberDao() == null) {
            return executionStatus;
        }
        return db.getMemberDao().getMember(memberName, teamName)
                .join()
                .map(member -> ExecutionStatus.fromValue(member.getExecutionStatus()))
                .orElse(null);
    }

    public void setExecutionStatus(ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }

    public boolean updateExecutionStatus(ExecutionStatus newStatus) {
        ExecutionStatus oldStatus = executionStatus();
        if (db == null || db.getMemberDao() == null) {
            this.executionStatus = newStatus;
            return true;
        }
        boolean success = db.getMemberDao()
                .updateMemberExecutionStatus(memberName, teamName, newStatus.value())
                .join();
        if (!success) {
            return false;
        }
        publishEvent("member_execution_changed", Map.of(
                "team_name", teamName,
                "member_name", memberName,
                "old_status", oldStatus != null ? oldStatus.value() : "",
                "new_status", newStatus.value()
        ));
        this.executionStatus = newStatus;
        return true;
    }

    private void publishEvent(String eventType, Map<String, Object> payload) {
        if (messager == null) {
            return;
        }
        try {
            messager.publish(
                    TeamTopic.TEAM.build(SpawnContext.getSessionId(), teamName),
                    new EventMessage(eventType, payload)
            );
        } catch (Exception ignored) {
            // Python logs event publishing failures and keeps the status update.
        }
    }
}
