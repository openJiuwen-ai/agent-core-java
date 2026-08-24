/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.skill_call;

import com.openjiuwen.agentevolving.ApplyResult;
import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agentevolving.UpdateValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen/core/operator/skill_call/base.py}.
 */
class SkillExperienceOperatorTest {

    @Test
    void exposesSkillExperienceTunableAndAliasOperatorId() {
        SkillCallOperator operator = new SkillCallOperator("planner");

        assertEquals("skill_experience_planner", operator.getOperatorId());
        assertTrue(operator.getTunables().containsKey(Protocols.EXPERIENCES_TARGET));
    }

    @Test
    void setParameterNormalizesSingleItemToList() {
        List<Object> updates = new ArrayList<>();
        SkillExperienceOperator operator = new SkillExperienceOperator("planner", (target, value) -> updates.add(value));

        operator.setParameter(Protocols.EXPERIENCES_TARGET, Map.of("id", 1));

        assertEquals(1, updates.size());
        assertEquals(List.of(Map.of("id", 1)), updates.get(0));
    }

    @Test
    void previewUpdateRejectsUnsupportedModeOrTarget() {
        SkillExperienceOperator operator = new SkillExperienceOperator("planner");

        ApplyResult wrongTarget = operator.previewUpdate("other", UpdateValue.builder()
                .payload("x")
                .mode(Protocols.APPEND_MODE)
                .effect(Protocols.PENDING_CHANGE_EFFECT)
                .build());
        ApplyResult wrongMode = operator.previewUpdate(Protocols.EXPERIENCES_TARGET, UpdateValue.builder()
                .payload("x")
                .mode(Protocols.REPLACE_MODE)
                .effect(Protocols.PENDING_CHANGE_EFFECT)
                .build());

        assertFalse(wrongTarget.isApplied());
        assertFalse(wrongMode.isApplied());
        assertTrue(wrongTarget.getErrors().get(0).contains("unsupported target"));
        assertTrue(wrongMode.getErrors().get(0).contains("unsupported update mode/effect"));
    }

    @Test
    void previewUpdateReturnsLocalApplyCompletedMetadata() {
        SkillExperienceOperator operator = new SkillExperienceOperator("planner");
        ApplyResult result = operator.previewUpdate(Protocols.EXPERIENCES_TARGET, UpdateValue.builder()
                .payload(List.of(Map.of("id", 1)))
                .mode(Protocols.MERGE_MODE)
                .effect(Protocols.PENDING_CHANGE_EFFECT)
                .changeType("preview")
                .metadata(Map.of("source", "test"))
                .build());

        assertTrue(result.isApplied());
        assertEquals(Protocols.LOCAL_APPLY_COMPLETED, result.getLifecycleStage());
        assertEquals("planner", result.getMetadata().get("skill_name"));
        assertEquals(List.of(Map.of("id", 1)), result.getRecords());
    }
}
