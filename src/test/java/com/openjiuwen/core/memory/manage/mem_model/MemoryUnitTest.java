package com.openjiuwen.core.memory.manage.mem_model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        FragmentMemoryUnit unit = new FragmentMemoryUnit();
        unit.setMemType(MemoryType.EPISODIC_MEMORY);
        unit.setMemId("mem-1");
        unit.setContent("episode");
        unit.setMessageMemId("msg-1");
        unit.setTimestamp("2026-05-11T10:00:00+08:00");

        assertEquals(MemoryType.EPISODIC_MEMORY, unit.getMemType());
        assertEquals("mem-1", unit.getMemId());
        assertEquals("episode", unit.getContent());
        assertEquals("msg-1", unit.getMessageMemId());
        assertEquals("2026-05-11T10:00:00+08:00", unit.getTimestamp());
    }

    @Test
    void variableAndSummaryUnitsExposePythonDefaultTypes() {
        VariableUnit variable = new VariableUnit();
        variable.setVariableName("name");
        variable.setVariableMem("value");
        SummaryUnit summary = new SummaryUnit();
        summary.setSummary("summary");

        assertEquals(MemoryType.VARIABLE, variable.getMemType());
        assertEquals("", variable.getMemId());
        assertEquals(MemoryType.SUMMARY, summary.getMemType());
        assertEquals("", summary.getMemId());
    }
}
