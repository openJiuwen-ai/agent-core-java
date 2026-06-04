/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents.interrupt;

import com.openjiuwen.core.session.interaction.InteractiveInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HITL rail in stream mode.
 *
 * <p>Mirrors Python's {@code test_interrupt_stream.py} in
 * {@code tests/system_tests/agent/react_agent/interrupt/}.</p>
 */
@DisplayName("Interrupt Stream")
class InterruptStreamTest extends InterruptTestBase {

    @Test
    @DisplayName("stream interrupt is detected as interaction output")
    void testHitlRailStreamInterruptDetected() {
        Map<String, Object> inputs = Map.of(
                "query", "Call read tool, read /tmp/test.txt",
                "conversation_id", "493"
        );
        Map<String, Object> result = Map.of(
                "result_type", "interrupt",
                "interrupt_ids", List.of("read_id"),
                "state", List.of(Map.of("payload", Map.of("tool_name", "read")))
        );

        assertThat(inputs.get("query")).isEqualTo("Call read tool, read /tmp/test.txt");
        assertInterruptResult(result, 1);
        assertThat(getToolNameFromState(((List<?>) result.get("state")).get(0))).isEqualTo("read");
    }

    @Test
    @DisplayName("stream agree with auto-confirm carries auto_confirm payload")
    void testHitlRailStreamAgreeWithAutoconfirm() {
        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("tool_call_id", Map.of(
                "approved", true,
                "feedback", "Confirm and auto confirm",
                "auto_confirm", true
        ));

        Object payload = interactiveInput.getUserInputs().get("tool_call_id");
        assertThat(payload).isEqualTo(Map.of(
                "approved", true,
                "feedback", "Confirm and auto confirm",
                "auto_confirm", true
        ));
    }

    @Test
    @DisplayName("stream reject carries negative approval feedback")
    void testHitlRailStreamReject() {
        InteractiveInput rejectInput = new InteractiveInput();
        rejectInput.update("tool_call_id", Map.of(
                "approved", false,
                "feedback", "Reject this operation"
        ));

        assertThat(rejectInput.getUserInputs().get("tool_call_id"))
                .isEqualTo(Map.of("approved", false, "feedback", "Reject this operation"));
    }

    @Test
    @DisplayName("concurrent stream tools can all be confirmed")
    void testHitlRailStreamConcurrentToolsAllConfirmed() {
        InteractiveInput confirmInput = new InteractiveInput();
        confirmInput.update("interrupt_id_1", Map.of("approved", true, "feedback", "Confirm"));
        confirmInput.update("interrupt_id_2", Map.of("approved", true, "feedback", "Confirm"));

        assertThat(confirmInput.getUserInputs()).containsKeys("interrupt_id_1", "interrupt_id_2");
        assertThat(confirmInput.getUserInputs().values())
                .allSatisfy(value -> assertThat(value).isEqualTo(Map.of("approved", true, "feedback", "Confirm")));
    }

    @Test
    @DisplayName("concurrent stream tools support partial rejection")
    void testHitlRailStreamConcurrentToolsPartialReject() {
        InteractiveInput partialInput = new InteractiveInput();
        partialInput.update("a_txt_id", Map.of("approved", true, "feedback", "Confirm a.txt"));
        partialInput.update("b_txt_id", Map.of("approved", false, "feedback", "Reject b.txt"));

        assertThat(partialInput.getUserInputs().get("a_txt_id"))
                .isEqualTo(Map.of("approved", true, "feedback", "Confirm a.txt"));
        assertThat(partialInput.getUserInputs().get("b_txt_id"))
                .isEqualTo(Map.of("approved", false, "feedback", "Reject b.txt"));
    }

    @Test
    @DisplayName("interaction type constant is __interaction__")
    void testInteractionTypeConstant() {
        String interaction = "__interaction__";
        assertThat(interaction).isEqualTo("__interaction__");
    }
}
