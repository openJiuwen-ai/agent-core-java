/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import com.openjiuwen.agentevolving.ApplyResult;
import com.openjiuwen.agentevolving.Protocols;

import java.util.List;

/**
 * Mirrors Python's {@code ExperienceApprovalRequest} in
 * {@code openjiuwen/agent_evolving/experience/types.py}.
 */
public class ExperienceApprovalRequest {

    private String skillName;
    private ExperienceProposal proposal;
    private PendingChange pendingChange;
    private String requestId;
    private List<ApplyResult> applyResults = List.of();

    public ExperienceApprovalRequest() {
    }

    public ExperienceApprovalRequest(
            String skillName,
            ExperienceProposal proposal,
            PendingChange pendingChange,
            String requestId,
            List<ApplyResult> applyResults
    ) {
        this.skillName = skillName;
        this.proposal = proposal;
        this.pendingChange = pendingChange;
        this.requestId = requestId;
        setApplyResults(applyResults);
    }

    public HostFacingExperienceResult toHostResult() {
        int pendingCount = pendingChange != null ? pendingChange.getPayload().size() : 0;
        String changeType = pendingChange != null ? pendingChange.getChangeType() : Protocols.SKILL_EXPERIENCE_ENTRY;
        return HostFacingExperienceResult.pendingApproval(
                skillName,
                requestId == null ? "" : requestId,
                changeType,
                pendingCount
        );
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public ExperienceProposal getProposal() {
        return proposal;
    }

    public void setProposal(ExperienceProposal proposal) {
        this.proposal = proposal;
    }

    public PendingChange getPendingChange() {
        return pendingChange;
    }

    public void setPendingChange(PendingChange pendingChange) {
        this.pendingChange = pendingChange;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public List<ApplyResult> getApplyResults() {
        return ExperienceTypeUtils.copyList(applyResults);
    }

    public void setApplyResults(List<ApplyResult> applyResults) {
        this.applyResults = ExperienceTypeUtils.copyList(applyResults);
    }
}
