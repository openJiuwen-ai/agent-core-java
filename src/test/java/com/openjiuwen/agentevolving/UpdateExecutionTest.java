/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving;

import com.openjiuwen.agent_evolving.trajectory.UpdateKey;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for shared update execution helpers.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/agent_evolving/update_execution.py}.</p>
 *
 * <p>Test cases also follow Python's
 * {@code tests/unit_tests/agent_evolving/updater/test_apply.py}.</p>
 */
class UpdateExecutionTest {

    @Test
    void executeUpdatesNormalizesLegacyReplaceUpdates() {
        RecordingOperator operator = new RecordingOperator("op1");
        Map<UpdateKey, Object> updates = new LinkedHashMap<>();
        updates.put(UpdateKey.of("op1", "system_prompt"), "new prompt");

        List<ApplyResult> results = UpdateExecution.executeUpdates(Map.of("op1", operator), updates);

        assertEquals(List.of(List.of("system_prompt", "new prompt")), operator.getCalls());
        assertEquals(List.of(new ApplyResult("op1", "system_prompt", true,
                Protocols.REPLACE_MODE, Protocols.STATE_EFFECT, "new prompt", List.of(),
                null, null, null, List.of(), Map.of())), results);
    }

    @Test
    void applyUpdatesNormalizesLegacyReplaceUpdates() {
        RecordingOperator operator = new RecordingOperator("op1");
        Map<UpdateKey, Object> updates = new LinkedHashMap<>();
        updates.put(UpdateKey.of("op1", "system_prompt"), "new prompt");

        List<ApplyResult> results = UpdateExecution.applyUpdates(Map.of("op1", operator), updates);

        assertEquals(List.of(List.of("system_prompt", "new prompt")), operator.getCalls());
        assertEquals(1, results.size());
        assertTrue(results.get(0).isApplied());
    }

    @Test
    void applyUpdatesSupportsMixedLegacyAndStructuredBatches() {
        RecordingOperator operator = new RecordingOperator("op1");
        Map<UpdateKey, Object> updates = new LinkedHashMap<>();
        updates.put(UpdateKey.of("op1", "system_prompt"), "prompt");
        updates.put(UpdateKey.of("op1", Protocols.EXPERIENCES_TARGET), UpdateValue.builder()
                .payload(List.of("record-1"))
                .mode(Protocols.APPEND_MODE)
                .effect(Protocols.PENDING_CHANGE_EFFECT)
                .changeType(Protocols.SKILL_EXPERIENCE_ENTRY)
                .metadata(Map.of("change_type", Protocols.SKILL_EXPERIENCE_ENTRY))
                .build());

        List<ApplyResult> results = UpdateExecution.applyUpdates(Map.of("op1", operator), updates);

        assertEquals(List.of(List.of("system_prompt", "prompt")), operator.getCalls());
        assertTrue(results.get(0).isApplied());
        assertFalse(results.get(1).isApplied());
        assertEquals(Protocols.APPEND_MODE, results.get(1).getMode());
        assertEquals(Protocols.PENDING_CHANGE_EFFECT, results.get(1).getEffect());
    }

    @Test
    void applyUpdatesReportsMissingOperatorsAndNoneValuesWithoutCrashing() {
        RecordingOperator operator = new RecordingOperator("op1");
        Map<UpdateKey, Object> updates = new LinkedHashMap<>();
        updates.put(UpdateKey.of("missing", "param"), "value");
        updates.put(UpdateKey.of("op1", "param"), null);

        List<ApplyResult> results = UpdateExecution.applyUpdates(Map.of("op1", operator), updates);

        assertEquals(List.of(), operator.getCalls());
        assertEquals("missing", results.get(0).getOperatorId());
        assertFalse(results.get(0).isApplied());
        assertEquals("op1", results.get(1).getOperatorId());
        assertEquals(List.of("update value is None"), results.get(1).getErrors());
    }

    @Test
    void applyUpdatesReturnsFailedApplyResultFromOperator() {
        FailingOperator operator = new FailingOperator("op1");
        Map<UpdateKey, Object> updates = new LinkedHashMap<>();
        updates.put(UpdateKey.of("op1", "system_prompt"), "new prompt");

        List<ApplyResult> results = UpdateExecution.applyUpdates(Map.of("op1", operator), updates);

        assertEquals(List.of(ApplyResult.builder()
                .operatorId("op1")
                .target("system_prompt")
                .applied(false)
                .mode(Protocols.REPLACE_MODE)
                .effect(Protocols.STATE_EFFECT)
                .value("new prompt")
                .errors(List.of("apply failed"))
                .build()), results);
    }

    @Test
    void summarizeApplyResultsCountsAppliedAndFailedUpdates() {
        Map<String, Integer> summary = UpdateExecution.summarizeApplyResults(List.of(
                new ApplyResult("op1", "a", true),
                ApplyResult.builder()
                        .operatorId("op1")
                        .target("b")
                        .applied(false)
                        .errors(List.of("bad update"))
                        .build()
        ));

        assertEquals(2, summary.get("total"));
        assertEquals(1, summary.get("applied"));
        assertEquals(1, summary.get("failed"));
    }

    private static class RecordingOperator extends Operator {
        private final String operatorId;
        private final List<List<String>> calls = new ArrayList<>();
        private final Map<String, Object> state = new LinkedHashMap<>();

        RecordingOperator(String operatorId) {
            this.operatorId = operatorId;
        }

        @Override
        public String getOperatorId() {
            return operatorId;
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return Map.of();
        }

        @Override
        public Map<String, Object> getState() {
            return new LinkedHashMap<>(state);
        }

        @Override
        public void setParameter(String target, Object value) {
            calls.add(List.of(target, String.valueOf(value)));
            state.put(target, value);
        }

        @Override
        public void loadState(Map<String, Object> state) {
            this.state.clear();
            if (state != null) {
                this.state.putAll(state);
            }
        }

        List<List<String>> getCalls() {
            return List.copyOf(calls);
        }
    }

    private static final class FailingOperator extends RecordingOperator {
        FailingOperator(String operatorId) {
            super(operatorId);
        }

        @Override
        public ApplyResult applyUpdate(String target, UpdateValue update) {
            return ApplyResult.builder()
                    .operatorId(getOperatorId())
                    .target(target)
                    .applied(false)
                    .mode(update.getMode())
                    .effect(update.getEffect())
                    .value(update.getPayload())
                    .errors(List.of("apply failed"))
                    .build();
        }
    }
}
