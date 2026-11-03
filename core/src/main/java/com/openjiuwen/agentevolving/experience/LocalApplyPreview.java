/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openjiuwen.agentevolving.ApplyResult;
import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mirrors Python's {@code LocalApplyPreview} in
 * {@code openjiuwen/agent_evolving/experience/lifecycle.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class LocalApplyPreview {

    private final String skillName;
    private final List<EvolutionRecord> records;
    private final List<ApplyResult> applyResults;
    private final String changeType;
    private final String lifecycleStage;

    public LocalApplyPreview(
            String skillName,
            List<EvolutionRecord> records,
            List<ApplyResult> applyResults
    ) {
        this(skillName, records, applyResults, Protocols.SKILL_EXPERIENCE_ENTRY, Protocols.LOCAL_APPLY_COMPLETED);
    }

    public LocalApplyPreview(
            String skillName,
            List<EvolutionRecord> records,
            List<ApplyResult> applyResults,
            String changeType,
            String lifecycleStage
    ) {
        this.skillName = skillName;
        this.records = immutableList(records);
        this.applyResults = immutableList(applyResults);
        this.changeType = changeType != null ? changeType : Protocols.SKILL_EXPERIENCE_ENTRY;
        this.lifecycleStage = lifecycleStage != null ? lifecycleStage : Protocols.LOCAL_APPLY_COMPLETED;
    }

    public String getSkillName() {
        return skillName;
    }

    public List<EvolutionRecord> getRecords() {
        return records;
    }

    public List<ApplyResult> getApplyResults() {
        return applyResults;
    }

    public String getChangeType() {
        return changeType;
    }

    public String getLifecycleStage() {
        return lifecycleStage;
    }

    private static <T> List<T> immutableList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
