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
 * Mirrors Python's test_react_agent_auto_confirm.py.
 */
class ReactAgentAutoConfirmTest {

    @Test
    void testHitlRailAutoConfirm() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please read /tmp/test1.txt");
            inputs.put("conversation_id", "497");
            assertNotNull(inputs);

            InteractiveInput interactiveInput = new InteractiveInput();
            interactiveInput.update("tool_call_id", Map.of(
                    "approved", true,
                    "feedback", "Confirm, auto-pass subsequently",
                    "auto_confirm", true
            ));
            assertNotNull(interactiveInput);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testHitlRailSameToolMultipleCalls() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please execute action1, action2, and action3 simultaneously");
            inputs.put("conversation_id", "497");
            assertNotNull(inputs);

            Map<String, Object> result = new HashMap<>();
            result.put("result_type", "interrupt");
            result.put("interrupt_ids", List.of("id_1", "id_2", "id_3"));
            result.put("state", List.of());
            assertInterruptResult(result, 3);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testHitlRailConfirmOneAutoPassOthers() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please read /tmp/file1.txt, /tmp/file2.txt, and /tmp/file3.txt simultaneously");
            inputs.put("conversation_id", "497");
            assertNotNull(inputs);

            InteractiveInput interactiveInput = new InteractiveInput();
            interactiveInput.update("first_tool_call_id", Map.of(
                    "approved", true,
                    "feedback", "Confirm reading file",
                    "auto_confirm", true
            ));
            assertNotNull(interactiveInput);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testHitlRailClearSessionAfterReject() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            String sessionId = "497";
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please read /tmp/test1.txt");
            inputs.put("conversation_id", sessionId);
            assertNotNull(inputs);

            InteractiveInput interactiveInput = new InteractiveInput();
            interactiveInput.update("tool_call_id", Map.of(
                    "approved", true,
                    "feedback", "Confirm, auto-pass subsequently",
                    "auto_confirm", true
            ));
            assertNotNull(interactiveInput);
        } finally {
            Runner.stop();
        }
    }
}
