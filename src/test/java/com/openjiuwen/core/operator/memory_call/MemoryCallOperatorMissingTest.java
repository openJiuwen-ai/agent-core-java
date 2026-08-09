/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.memory_call;

import com.openjiuwen.agentevolving.ApplyResult;
import com.openjiuwen.agentevolving.UpdateValue;
import com.openjiuwen.core.operator.TunableSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Missing supplemental parity tests for memory-call operator state handling.
 *
 * <p>Mirrors Python's {@code TestMemoryCallOperator} and
 * {@code TestMemoryCallOperatorCallbacks} in
 * {@code tests/unit_tests/core/operator/test_memory_call.py}.</p>
 */
class MemoryCallOperatorMissingTest {

    @Test
    void defaultOperatorIdIsMemoryCall() {
        assertThat(new MemoryCallOperator().getOperatorId()).isEqualTo("memory_call");
    }

    @Test
    void customOperatorIdIsPreserved() {
        MemoryCallOperator operator = new MemoryCallOperator("custom_memory");

        assertThat(operator.getOperatorId()).isEqualTo("custom_memory");
    }

    @Test
    void tunablesExposeDiscreteKinds() {
        Map<String, TunableSpec> tunables = new MemoryCallOperator().getTunables();

        assertThat(tunables).containsKeys("enabled", "max_retries");
        assertThat(tunables.get("enabled").kind()).isEqualTo("discrete");
        assertThat(tunables.get("max_retries").kind()).isEqualTo("discrete");
    }

    @Test
    void tunablesExposeConstraints() {
        Map<String, TunableSpec> tunables = new MemoryCallOperator().getTunables();

        assertThat(tunables.get("enabled").constraint()).isEqualTo(Map.of("type", "bool"));
        assertThat(tunables.get("max_retries").constraint())
                .isEqualTo(Map.of("type", "int", "min", 0, "max", 5));
    }

    @Test
    void setParameterEnabledRoundTripsState() {
        MemoryCallOperator operator = new MemoryCallOperator();

        operator.setParameter("enabled", false);
        assertThat(operator.getState()).containsEntry("enabled", false);
        operator.setParameter("enabled", true);
        assertThat(operator.getState()).containsEntry("enabled", true);
    }

    @Test
    void setParameterMaxRetriesUpdatesState() {
        MemoryCallOperator operator = new MemoryCallOperator();

        operator.setParameter("max_retries", 3);

        assertThat(operator.getState()).containsEntry("max_retries", 3);
    }

    @Test
    void setParameterMaxRetriesClampsToBounds() {
        MemoryCallOperator operator = new MemoryCallOperator();

        operator.setParameter("max_retries", 10);
        assertThat(operator.getState()).containsEntry("max_retries", 5);
        operator.setParameter("max_retries", -1);
        assertThat(operator.getState()).containsEntry("max_retries", 0);
    }

    @Test
    void getStateReturnsDefaults() {
        Map<String, Object> state = new MemoryCallOperator().getState();

        assertThat(state).containsEntry("enabled", true)
                .containsEntry("max_retries", 0);
    }

    @Test
    void getStateReflectsLoadedCustomValues() {
        MemoryCallOperator operator = new MemoryCallOperator();
        operator.loadState(Map.of("enabled", false, "max_retries", 3));

        assertThat(operator.getState()).containsEntry("enabled", false)
                .containsEntry("max_retries", 3);
    }

    @Test
    void loadStateRestoresBothParameters() {
        MemoryCallOperator operator = new MemoryCallOperator();

        operator.loadState(Map.of("enabled", false, "max_retries", 2));

        assertThat(operator.getState()).containsEntry("enabled", false)
                .containsEntry("max_retries", 2);
    }

    @Test
    void loadStatePartialKeepsDefaultRetries() {
        MemoryCallOperator operator = new MemoryCallOperator();

        operator.loadState(Map.of("enabled", false));

        assertThat(operator.getState()).containsEntry("enabled", false)
                .containsEntry("max_retries", 0);
    }

    @Test
    void loadStateClampsRetriesToBounds() {
        MemoryCallOperator operator = new MemoryCallOperator();

        operator.loadState(Map.of("max_retries", 10));
        assertThat(operator.getState()).containsEntry("max_retries", 5);
        operator.loadState(Map.of("max_retries", -1));
        assertThat(operator.getState()).containsEntry("max_retries", 0);
    }

    @Test
    void setParameterEnabledTriggersCallback() {
        List<String> updates = new ArrayList<>();
        MemoryCallOperator operator = new MemoryCallOperator("memory_call",
                (target, value) -> updates.add(target + "=" + value));

        operator.setParameter("enabled", false);

        assertThat(updates).containsExactly("enabled=false");
    }

    @Test
    void setParameterMaxRetriesTriggersCallback() {
        List<String> updates = new ArrayList<>();
        MemoryCallOperator operator = new MemoryCallOperator("memory_call",
                (target, value) -> updates.add(target + "=" + value));

        operator.setParameter("max_retries", 3);

        assertThat(updates).containsExactly("max_retries=3");
    }

    @Test
    void loadStateTriggersCallbackForBothParameters() {
        List<String> updates = new ArrayList<>();
        MemoryCallOperator operator = new MemoryCallOperator("memory_call",
                (target, value) -> updates.add(target + "=" + value));

        operator.loadState(Map.of("enabled", false, "max_retries", 2));

        assertThat(updates).containsExactlyInAnyOrder("enabled=false", "max_retries=2");
    }

    @Test
    void setParameterUnknownTargetIsNoop() {
        MemoryCallOperator operator = new MemoryCallOperator();

        operator.setParameter("unknown", "value");

        assertThat(operator.getState()).containsEntry("enabled", true)
                .containsEntry("max_retries", 0);
    }

    @Test
    void applyUpdateUsesReplaceStateBehavior() {
        MemoryCallOperator operator = new MemoryCallOperator();

        ApplyResult result = operator.applyUpdate("max_retries", new UpdateValue(3));

        assertThat(operator.getState()).containsEntry("max_retries", 3);
        assertThat(result.isApplied()).isTrue();
        assertThat(result.getValue()).isEqualTo(3);
    }

    @Test
    void applyUpdateUnknownTargetReportsNoop() {
        MemoryCallOperator operator = new MemoryCallOperator();

        ApplyResult result = operator.applyUpdate("unknown", new UpdateValue("value"));

        assertThat(result.isApplied()).isFalse();
    }
}
