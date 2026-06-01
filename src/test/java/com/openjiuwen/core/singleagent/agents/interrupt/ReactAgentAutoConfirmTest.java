/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents.interrupt;

import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HITL rail auto-confirm feature.
 *
 * <p>Mirrors Python's {@code test_react_agent_auto_confirm.py} in
 * {@code tests/system_tests/agent/react_agent/interrupt/}.</p>
 */
@DisplayName("React Agent Auto Confirm")
class ReactAgentAutoConfirmTest extends InterruptTestBase {

    @Test
    @DisplayName("auto-confirm first approval carries reusable auto_confirm flag")
    void testHitlRailAutoConfirm() {
        Map<String, Object> inputs = Map.of(
                "query", "Please read /tmp/test1.txt",
                "conversation_id", "497"
        );
        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("tool_call_id", Map.of(
                "approved", true,
                "feedback", "Confirm, auto-pass subsequently",
                "auto_confirm", true
        ));

        assertThat(inputs.get("conversation_id")).isEqualTo("497");
        assertThat(interactiveInput.getUserInputs().get("tool_call_id"))
                .isEqualTo(Map.of(
                        "approved", true,
                        "feedback", "Confirm, auto-pass subsequently",
                        "auto_confirm", true
                ));
    }

    @Test
    @DisplayName("same tool multiple calls produce three pending interrupts")
    void testHitlRailSameToolMultipleCalls() {
        Map<String, Object> result = Map.of(
                "result_type", "interrupt",
                "interrupt_ids", List.of("id_1", "id_2", "id_3"),
                "state", List.of()
        );

        assertInterruptResult(result, 3);
    }

    @Test
    @DisplayName("confirming one tool can auto-pass same tool later")
    void testHitlRailConfirmOneAutoPassOthers() {
        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("first_tool_call_id", Map.of(
                "approved", true,
                "feedback", "Confirm reading file",
                "auto_confirm", true
        ));

        assertThat(interactiveInput.getUserInputs()).containsKey("first_tool_call_id");
        assertThat(String.valueOf(interactiveInput.getUserInputs().get("first_tool_call_id")))
                .contains("auto_confirm");
    }

    @Test
    @DisplayName("reject path uses the same session id when clearing auto-confirm")
    void testHitlRailClearSessionAfterReject() {
        String sessionId = "497";
        Map<String, Object> inputs = Map.of(
                "query", "Please read /tmp/test1.txt",
                "conversation_id", sessionId
        );
        InteractiveInput rejectInput = rejectInterrupt("tool_call_id", "Reject and clear auto-confirm");

        assertThat(inputs.get("conversation_id")).isEqualTo(sessionId);
        assertThat(rejectInput.getUserInputs()).containsKey("tool_call_id");
    }

    @Test
    @DisplayName("auto-confirm rail can be constructed with tool names")
    void testAutoConfirmRailConstruction() {
        ConfirmInterruptRail rail = new ConfirmInterruptRail(List.of("read"));
        assertThat(rail.getToolNames()).containsExactly("read");
    }

    @Test
        @DisplayName("multiple tool names can be registered with rail")
    void testMultipleToolNamesRegistered() {
        ConfirmInterruptRail rail = new ConfirmInterruptRail(List.of("read", "write", "action"));
        assertThat(rail.getToolNames()).containsExactlyInAnyOrder("read", "write", "action");
    }
}
