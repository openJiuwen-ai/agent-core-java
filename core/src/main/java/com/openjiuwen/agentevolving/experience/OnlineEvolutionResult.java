/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

/**
 * Mirrors Python's {@code OnlineEvolutionResult} in
 * {@code openjiuwen/agent_evolving/experience/types.py}.
 */
public class OnlineEvolutionResult {

    private String skillName;
    private String status;
    private ExperienceApprovalRequest request;
    private String message = "";

    public OnlineEvolutionResult() {
    }

    public OnlineEvolutionResult(String skillName, String status, ExperienceApprovalRequest request, String message) {
        this.skillName = skillName;
        this.status = status;
        this.request = request;
        this.message = message == null ? "" : message;
    }

    public static ExperienceApprovalRequest requestForOnlineEvolutionResult(OnlineEvolutionResult result) {
        if (result == null) {
            return null;
        }
        if (OnlineEvolutionStatus.OUTCOME_STATUSES.contains(result.getStatus())
                && !OnlineEvolutionStatus.PERSISTENCE_FAILED.equals(result.getStatus())) {
            return null;
        }
        return result.getRequest();
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ExperienceApprovalRequest getRequest() {
        return request;
    }

    public void setRequest(ExperienceApprovalRequest request) {
        this.request = request;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }
}
