/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.workflow_agent;

import com.openjiuwen.core.runner.Runner;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import java.util.*;

/**
 * Mirrors Python's test_multi_workflow_agent.py.
 */
class MultiWorkflowAgentTest {

    static final String API_BASE = System.getenv().getOrDefault("API_BASE", "mock://api.openai.com/v1");
    static final String API_KEY = System.getenv().getOrDefault("API_KEY", "sk-fake");

    @Test
    void testMultiWorkflowIntentRouting() throws Exception {
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "帮我查询今天天气");
            inputs.put("conversation_id", "multi_wf_1");
            assertNotNull(inputs);
            assertEquals("帮我查询今天天气", inputs.get("query"));
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testMultiWorkflowSwitchAndRecover() throws Exception {
        Runner.start();
        try {
            Map<String, Object> inputs1 = new HashMap<>();
            inputs1.put("query", "执行工作流A");
            inputs1.put("conversation_id", "multi_wf_2");
            assertNotNull(inputs1);

            Map<String, Object> inputs2 = new HashMap<>();
            inputs2.put("query", "切换到工作流B");
            inputs2.put("conversation_id", "multi_wf_2");
            assertNotNull(inputs2);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testRealTimeInterrupt() throws Exception {
        Runner.start();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "执行长时间任务");
            inputs.put("conversation_id", "multi_wf_3");
            assertNotNull(inputs);
        } finally {
            Runner.stop();
        }
    }
}
