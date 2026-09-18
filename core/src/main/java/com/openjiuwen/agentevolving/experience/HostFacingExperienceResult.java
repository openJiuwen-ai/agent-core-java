/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openjiuwen.agentevolving.Protocols;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code HostFacingExperienceResult} in
 * {@code openjiuwen/agent_evolving/experience/lifecycle.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class HostFacingExperienceResult {

    private final String skillName;
    private final String requestId;
    private final String effect;
    private final String changeType;
    private final int appliedCount;
    private final int rejectedCount;
    private final int pendingCount;
    private final String status;
    private final List<String> errors;
    private final Map<String, Object> metadata;

    public HostFacingExperienceResult(
            String skillName,
            String requestId,
            String effect,
            String changeType,
            int appliedCount,
            int rejectedCount,
            int pendingCount,
            String status,
            List<String> errors,
            Map<String, Object> metadata
    ) {
        this.skillName = skillName;
        this.requestId = requestId;
        this.effect = effect;
        this.changeType = changeType != null ? changeType : Protocols.SKILL_EXPERIENCE_ENTRY;
        this.appliedCount = appliedCount;
        this.rejectedCount = rejectedCount;
        this.pendingCount = pendingCount;
        this.status = status != null ? status : "pending_approval";
        this.errors = immutableList(errors);
        this.metadata = immutableMap(metadata);
    }

    public static HostFacingExperienceResult pendingApproval(
            String skillName,
            String requestId,
            int pendingCount
    ) {
        return pendingApproval(skillName, requestId, Protocols.SKILL_EXPERIENCE_ENTRY, pendingCount);
    }

    public static HostFacingExperienceResult pendingApproval(
            String skillName,
            String requestId,
            String changeType,
            int pendingCount
    ) {
        return new HostFacingExperienceResult(
                skillName,
                requestId,
                Protocols.PENDING_CHANGE_EFFECT,
                changeType,
                0,
                0,
                pendingCount,
                "pending_approval",
                List.of(),
                Map.of()
        );
    }

    public static HostFacingExperienceResult persisted(
            String skillName,
            String requestId,
            int appliedCount
    ) {
        return persisted(skillName, requestId, Protocols.SKILL_EXPERIENCE_ENTRY, appliedCount, 0, 0, null);
    }

    public static HostFacingExperienceResult persisted(
            String skillName,
            String requestId,
            String changeType,
            int appliedCount,
            int rejectedCount,
            int pendingCount,
            List<String> errors
    ) {
        List<String> errorList = errors == null ? List.of() : List.copyOf(errors);
        boolean hasPartialOutcome = pendingCount > 0 || rejectedCount > 0 || !errorList.isEmpty();
        String status = hasPartialOutcome ? "partial" : "persisted";
        return new HostFacingExperienceResult(
                skillName,
                requestId,
                Protocols.STATE_EFFECT,
                changeType,
                appliedCount,
                rejectedCount,
                pendingCount,
                status,
                errorList,
                Map.of()
        );
    }

    public static HostFacingExperienceResult rejected(
            String skillName,
            String requestId,
            int rejectedCount
    ) {
        return rejected(skillName, requestId, Protocols.SKILL_EXPERIENCE_ENTRY, rejectedCount);
    }

    public static HostFacingExperienceResult rejected(
            String skillName,
            String requestId,
            String changeType,
            int rejectedCount
    ) {
        return new HostFacingExperienceResult(
                skillName,
                requestId,
                Protocols.STATE_EFFECT,
                changeType,
                0,
                rejectedCount,
                0,
                "rejected",
                List.of(),
                Map.of()
        );
    }

    public String getSkillName() {
        return skillName;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getEffect() {
        return effect;
    }

    public String getChangeType() {
        return changeType;
    }

    public int getAppliedCount() {
        return appliedCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getErrors() {
        return errors;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    private static <T> List<T> immutableList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static Map<String, Object> immutableMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
