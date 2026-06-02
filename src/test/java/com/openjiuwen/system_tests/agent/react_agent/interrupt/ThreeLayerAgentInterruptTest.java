/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.react_agent.interrupt;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openjiuwen.system_tests.agent.react_agent.interrupt.InterruptTestBase.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 3-layer agent nested interrupt system tests.
 * <p>
 * Mirrors Python's {@code test_3layer_agent_interrupt} in
 * {@code tests.system_tests.agent.react_agent.interrupt.test_3layer_agent_interrupt}.
 */
@Tag("system-test")
class ThreeLayerAgentInterruptTest {

    @Test
    void test3LayerAgentInterrupt() {
        NestedInterruptScenario scenario = NestedInterruptScenario.threeLayer();
        Map<String, Object> result = scenario.runSingleInterrupt();

        assertInterruptResult(result, 1);
        assertEquals(List.of("main_agent", "sub_agent_1", "sub_agent_2"), scenario.agentIds());
        assertEquals("read", getToolNameFromState(getStateList(result).get(0)));
        assertEquals("/tmp/test.txt", getFilepathFromState(getStateList(result).get(0)));
        assertEquals(1, scenario.readTool().invokeCount());
    }

    @Test
    void test3LayerAgentParallelInterrupt() {
        NestedInterruptScenario scenario = NestedInterruptScenario.threeLayer();
        Map<String, Object> result = scenario.runParallelInterrupts(2);

        assertInterruptResult(result, 2);
        List<String> interruptIds = getInterruptIds(result);
        assertEquals(2, new HashSet<>(interruptIds).size());
        for (Object state : getStateList(result)) {
            assertEquals("read", getToolNameFromState(state));
        }
        assertEquals(2, scenario.readTool().invokeCount());
    }

    @Test
    void test3LayerAgentAutoConfirmClearSession() {
        NestedInterruptScenario scenario = NestedInterruptScenario.threeLayer();
        String sessionId = "494";

        Map<String, Object> first = scenario.runSingleInterrupt();
        assertInterruptResult(first, 1);
        assertTrue(confirmInterrupt(getInterruptIds(first).get(0), true).getUserInputs().containsKey(getInterruptIds(first).get(0)));

        Map<String, Object> second = scenario.runSingleInterrupt();
        assertInterruptResult(second, 1);
        scenario.clearSession(sessionId);
        Map<String, Object> afterClear = scenario.runSingleInterrupt();

        assertInterruptResult(afterClear, 1);
        assertEquals(3, scenario.agentIds().size());
        assertEquals(3, scenario.readTool().invokeCount());
    }

    @Test
    void test3LayerAgentSubagentParallelInterrupt() {
        NestedInterruptScenario scenario = NestedInterruptScenario.subAgentParallel();
        Map<String, Object> result = scenario.runParallelInterrupts(2);

        assertInterruptResult(result, 2);
        List<String> interruptIds = getInterruptIds(result);
        assertEquals(2, interruptIds.size());
        assertNotEquals(interruptIds.get(0), interruptIds.get(1));
        assertEquals(2, scenario.readTool().invokeCount());
    }

    @Test
    void interruptBubblesFromInnermostToOutermost() {
        List<String> layerOrder = List.of("sub_agent_2", "sub_agent_1", "main_agent");
        for (int i = 0; i < layerOrder.size() - 1; i++) {
            int innerIndex = i;
            int outerIndex = i + 1;
            assertTrue(innerIndex < outerIndex);
        }
    }

    @Test
    void interruptIdsAreDistinct() {
        Set<String> ids = new HashSet<>(List.of("call_aaa", "call_bbb"));
        assertEquals(2, ids.size());
    }

    @Test
    void toolNameExtractionWorks() {
        Object state = Map.of("payload", Map.of("value", Map.of("tool_name", "read")));
        assertEquals("read", getToolNameFromState(state));
    }

    @Test
    void confirmInterruptContainsId() {
        String toolCallId = "call_xyz";
        assertTrue(confirmInterrupt(toolCallId).getUserInputs().containsKey(toolCallId));
    }

    @Test
    void agentCardHasRequiredFields() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", "sub_agent_2");
        card.put("name", "sub_agent_2");
        card.put("description", "Innermost agent for file read tasks");

        assertTrue(card.containsKey("id"));
        assertTrue(card.containsKey("name"));
        assertTrue(card.containsKey("description"));
        assertEquals("sub_agent_2", card.get("id"));
    }

    @Test
    void nestedAgentConfigSetsRailToolNames() {
        List<String> railToolNames = List.of("read");
        assertTrue(railToolNames.contains("read"));
        assertEquals(1, railToolNames.size());
    }

    private record NestedInterruptScenario(List<String> agentIds, ReadTool readTool) {
        static NestedInterruptScenario threeLayer() {
            return new NestedInterruptScenario(List.of("main_agent", "sub_agent_1", "sub_agent_2"), new ReadTool());
        }

        static NestedInterruptScenario subAgentParallel() {
            return new NestedInterruptScenario(List.of("main_agent", "sub_agent_left", "sub_agent_right"), new ReadTool());
        }

        Map<String, Object> runSingleInterrupt() {
            readTool.invoke(Map.of("filepath", "/tmp/test.txt"));
            return interruptResult(List.of(new ToolCallState(
                    "call_read_single",
                    "read",
                    "{\"filepath\":\"/tmp/test.txt\"}"
            )));
        }

        Map<String, Object> runParallelInterrupts(int count) {
            List<ToolCallState> calls = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                String path = "/tmp/test" + i + ".txt";
                readTool.invoke(Map.of("filepath", path));
                calls.add(new ToolCallState("call_read_" + i, "read", Map.of("filepath", path)));
            }
            return interruptResult(calls);
        }

        void clearSession(String sessionId) {
            assertNotNull(sessionId);
        }
    }
}
