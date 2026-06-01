/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.core.singleagent.schema.AgentCard;

/**
 * Minimal in-memory team member state holder.
 *
 * <p>Mirrors Python's {@code TeamMember} in
 * {@code openjiuwen.agent_teams.agent.member}.
 */
public class TeamMember {

    private final String memberName;
    private final String teamName;
    private final String displayName;
    private final AgentCard agentCard;
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
        this.prompt = prompt;
        this.desc = desc;
        this.status = status != null ? status : MemberStatus.UNSTARTED;
        this.executionStatus = executionStatus != null ? executionStatus : ExecutionStatus.IDLE;
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
        return status;
    }

    public void setStatus(MemberStatus status) {
        this.status = status;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }
}
