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

/**
 * Mirrors Python's {@code test_react_agent_auto_confirm.py} in
 * {@code tests.unit_tests.agent.react_agent.interrupt}.
 */
class ReactAgentAutoConfirmTest extends InterruptTestBase {

    @Test
    @DisplayName("confirm once with auto-confirm allows later same-name reads")
    void testHitlRailAutoConfirm() {
        ReadTool readTool = new ReadTool();
        AssistantFlow flow = newConfirmFlow(new ConfirmInterruptRail(List.of("read")), readTool);

        List<String> ids = assertInterruptResult(flow.start(
            toolCall("read1", "read", "{\"filepath\": \"/tmp/test1.txt\"}")
        ));
        assertAnswerResult(flow.resume(confirmInterrupt(ids.get(0), true)));
        assertEquals(1, readTool.getInvokeCount());

        assertAnswerResult(flow.start(
            toolCall("read2", "read", "{\"filepath\": \"/tmp/test2.txt\"}")
        ));
        assertEquals(2, readTool.getInvokeCount());
    }

    @Test
    @DisplayName("same tool multiple calls are confirmed independently")
    void testHitlRailSameToolMultipleCalls() {
        ActionTool actionTool = new ActionTool("multi_action");
        AssistantFlow flow = newConfirmFlow(new ConfirmInterruptRail(List.of("multi_action")), actionTool);

        Map<String, Object> first = flow.start(
            toolCall("c1", "multi_action", "{\"action\": \"action1\"}"),
            toolCall("c2", "multi_action", "{\"action\": \"action2\"}"),
            toolCall("c3", "multi_action", "{\"action\": \"action3\"}")
        );
        assertInterruptResult(first, 3);

        Map<String, Object> current = first;
        int confirmedCount = 0;
        while ("interrupt".equals(current.get("result_type"))) {
            List<String> currentIds = interruptIds(current);
            if (currentIds.isEmpty()) {
                break;
            }
            confirmedCount++;
            current = flow.resume(confirmInterrupt(currentIds.get(0)));
        }

        assertAnswerResult(current);
        assertEquals(confirmedCount, actionTool.getInvokeCount());
        assertEquals(3, confirmedCount);
    }

    @Test
    @DisplayName("confirming one read with auto-confirm auto-passes concurrent siblings")
    void testHitlRailConfirmOneAutoPassOthers() {
        ReadTool readTool = new ReadTool();
        AssistantFlow flow = newConfirmFlow(new ConfirmInterruptRail(List.of("read")), readTool);

        Map<String, Object> first = flow.start(
            toolCall("c1", "read", "{\"filepath\": \"/tmp/file1.txt\"}"),
            toolCall("c2", "read", "{\"filepath\": \"/tmp/file2.txt\"}"),
            toolCall("c3", "read", "{\"filepath\": \"/tmp/file3.txt\"}")
        );
        List<String> ids = assertInterruptResult(first, 3);

        Map<String, Object> second = flow.resume(confirmInterrupt(ids.get(0), true));
        assertAnswerResult(second);
        assertEquals(3, readTool.getInvokeCount());
    }

    @Test
    @DisplayName("auto-confirm payload records requested flag")
    void testAutoConfirmPayloadRecordsRequestedFlag() {
        InteractiveInput input = confirmInterrupt("tool_call_id", true);
        assertEquals(
            Map.of("approved", true, "feedback", "Confirm", "auto_confirm", true),
            input.getUserInputs().get("tool_call_id")
        );
    }
}
