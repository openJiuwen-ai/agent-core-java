/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.Objects;

/**
 * Team member table model.
 * <p>
 * Mirrors Python's {@code TeamMember} in {@code openjiuwen.agent_teams.tools.models}.
 * </p>
 */
public class TeamMember {

    private String memberName;
    private String teamName;
    private String displayName;
    private String desc;
    private String agentCard;
    private String status;
    private String executionStatus;
    private String mode;
    private String prompt;
    private String modelRefJson;
    private Long updatedAt;

    public TeamMember() {
    }

    public TeamMember(String memberName, String teamName, String displayName,
                       String desc, String agentCard, String status,
                       String executionStatus, String mode, String prompt,
                       String modelRefJson, Long updatedAt) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.displayName = displayName;
        this.desc = desc;
        this.agentCard = agentCard;
        this.status = status;
        this.executionStatus = executionStatus;
        this.mode = mode;
        this.prompt = prompt;
        this.modelRefJson = modelRefJson;
        this.updatedAt = updatedAt;
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getAgentCard() {
        return agentCard;
    }

    public void setAgentCard(String agentCard) {
        this.agentCard = agentCard;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getModelRefJson() {
        return modelRefJson;
    }

    public void setModelRefJson(String modelRefJson) {
        this.modelRefJson = modelRefJson;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamMember that)) return false;
        return Objects.equals(memberName, that.memberName)
                && Objects.equals(teamName, that.teamName)
                && Objects.equals(displayName, that.displayName)
                && Objects.equals(desc, that.desc)
                && Objects.equals(agentCard, that.agentCard)
                && Objects.equals(status, that.status)
                && Objects.equals(executionStatus, that.executionStatus)
                && Objects.equals(mode, that.mode)
                && Objects.equals(prompt, that.prompt)
                && Objects.equals(modelRefJson, that.modelRefJson)
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberName, teamName, displayName, desc, agentCard,
                         status, executionStatus, mode, prompt, modelRefJson, updatedAt);
    }

    @Override
    public String toString() {
        return "TeamMember{" +
                "memberName='" + memberName + '\'' +
                ", teamName='" + teamName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", desc='" + desc + '\'' +
                ", agentCard='" + agentCard + '\'' +
                ", status='" + status + '\'' +
                ", executionStatus='" + executionStatus + '\'' +
                ", mode='" + mode + '\'' +
                ", prompt='" + prompt + '\'' +
                ", modelRefJson='" + modelRefJson + '\'' +
                ", updatedAt=" + updatedAt +
                '}';
    }
}