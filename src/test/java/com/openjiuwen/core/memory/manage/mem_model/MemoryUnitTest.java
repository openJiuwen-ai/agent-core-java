
package com.openjiuwen.core.memory.manage.mem_model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MemoryUnitTest {
    @Test
    void memoryTypeValuesMatchPythonPublicModel() {
        assertEquals("user_profile", MemoryType.USER_PROFILE.getValue());
        assertEquals("semantic_memory", MemoryType.SEMANTIC_MEMORY.getValue());
        assertEquals("episodic_memory", MemoryType.EPISODIC_MEMORY.getValue());
        assertEquals("variable", MemoryType.VARIABLE.getValue());
        assertEquals("summary", MemoryType.SUMMARY.getValue());
        assertEquals("unknown", MemoryType.UNKNOWN.getValue());
        assertEquals(MemoryType.UNKNOWN, MemoryType.fromValue("missing"));
    }

    @Test
    void fragmentMemoryUnitKeepsCallerProvidedType() {
        FragmentMemoryUnit unit = FragmentMemoryUnit.builder().memType(MemoryType.EPISODIC_MEMORY).memId("mem-1")
                .content("episode").messageMemId("msg-1").timestamp("2026-05-11T10:00:00+08:00").build();

        assertEquals(MemoryType.EPISODIC_MEMORY, unit.getMemType());
        assertEquals("mem-1", unit.getMemId());
        assertEquals("episode", unit.getContent());
        assertEquals("msg-1", unit.getMessageMemId());
        assertEquals("2026-05-11T10:00:00+08:00", unit.getTimestamp());
    }

    @Test
    void variableAndSummaryUnitsExposePythonDefaultTypes() {
        VariableUnit variable = VariableUnit.builder().memType(MemoryType.USER_PROFILE).memId("ignored")
                .variableName("name").variableMem("value").build();
        SummaryUnit summary = SummaryUnit.builder().summary("summary").build();

        assertEquals(MemoryType.VARIABLE, variable.getMemType());
        assertEquals("", variable.getMemId());
        assertEquals(MemoryType.SUMMARY, summary.getMemType());
        assertNull(summary.getMemId());
    }
}
