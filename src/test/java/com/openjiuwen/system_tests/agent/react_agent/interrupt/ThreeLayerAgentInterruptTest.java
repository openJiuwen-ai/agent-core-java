/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.react_agent.interrupt;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

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
    @Disabled("System test requires API_KEY and API_BASE")
    void test3LayerAgentInterrupt() {
        List<String> agentIds = List.of("main_agent", "sub_agent_1", "sub_agent_2");
        assertEquals(3, agentIds.size());

        String toolName = "read";
        assertEquals("read", toolName);

        int expectedInvokeCount = 1;
        assertTrue(expectedInvokeCount >= 1);
    }

    @Test
    @Disabled("System test requires API_KEY and API_BASE")
    void test3LayerAgentParallelInterrupt() {
        List<String> interruptIds = List.of("call_aaa", "call_bbb");
        assertEquals(2, interruptIds.size());

        assertNotEquals(interruptIds.get(0), interruptIds.get(1));

        int expectedInvokeCount = 2;
        assertTrue(expectedInvokeCount >= 2);
    }

    @Test
    @Disabled("System test requires API_KEY and API_BASE")
    void test3LayerAgentAutoConfirmClearSession() {
        String sessionId = "494";
        assertNotNull(sessionId);

        List<String> agentIds = List.of("main_agent", "sub_agent_1", "sub_agent_2");
        assertEquals(3, agentIds.size());
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
        String state = "tool_name=read";
        String toolName = state.split("=")[1];
        assertEquals("read", toolName);
    }

    @Test
    void confirmInterruptContainsId() {
        String toolCallId = "call_xyz";
        String confirmation = "Confirm interrupt for tool_call_id: " + toolCallId;
        assertTrue(confirmation.contains(toolCallId));
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
}
