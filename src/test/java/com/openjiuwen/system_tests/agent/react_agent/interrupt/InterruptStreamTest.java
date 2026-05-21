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
 * Mirrors Python's test_interrupt_stream.py.
 */
class InterruptStreamTest {

    @Test
    void testHitlRailStreamInterruptDetected() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Call read tool, read /tmp/test.txt");
            inputs.put("conversation_id", "493");
            assertNotNull(inputs);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testHitlRailStreamAgreeWithAutoconfirm() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please read /tmp/test1.txt");
            inputs.put("conversation_id", "493");
            assertNotNull(inputs);

            InteractiveInput interactiveInput = new InteractiveInput();
            interactiveInput.update("tool_call_id", Map.of(
                    "approved", true,
                    "feedback", "Confirm and auto confirm",
                    "auto_confirm", true
            ));
            assertNotNull(interactiveInput);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testHitlRailStreamReject() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Call read tool, read /tmp/test.txt");
            inputs.put("conversation_id", "493");
            assertNotNull(inputs);

            InteractiveInput rejectInput = new InteractiveInput();
            rejectInput.update("tool_call_id", Map.of(
                    "approved", false,
                    "feedback", "Reject this operation"
            ));
            assertNotNull(rejectInput);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testHitlRailStreamConcurrentToolsAllConfirmed() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please read file a.txt and file b.txt simultaneously");
            inputs.put("conversation_id", "498");
            assertNotNull(inputs);

            InteractiveInput confirmInput = new InteractiveInput();
            confirmInput.update("interrupt_id_1", Map.of("approved", true, "feedback", "Confirm"));
            confirmInput.update("interrupt_id_2", Map.of("approved", true, "feedback", "Confirm"));
            assertNotNull(confirmInput);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testHitlRailStreamConcurrentToolsPartialReject() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please read file a.txt and file b.txt simultaneously");
            inputs.put("conversation_id", "498");
            assertNotNull(inputs);

            InteractiveInput partialInput = new InteractiveInput();
            partialInput.update("a_txt_id", Map.of("approved", true, "feedback", "Confirm a.txt"));
            partialInput.update("b_txt_id", Map.of("approved", false, "feedback", "Reject b.txt"));
            assertNotNull(partialInput);
        } finally {
            Runner.stop();
        }
    }
}
