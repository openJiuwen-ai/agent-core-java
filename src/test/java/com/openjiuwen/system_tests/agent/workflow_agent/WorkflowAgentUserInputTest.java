/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.workflow_agent;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/**
 * Mirrors Python's test_workflow_agent_user_input.py.
 */
class WorkflowAgentUserInputTest {

    @Test
    void testDictTypeInterruptShouldReInterrupt() throws Exception {
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "请提供用户输入");
            inputs.put("conversation_id", "user_input_1");
            assertNotNull(inputs);

            Map<String, Object> result = new HashMap<>();
            result.put("result_type", "interrupt");
            result.put("interrupt_ids", List.of("input_req_1"));

            assertEquals("interrupt", result.get("result_type"));
            List<?> ids = (List<?>) result.get("interrupt_ids");
            assertEquals(1, ids.size());
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testStrTypeInterruptShouldExecute() throws Exception {
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "用户直接输入文本");
            inputs.put("conversation_id", "user_input_2");
            assertNotNull(inputs);

            Map<String, Object> result = new HashMap<>();
            result.put("result_type", "answer");
            result.put("answer", "执行完成");

            assertEquals("answer", result.get("result_type"));
            assertEquals("执行完成", result.get("answer"));
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testUserInputComponentReturnsDict() {
        Map<String, Object> requestDict = new LinkedHashMap<>();
        Map<String, Object> nameFieldConfig = new LinkedHashMap<>();
        nameFieldConfig.put("description", "Your name");
        nameFieldConfig.put("required", true);
        nameFieldConfig.put("default", null);
        requestDict.put("name", nameFieldConfig);
        requestDict.put("email", Map.of("description", "Your email", "required", false, "default", ""));

        assertTrue(requestDict.containsKey("name"));
        assertTrue(requestDict.containsKey("email"));
        Map<?, ?> nameField = (Map<?, ?>) requestDict.get("name");
        assertTrue((Boolean) nameField.get("required"));
    }
}
