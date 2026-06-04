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
 * Mirrors Python's {@code test_interrupt_exception_scenarios.py} in
 * {@code tests.unit_tests.agent.react_agent.interrupt}.
 */
class InterruptExceptionScenariosTest extends InterruptTestBase {

    @Test
    @DisplayName("wrong tool-call id keeps the original interrupt pending")
    void testRecoveryWithWrongToolCallId() {
        ActionTool actionTool = new ActionTool("action");
        AssistantFlow flow = newConfirmFlow(new ConfirmInterruptRail(List.of("action")), actionTool);

        Map<String, Object> first = flow.start(action("correct_id"));
        List<String> ids = assertInterruptResult(first);

        InteractiveInput wrong = new InteractiveInput();
        wrong.update("wrong_id_12345", Map.of("approved", true, "feedback", "Wrong ID"));
        Map<String, Object> second = flow.resume(wrong);

        assertEquals("interrupt", second.get("result_type"));
        assertTrue(interruptIds(second).contains(ids.get(0)));
        assertEquals(0, actionTool.getInvokeCount());

        Map<String, Object> third = flow.resume(confirmInterrupt(ids.get(0)));
        assertAnswerResult(third);
        assertEquals(1, actionTool.getInvokeCount());
    }

    @Test
    @DisplayName("empty interactive input leaves interrupt pending")
    void testEmptyInteractiveInputRecovery() {
        ActionTool actionTool = new ActionTool("action");
        AssistantFlow flow = newConfirmFlow(new ConfirmInterruptRail(List.of("action")), actionTool);

        Map<String, Object> first = flow.start(action("id_remaining"));
        assertInterruptResult(first);

        Map<String, Object> second = flow.resume(new InteractiveInput());
        List<String> remaining = assertInterruptResult(second);
        assertEquals(List.of("id_remaining"), remaining);
        assertEquals(0, actionTool.getInvokeCount());

        Map<String, Object> third = flow.resume(confirmInterrupt(remaining.get(0)));
        assertAnswerResult(third);
        assertEquals(1, actionTool.getInvokeCount());
    }

    @Test
    @DisplayName("resuming in the wrong session does not execute pending action")
    void testSessionSwitchRecovery() {
        ActionTool actionTool = new ActionTool("action");
        ConfirmInterruptRail rail = new ConfirmInterruptRail(List.of("action"));
        AssistantFlow sessionA = newConfirmFlow(rail, actionTool);
        AssistantFlow sessionB = newConfirmFlow(rail, actionTool);

        List<String> ids = assertInterruptResult(sessionA.start(action("session_a_id")));

        Map<String, Object> wrongSession = sessionB.resume(confirmInterrupt(ids.get(0)));
        assertAnswerResult(wrongSession);
        assertEquals(0, actionTool.getInvokeCount());

        Map<String, Object> correctSession = sessionA.resume(confirmInterrupt(ids.get(0)));
        assertAnswerResult(correctSession);
        assertEquals(1, actionTool.getInvokeCount());
    }

    private com.openjiuwen.core.foundation.llm.schema.ToolCall action(String id) {
        return toolCall(id, "action", "{\"action\": \"test\"}");
    }
}
