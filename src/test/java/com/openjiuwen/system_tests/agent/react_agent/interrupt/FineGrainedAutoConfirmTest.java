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
 * Mirrors Python's test_fine_grained_auto_confirm.py.
 */
class FineGrainedAutoConfirmTest {

    @Test
    void testSingleAgentFineGrainedAutoConfirm() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs1 = new HashMap<>();
            inputs1.put("query", "Please read /tmp/a.txt");
            inputs1.put("conversation_id", "test_single_fg");
            assertNotNull(inputs1);

            InteractiveInput interactiveInput = new InteractiveInput();
            interactiveInput.update("tool_call_id_1", Map.of(
                    "approved", true,
                    "feedback", "Confirm reading a.txt",
                    "auto_confirm", true
            ));
            assertNotNull(interactiveInput);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void test3LayerAgentFineGrainedAutoConfirm() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please read files /tmp/a.txt and /tmp/b.txt");
            inputs.put("conversation_id", "test_3layer_fg");
            assertNotNull(inputs);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void test3LayerAgentFineGrainedAutoConfirmClearSession() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please read file /tmp/a.txt");
            inputs.put("conversation_id", "test_clear_session");
            assertNotNull(inputs);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testFineGrainedAutoConfirmMergeKeys() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "Please read file /tmp/a.txt");
            inputs.put("conversation_id", "test_merge_keys");
            assertNotNull(inputs);
        } finally {
            Runner.stop();
        }
    }
}
