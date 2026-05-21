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
 * Mirrors Python's test_react_agent_interrupt_concurrent_tools.py.
 */
class ReactAgentInterruptConcurrentToolsTest {

    @Test
    void testHitlRailConcurrentToolsAllConfirmed() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please read file a.txt and file b.txt simultaneously");
            inputs.put("conversation_id", "498");
            assertNotNull(inputs);

            Map<String, Object> result = new HashMap<>();
            result.put("result_type", "interrupt");
            result.put("interrupt_ids", List.of("id_a", "id_b"));
            assertInterruptResult(result, 2);

            InteractiveInput confirmFirst = confirmInterrupt("id_a");
            assertNotNull(confirmFirst);

            Map<String, Object> result2 = new HashMap<>();
            result2.put("result_type", "interrupt");
            result2.put("interrupt_ids", List.of("id_b"));
            assertInterruptResult(result2, 1);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testHitlRailConcurrentToolsPartialRejectOneRound() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please read file a.txt and file b.txt simultaneously");
            inputs.put("conversation_id", "498");
            assertNotNull(inputs);

            InteractiveInput interactiveInput = new InteractiveInput();
            interactiveInput.update("b_txt_id", Map.of("approved", false, "feedback", "Reject reading b.txt"));
            interactiveInput.update("a_txt_id", Map.of("approved", true, "feedback", "Confirm read"));
            assertNotNull(interactiveInput);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testHitlRailConcurrentToolsPartialRejectTwoRounds() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please read file a.txt and file b.txt simultaneously");
            inputs.put("conversation_id", "498");
            assertNotNull(inputs);

            InteractiveInput rejectB = rejectInterrupt("b_txt_id", "Reject reading b.txt");
            assertNotNull(rejectB);

            InteractiveInput confirmA = confirmInterrupt("a_txt_id");
            assertNotNull(confirmA);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testHitlRailConcurrentToolsOnePassOneInterrupt() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please read file a.txt and execute action operation simultaneously");
            inputs.put("conversation_id", "498");
            assertNotNull(inputs);

            Map<String, Object> result = new HashMap<>();
            result.put("result_type", "interrupt");
            result.put("interrupt_ids", List.of("read_id"));
            result.put("state", List.of(Map.of("payload", Map.of("tool_name", "read"))));
            assertInterruptResult(result, 1);
            assertEquals("read", getToolNameFromState(((List<?>) result.get("state")).get(0)));
        } finally {
            Runner.stop();
        }
    }
}
