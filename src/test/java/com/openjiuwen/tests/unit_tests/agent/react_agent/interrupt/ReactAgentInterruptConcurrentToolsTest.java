/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.agent.react_agent.interrupt;

import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_react_agent_interrupt_concurrent_tools.py} in
 * {@code tests.unit_tests.agent.react_agent.interrupt}.
 */
class ReactAgentInterruptConcurrentToolsTest extends InterruptTestBase {

    @Test
    @DisplayName("concurrent reads can be confirmed sequentially")
    void testHitlRailConcurrentToolsAllConfirmed() {
        ReadTool readTool = new ReadTool();
        AssistantFlow flow = newConfirmFlow(new ConfirmInterruptRail(List.of("read")), readTool);

        Map<String, Object> first = flow.start(read("c1", "a.txt"), read("c2", "b.txt"));
        List<String> ids = assertInterruptResult(first, 2);

        Map<String, Object> second = flow.resume(confirmInterrupt(ids.get(0)));
        List<String> remaining = assertInterruptResult(second);
        assertEquals(List.of(ids.get(1)), remaining);

        Map<String, Object> third = flow.resume(confirmInterrupt(remaining.get(0)));
        assertAnswerResult(third);
        assertEquals(2, readTool.getInvokeCount());
    }

    @Test
    @DisplayName("reject one and confirm one in a single round")
    void testHitlRailConcurrentToolsPartialRejectOneRound() {
        ReadTool readTool = new ReadTool();
        AssistantFlow flow = newConfirmFlow(new ConfirmInterruptRail(List.of("read")), readTool);

        Map<String, Object> first = flow.start(read("c1", "a.txt"), read("c2", "b.txt"));
        List<String> ids = assertInterruptResult(first, 2);
        List<Object> states = stateList(first);

        InteractiveInput input = new InteractiveInput();
        for (int i = 0; i < ids.size(); i++) {
            String filepath = getFilepathFromState(states.get(i));
            if ("b.txt".equals(filepath)) {
                input.update(ids.get(i), Map.of("approved", false, "feedback", "Reject reading b.txt"));
            } else {
                input.update(ids.get(i), Map.of("approved", true, "feedback", "Confirm read"));
            }
        }

        assertAnswerResult(flow.resume(input));
        assertEquals(1, readTool.getInvokeCount());
    }

    @Test
    @DisplayName("reject one then confirm the remaining read")
    void testHitlRailConcurrentToolsPartialRejectTwoRounds() {
        ReadTool readTool = new ReadTool();
        AssistantFlow flow = newConfirmFlow(new ConfirmInterruptRail(List.of("read")), readTool);

        Map<String, Object> first = flow.start(read("c1", "a.txt"), read("c2", "b.txt"));
        assertInterruptResult(first, 2);

        String aId = null;
        String bId = null;
        List<String> ids = interruptIds(first);
        List<Object> states = stateList(first);
        for (int i = 0; i < ids.size(); i++) {
            String filepath = getFilepathFromState(states.get(i));
            if ("a.txt".equals(filepath)) {
                aId = ids.get(i);
            } else if ("b.txt".equals(filepath)) {
                bId = ids.get(i);
            }
        }
        assertTrue(aId != null && bId != null);

        Map<String, Object> second = flow.resume(rejectInterrupt(bId, "Reject reading b.txt"));
        List<String> remaining = assertInterruptResult(second);
        assertTrue(remaining.contains(aId));
        assertEquals(1, remaining.size());

        assertAnswerResult(flow.resume(confirmInterrupt(aId)));
        assertEquals(1, readTool.getInvokeCount());
    }

    @Test
    @DisplayName("non-intercepted action passes while read interrupts")
    void testHitlRailConcurrentToolsOnePassOneInterrupt() {
        ReadTool readTool = new ReadTool();
        ActionTool actionTool = new ActionTool("action");
        AssistantFlow flow = newConfirmFlow(new ConfirmInterruptRail(List.of("read")), readTool, actionTool);

        Map<String, Object> first = flow.start(
            read("read_id", "a.txt"),
            toolCall("action_id", "action", "{\"action\": \"test\"}")
        );

        List<String> ids = assertInterruptResult(first);
        assertEquals(List.of("read_id"), ids);
        assertEquals("read", getToolNameFromState(stateList(first).get(0)));
        assertEquals(1, actionTool.getInvokeCount());

        assertAnswerResult(flow.resume(confirmInterrupt(ids.get(0))));
        assertEquals(1, readTool.getInvokeCount());
        assertEquals(1, actionTool.getInvokeCount());
    }

    private com.openjiuwen.core.foundation.llm.schema.ToolCall read(String id, String filepath) {
        return toolCall(id, "read", "{\"filepath\": \"" + filepath + "\"}");
    }
}
