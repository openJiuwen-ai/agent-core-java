/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.experience;

import com.openjiuwen.agent_evolving.Protocols;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code ExperienceApplyResult} in
 * {@code openjiuwen/agent_evolving/experience/types.py}.
 */
public class ExperienceApplyResult {

    private String skillName;
    private int appliedCount;
    private int rejectedCount;
    private int pendingCount;
    private List<String> errors = List.of();
    private Map<String, Object> metadata = Map.of();

    public ExperienceApplyResult() {
    }

    public ExperienceApplyResult(
            String skillName,
            int appliedCount,
            int rejectedCount,
            int pendingCount,
            List<String> errors,
            Map<String, Object> metadata
    ) {
        this.skillName = skillName;
        this.appliedCount = appliedCount;
        this.rejectedCount = rejectedCount;
        this.pendingCount = pendingCount;
        setErrors(errors);
        setMetadata(metadata);
    }

    public boolean isOk() {
        return errors.isEmpty() && pendingCount == 0;
    }

    public HostFacingExperienceResult toHostResult(String requestId, String changeType) {
        boolean pureRejection = rejectedCount > 0
                && appliedCount == 0
                && pendingCount == 0
                && errors.isEmpty();
        if (pureRejection) {
            return HostFacingExperienceResult.rejected(skillName, requestId, changeType, rejectedCount);
        }
        return HostFacingExperienceResult.persisted(
                skillName,
                requestId,
                changeType == null ? Protocols.SKILL_EXPERIENCE_ENTRY : changeType,
                appliedCount,
                rejectedCount,
                pendingCount,
                errors
        );
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public int getAppliedCount() {
        return appliedCount;
    }

    public void setAppliedCount(int appliedCount) {
        this.appliedCount = appliedCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(int rejectedCount) {
        this.rejectedCount = rejectedCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }

    public List<String> getErrors() {
        return ExperienceTypeUtils.copyStringList(errors);
    }

    public void setErrors(List<String> errors) {
        this.errors = ExperienceTypeUtils.copyStringList(errors);
    }

    public Map<String, Object> getMetadata() {
        return ExperienceTypeUtils.copyMap(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = ExperienceTypeUtils.copyMap(metadata);
    }
}
