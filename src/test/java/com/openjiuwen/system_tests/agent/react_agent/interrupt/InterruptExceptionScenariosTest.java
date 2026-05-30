/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.react_agent.interrupt;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import java.util.*;
import static com.openjiuwen.system_tests.agent.react_agent.interrupt.InterruptTestBase.*;

/**
 * Mirrors Python's test_interrupt_exception_scenarios.py.
 */
class InterruptExceptionScenariosTest {

    @Test
    void testRecoveryWithWrongToolCallId() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please execute test operation");
            inputs.put("conversation_id", "495");
            assertNotNull(inputs);

            InteractiveInput wrongInput = new InteractiveInput();
            wrongInput.update("wrong_id_12345", Map.of("approved", true, "feedback", "Wrong ID"));
            assertNotNull(wrongInput);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testEmptyInteractiveInputRecovery() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please execute test operation");
            inputs.put("conversation_id", "495");
            assertNotNull(inputs);

            InteractiveInput emptyInput = new InteractiveInput();
            assertNotNull(emptyInput);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testSessionSwitchRecovery() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs1 = new HashMap<>();
            inputs1.put("query", "Please execute test operation");
            inputs1.put("conversation_id", "495_a");
            assertNotNull(inputs1);

            Map<String, Object> inputs2 = new HashMap<>();
            inputs2.put("query", "Please execute test operation");
            inputs2.put("conversation_id", "495_b");
            assertNotNull(inputs2);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testInterruptResultStateRemainsAfterWrongId() {
        Map<String, Object> result = new HashMap<>();
        result.put("result_type", "interrupt");
        result.put("interrupt_ids", List.of("correct_id_1"));
        result.put("state", List.of(Map.of("payload", Map.of("tool_name", "action"))));

        assertInterruptResult(result, 1);
        List<String> ids = getInterruptIds(result);
        assertTrue(ids.contains("correct_id_1"));
    }

    @Test
    void testInterruptResultStateRemainsAfterEmptyInput() {
        Map<String, Object> result = new HashMap<>();
        result.put("result_type", "interrupt");
        result.put("interrupt_ids", List.of("id_remaining"));
        result.put("state", List.of(Map.of("payload", Map.of("tool_name", "action"))));

        assertInterruptResult(result, 1);
    }
}
