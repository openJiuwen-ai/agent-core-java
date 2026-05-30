/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot of staged evolution records awaiting user approval.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing.types.PendingChange}.
 */
public class PendingChange {

    private final String operatorId;
    private final String skillName;
    private final String changeType;
    private final List<EvolutionRecord> payload;
    private final String createdAt;
    private final String changeId;

    public PendingChange(
            String operatorId,
            String skillName,
            String changeType,
            List<EvolutionRecord> payload,
            String createdAt,
            String changeId
    ) {
        this.operatorId = operatorId;
        this.skillName = skillName;
        this.changeType = changeType;
        this.payload = payload == null ? new ArrayList<>() : new ArrayList<>(payload);
        this.createdAt = createdAt;
        this.changeId = changeId != null ? changeId : "skill_evolve_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static PendingChange make(String skillName, List<EvolutionRecord> records) {
        return new PendingChange(
                "skill_call_" + skillName,
                skillName,
                "experience_entry",
                records,
                Instant.now().toString(),
                null
        );
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getSkillName() {
        return skillName;
    }

    public String getChangeType() {
        return changeType;
    }

    public List<EvolutionRecord> getPayload() {
        return new ArrayList<>(payload);
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getChangeId() {
        return changeId;
    }
}
