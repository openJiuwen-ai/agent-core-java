/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.operator.skill_call;

import com.openjiuwen.agent_evolving.ApplyResult;
import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.UpdateValue;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.core.operator.PreviewableOperator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TestSkillExperienceOperator} in
 * {@code tests/unit_tests/core/operator/test_skill_experience.py}.
 */
class SkillExperienceOperatorMissingTest {

    @Test
    void operatorIdUsesSkillExperiencePrefix() {
        SkillExperienceOperator operator = new SkillExperienceOperator("skill-a");

        assertEquals("skill_experience_skill-a", operator.getOperatorId());
        assertTrue(operator instanceof PreviewableOperator);
    }

    @Test
    void deprecatedSkillCallAliasPointsToSkillExperienceOperator() {
        assertTrue(SkillExperienceOperator.class.isAssignableFrom(SkillCallOperator.class));
        assertTrue(new SkillCallOperator("skill-a") instanceof SkillExperienceOperator);
    }

    @Test
    void previewUpdateExposesLocalApplyLifecycle() {
        SkillExperienceOperator operator = new SkillExperienceOperator("skill-a");

        ApplyResult result = operator.previewUpdate(
                Protocols.EXPERIENCES_TARGET,
                UpdateValue.builder()
                        .payload(List.of("record-1"))
                        .mode(Protocols.APPEND_MODE)
                        .effect(Protocols.PENDING_CHANGE_EFFECT)
                        .changeType(Protocols.SKILL_EXPERIENCE_ENTRY)
                        .build()
        );

        assertTrue(result.isApplied());
        assertEquals(Protocols.PENDING_CHANGE_EFFECT, result.getEffect());
        assertEquals(List.of("record-1"), result.getRecords());
        assertEquals(Protocols.LOCAL_APPLY_COMPLETED, result.getLifecycleStage());
        assertNull(result.getPendingChangeId());
        assertEquals("skill-a", result.getMetadata().get("skill_name"));
    }

    @Test
    void previewUpdateDoesNotStagePendingState() {
        SkillExperienceOperator operator = new SkillExperienceOperator("skill-a");
        EvolutionRecord newRecord = makeRecord("ev_new", "experience content");

        ApplyResult result = operator.previewUpdate(
                Protocols.EXPERIENCES_TARGET,
                UpdateValue.builder()
                        .payload(newRecord)
                        .mode(Protocols.APPEND_MODE)
                        .effect(Protocols.PENDING_CHANGE_EFFECT)
                        .build()
        );

        assertEquals(List.of(newRecord), result.getRecords());
        assertEquals(Map.of(), operator.getState());
    }

    @Test
    void applyUpdateDelegatesToPreviewUpdateForCompatibility() {
        SkillExperienceOperator operator = new SkillExperienceOperator("skill-a");
        EvolutionRecord newRecord = makeRecord("ev_new", "experience content");

        ApplyResult result = operator.applyUpdate(
                Protocols.EXPERIENCES_TARGET,
                UpdateValue.builder()
                        .payload(List.of(newRecord))
                        .mode(Protocols.APPEND_MODE)
                        .effect(Protocols.PENDING_CHANGE_EFFECT)
                        .changeType(Protocols.SKILL_EXPERIENCE_ENTRY)
                        .build()
        );

        assertTrue(result.isApplied());
        assertEquals(Protocols.SKILL_EXPERIENCE_ENTRY, result.getChangeType());
        assertEquals(List.of(newRecord), result.getRecords());
        assertEquals(Protocols.LOCAL_APPLY_COMPLETED, result.getLifecycleStage());
        assertEquals("skill-a", result.getMetadata().get("skill_name"));
    }

    @Test
    void applyUpdateDoesNotTriggerParameterCallback() {
        List<String> calls = new ArrayList<>();
        SkillExperienceOperator operator = new SkillExperienceOperator(
                "skill-a",
                (target, value) -> calls.add(target + ":" + value)
        );

        operator.applyUpdate(
                Protocols.EXPERIENCES_TARGET,
                UpdateValue.builder()
                        .payload(List.of("record-1"))
                        .mode(Protocols.APPEND_MODE)
                        .effect(Protocols.PENDING_CHANGE_EFFECT)
                        .build()
        );

        assertEquals(List.of(), calls);
    }

    @Test
    void setParameterKeepsDirectCallbackCompatibility() {
        List<String> targets = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        SkillExperienceOperator operator = new SkillExperienceOperator("skill-a", (target, value) -> {
            targets.add(target);
            values.add(value);
        });

        operator.setParameter(Protocols.EXPERIENCES_TARGET, "record-1");

        assertEquals(List.of(Protocols.EXPERIENCES_TARGET), targets);
        assertEquals(List.of(List.of("record-1")), values);
    }

    @Test
    void previewUpdateRejectsNonExperienceTarget() {
        SkillExperienceOperator operator = new SkillExperienceOperator("skill-a");

        ApplyResult result = operator.previewUpdate(
                "prompt",
                UpdateValue.builder()
                        .payload("new prompt")
                        .mode(Protocols.REPLACE_MODE)
                        .effect(Protocols.STATE_EFFECT)
                        .build()
        );

        assertFalse(result.isApplied());
        assertEquals(List.of(), result.getRecords());
        assertNull(result.getLifecycleStage());
    }

    private static EvolutionRecord makeRecord(String recordId, String content) {
        return EvolutionRecord.builder()
                .id(recordId)
                .source("signal:skill-a")
                .timestamp("2026-05-07T00:00:00+00:00")
                .context("ctx")
                .change(EvolutionPatch.builder()
                        .section("Troubleshooting")
                        .action(Protocols.APPEND_MODE)
                        .content(content)
                        .target(EvolutionTarget.BODY)
                        .build())
                .build();
    }
}
