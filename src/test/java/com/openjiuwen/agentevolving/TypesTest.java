/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving;

import com.openjiuwen.agentevolving.experience.PendingChange;
import com.openjiuwen.agentevolving.trajectory.UpdateKey;
import com.openjiuwen.agentevolving.trajectory.Updates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for shared evolution apply types.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_evolving/test_types.py}.
 */
class TypesTest {

    @Test
    @DisplayName("normalizeUpdates wraps legacy values as replace/state")
    void testNormalizeUpdatesWrapsLegacyValuesAsReplaceState() {
        Map<UpdateKey, Object> normalized = new LinkedHashMap<>(EvolutionTypes.normalizeUpdates(Map.of(
                UpdateKey.of("op1", "system_prompt"), "new prompt",
                UpdateKey.of("op2", "temperature"), 0.3
        )));

        assertEquals(new UpdateValue("new prompt"), normalized.get(UpdateKey.of("op1", "system_prompt")));
        assertEquals(new UpdateValue(0.3), normalized.get(UpdateKey.of("op2", "temperature")));
    }

    @Test
    @DisplayName("normalizeUpdates preserves structured values")
    void testNormalizeUpdatesPreservesStructuredValues() {
        UpdateValue structured = UpdateValue.builder()
                .payload(Map.of("records", List.of(1, 2)))
                .mode(Protocols.APPEND_MODE)
                .effect(Protocols.PENDING_CHANGE_EFFECT)
                .metadata(Map.of("change_type", Protocols.EXPERIENCE_ENTRY))
                .build();

        Map<UpdateKey, UpdateValue> normalized = EvolutionTypes.normalizeUpdates(
                Map.of(UpdateKey.of("skill_op", "experience"), structured)
        );

        assertSame(structured, normalized.get(UpdateKey.of("skill_op", "experience")));
    }

    @Test
    @DisplayName("normalizeUpdates supports mixed legacy and structured values")
    void testNormalizeUpdatesSupportsMixedLegacyAndStructuredValues() {
        UpdateValue structured = UpdateValue.builder()
                .payload(Map.of("patch", "delta"))
                .mode(Protocols.MERGE_MODE)
                .effect(Protocols.PENDING_CHANGE_EFFECT)
                .metadata(Map.of("change_type", "team_skill_patch"))
                .build();

        Map<UpdateKey, UpdateValue> normalized = EvolutionTypes.normalizeUpdates(Map.of(
                UpdateKey.of("op1", "system_prompt"), "new prompt",
                UpdateKey.of("op2", "team_skill"), structured
        ));

        assertEquals(new UpdateValue("new prompt"), normalized.get(UpdateKey.of("op1", "system_prompt")));
        assertSame(structured, normalized.get(UpdateKey.of("op2", "team_skill")));
    }

    @Test
    @DisplayName("legacy experiences target normalizes to append pending change")
    void testNormalizeUpdatesPreservesLegacySkillExperienceSemantics() {
        List<String> legacyRecords = List.of("record-1", "record-2");

        Map<UpdateKey, UpdateValue> normalized = EvolutionTypes.normalizeUpdates(
                Map.of(UpdateKey.of("skill_experience_skill-a", Protocols.EXPERIENCES_TARGET), legacyRecords)
        );

        assertEquals(
                UpdateValue.builder()
                        .payload(legacyRecords)
                        .mode(Protocols.APPEND_MODE)
                        .effect(Protocols.PENDING_CHANGE_EFFECT)
                        .changeType(Protocols.SKILL_EXPERIENCE_ENTRY)
                        .metadata(Map.of("change_type", Protocols.SKILL_EXPERIENCE_ENTRY))
                        .build(),
                normalized.get(UpdateKey.of("skill_experience_skill-a", Protocols.EXPERIENCES_TARGET))
        );
    }

    @Test
    @DisplayName("normalizeUpdateValue defaults to replace/state outside experiences target")
    void testNormalizeUpdateValueDefaultsToReplaceState() {
        UpdateValue normalized = EvolutionTypes.normalizeUpdateValue("prompt", "system_prompt");

        assertEquals("prompt", normalized.getPayload());
        assertEquals(Protocols.REPLACE_MODE, normalized.getMode());
        assertEquals(Protocols.STATE_EFFECT, normalized.getEffect());
        assertEquals(Map.of(), normalized.getMetadata());
    }

    @Test
    @DisplayName("legacy aliases stay available through trajectory types")
    void testLegacyUpdateAliasesRemainCompatible() {
        Updates updates = Updates.of("op1", "system_prompt", "new prompt");

        assertTrue(updates.containsKey(UpdateKey.of("op1", "system_prompt")));
        assertEquals("new prompt", updates.get("op1", "system_prompt"));
        assertEquals(
                new UpdateValue("new prompt"),
                EvolutionTypes.normalizeUpdates(updates).get(UpdateKey.of("op1", "system_prompt"))
        );
    }

    @Test
    @DisplayName("apply result ok depends on applied flag and errors")
    void testApplyResultOkDependsOnAppliedFlagAndErrors() {
        ApplyResult result = new ApplyResult("op1", "system_prompt", true);
        ApplyResult failed = ApplyResult.builder()
                .operatorId("op1")
                .target("system_prompt")
                .applied(false)
                .errors(List.of("apply failed"))
                .build();

        assertTrue(result.isOk());
        assertFalse(failed.isOk());
    }

    @Test
    @DisplayName("apply result exposes staged pending change fields")
    void testApplyResultExposesPendingChangeFieldsForFutureLifecycle() {
        ApplyResult result = ApplyResult.builder()
                .operatorId("skill_op")
                .target("experience")
                .applied(true)
                .mode(Protocols.APPEND_MODE)
                .effect(Protocols.PENDING_CHANGE_EFFECT)
                .records(List.of("record-1"))
                .changeType(Protocols.SKILL_EXPERIENCE_ENTRY)
                .lifecycleStage(Protocols.LOCAL_APPLY_COMPLETED)
                .pendingChangeId("pending-123")
                .build();

        assertEquals("pending-123", result.getPendingChangeId());
        assertEquals(Protocols.PENDING_CHANGE_EFFECT, result.getEffect());
        assertEquals(List.of("record-1"), result.getRecords());
        assertEquals(Protocols.SKILL_EXPERIENCE_ENTRY, result.getChangeType());
        assertEquals(Protocols.LOCAL_APPLY_COMPLETED, result.getLifecycleStage());
    }

    @Test
    @DisplayName("PendingChange.make uses unified skill experience entry naming")
    void testPendingChangeMakeUsesUnifiedSkillExperienceEntryName() {
        PendingChange pending = PendingChange.make("skill-a", List.of());

        assertEquals("skill_experience_skill-a", pending.getOperatorId());
        assertEquals("skill-a", pending.getSkillName());
        assertEquals(Protocols.SKILL_EXPERIENCE_ENTRY, pending.getChangeType());
    }
}
