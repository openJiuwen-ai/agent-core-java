/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.agent.react_agent.interrupt;

import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code test_hitl_rail_chain_tools.py} in
 * {@code tests.unit_tests.agent.react_agent.interrupt}.
 */
class HitlRailChainToolsTest extends InterruptTestBase {

    @Test
    @DisplayName("multi-tool chain read confirm then write reject")
    void testHitlRailChainTools() {
        ReadTool readTool = new ReadTool();
        WriteTool writeTool = new WriteTool();
        AssistantFlow flow = newConfirmFlow(
            new ConfirmInterruptRail(List.of("read", "write")),
            readTool,
            writeTool
        );

        Map<String, Object> first = flow.start(
            toolCall("read_id", "read", "{\"filepath\": \"/tmp/test.txt\"}")
        );
        List<String> readIds = assertInterruptResult(first);
        assertEquals("read", getToolNameFromState(stateList(first).get(0)));

        assertAnswerResult(flow.resume(confirmInterrupt(readIds.get(0))));
        assertEquals(1, readTool.getInvokeCount());

        Map<String, Object> second = flow.start(
            toolCall("write_id", "write", "{\"filepath\": \"/tmp/test.txt\", \"content\": \"new content\"}")
        );
        List<String> writeIds = assertInterruptResult(second);
        assertEquals("write", getToolNameFromState(stateList(second).get(0)));

        Map<String, Object> third = flow.resume(rejectInterrupt(writeIds.get(0), "Reject write operation"));
        assertAnswerResult(third);
        assertEquals(1, readTool.getInvokeCount());
        assertEquals(0, writeTool.getInvokeCount());
    }
}
