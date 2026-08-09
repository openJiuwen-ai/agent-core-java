/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.signal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code test_base} module in
 * {@code tests/unit_tests/agent_evolving/signal/test_base.py}.
 */
class EvolutionSignalBaseTest {

    @Test
    void toDictUsesOnlyStableTopLevelFields() {
        Map<String, Object> context = linkedMap("source", "passive_conversation", "tool_name", "bash");
        EvolutionSignal signal = EvolutionSignal.builder()
                .signalType("execution_failure")
                .section("Troubleshooting")
                .excerpt("tool timeout")
                .skillName("skill-a")
                .context(context)
                .build();

        assertEquals(linkedMap(
                "type", "execution_failure",
                "section", "Troubleshooting",
                "excerpt", "tool timeout",
                "skill_name", "skill-a",
                "context", context
        ), signal.toDict());
    }

    @Test
    void makeEvolutionSignalMovesToolNameIntoContext() {
        EvolutionSignal signal = EvolutionSignals.makeEvolutionSignal(
                "execution_failure",
                "Troubleshooting",
                "tool timeout",
                "bash",
                "skill-a",
                "passive_conversation",
                null
        );

        assertEquals(linkedMap("source", "passive_conversation", "tool_name", "bash"), signal.getContext());
    }

    @Test
    void makeSignalFingerprintReadsToolNameFromContext() {
        EvolutionSignal signal = EvolutionSignal.builder()
                .signalType("execution_failure")
                .section("Troubleshooting")
                .excerpt("tool timeout")
                .skillName("skill-a")
                .context(Map.of("tool_name", "bash"))
                .build();

        assertArrayEquals(
                new String[] {"execution_failure", "bash", "skill-a", "tool timeout"},
                EvolutionSignals.makeSignalFingerprint(signal)
        );
    }

    private static Map<String, Object> linkedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            map.put(String.valueOf(keysAndValues[index]), keysAndValues[index + 1]);
        }
        return map;
    }
}
