/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.experience;

import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code PendingChange} in
 * {@code openjiuwen/agent_evolving/experience/types.py}.
 */
public class PendingChange {

    private String operatorId;
    private String skillName;
    private String changeType;
    private List<EvolutionRecord> payload;
    private String createdAt;
    private String changeId = ExperienceTypeUtils.newPendingChangeId();
    private boolean sharedRecords;
    private Object trajectory;
    private List<Map<String, Object>> messages;

    public PendingChange() {
        this.payload = List.of();
    }

    public PendingChange(
            String operatorId,
            String skillName,
            String changeType,
            List<EvolutionRecord> payload,
            String createdAt,
            String changeId,
            boolean sharedRecords,
            Object trajectory,
            List<Map<String, Object>> messages
    ) {
        this.operatorId = operatorId;
        this.skillName = skillName;
        this.changeType = changeType;
        setPayload(payload);
        this.createdAt = createdAt;
        if (changeId != null && !changeId.isBlank()) {
            this.changeId = changeId;
        }
        this.sharedRecords = sharedRecords;
        this.trajectory = trajectory;
        setMessages(messages);
    }

    public static PendingChange make(
            String skillName,
            List<EvolutionRecord> records,
            Object trajectory,
            List<Map<String, Object>> messages
    ) {
        return new PendingChange(
                "skill_experience_" + skillName,
                skillName,
                Protocols.SKILL_EXPERIENCE_ENTRY,
                ExperienceTypeUtils.copyList(records),
                ExperienceTypeUtils.utcNowIso(),
                ExperienceTypeUtils.newPendingChangeId(),
                false,
                trajectory,
                messages == null ? null : ExperienceTypeUtils.copyMessageList(messages)
        );
    }

    public static PendingChange makeForSharedRecords(
            String skillName,
            List<EvolutionRecord> records,
            Object trajectory,
            List<Map<String, Object>> messages
    ) {
        PendingChange pending = make(skillName, records, trajectory, messages);
        pending.setSharedRecords(true);
        return pending;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public List<EvolutionRecord> getPayload() {
        return ExperienceTypeUtils.copyList(payload);
    }

    public void setPayload(List<EvolutionRecord> payload) {
        this.payload = ExperienceTypeUtils.copyList(payload);
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getChangeId() {
        return changeId;
    }

    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    public boolean isSharedRecords() {
        return sharedRecords;
    }

    public void setSharedRecords(boolean sharedRecords) {
        this.sharedRecords = sharedRecords;
    }

    public Object getTrajectory() {
        return trajectory;
    }

    public void setTrajectory(Object trajectory) {
        this.trajectory = trajectory;
    }

    public List<Map<String, Object>> getMessages() {
        return ExperienceTypeUtils.copyMessageList(messages);
    }

    public void setMessages(List<Map<String, Object>> messages) {
        this.messages = messages == null ? null : ExperienceTypeUtils.copyMessageList(messages);
    }
}
