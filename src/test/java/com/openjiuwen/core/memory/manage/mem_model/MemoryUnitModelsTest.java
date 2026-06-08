/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemoryUnitModelsTest {

    @Test
    void memoryEnumsMirrorPythonValues() {
        assertThat(MemoryType.USER_PROFILE.getValue()).isEqualTo("user_profile");
        assertThat(MemoryType.SEMANTIC_MEMORY.getValue()).isEqualTo("semantic_memory");
        assertThat(MemoryType.EPISODIC_MEMORY.getValue()).isEqualTo("episodic_memory");
        assertThat(OperationType.DELETE.getValue()).isEqualTo("delete");
        assertThat(SupportMemoryType.SUMMARY.getValue()).isEqualTo("summary");
    }

    @Test
    void fragmentMemoryUnitPreservesOptionalFields() {
        FragmentMemoryUnit unit = new FragmentMemoryUnit(
                MemoryType.SEMANTIC_MEMORY,
                "mem-1",
                "content",
                "msg-1",
                "2025-01-01 00:00:00",
                OperationType.ADD
        );

        assertThat(unit.getMemType()).isEqualTo(MemoryType.SEMANTIC_MEMORY);
        assertThat(unit.getMessageMemId()).isEqualTo("msg-1");
        assertThat(unit.getOperationType()).isEqualTo(OperationType.ADD);
    }

    @Test
    void variableAndSummaryUnitsEnforceFixedTypes() {
        VariableUnit variableUnit = new VariableUnit("name", "value");
        SummaryUnit summaryUnit = new SummaryUnit("mem-2", "summary", "msg-2", "2025-01-01 00:00:00");

        variableUnit.setMemType(MemoryType.UNKNOWN);
        variableUnit.setMemId("ignored");
        summaryUnit.setMemType(MemoryType.UNKNOWN);

        assertThat(variableUnit.getMemType()).isEqualTo(MemoryType.VARIABLE);
        assertThat(variableUnit.getMemId()).isEmpty();
        assertThat(summaryUnit.getMemType()).isEqualTo(MemoryType.SUMMARY);
        assertThat(summaryUnit.getMemId()).isEqualTo("mem-2");
    }
}
