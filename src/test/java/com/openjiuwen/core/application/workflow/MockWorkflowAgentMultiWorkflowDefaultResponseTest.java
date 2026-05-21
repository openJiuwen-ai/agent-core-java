/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.workflow.Workflow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowAgent multi-workflow default response UT.
 *
 * <p>Mirrors Python's {@code test_mock_workflow_agent_multi_workflow_default_response.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.
 */
@DisplayName("WorkflowAgent Multi Workflow Default Response")
class MockWorkflowAgentMultiWorkflowDefaultResponseTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("invoke with default response configuration")
    void testInvokeWithDefaultResponse() {
        Workflow workflow = WorkflowTestHelper.buildSimpleWorkflow("test_default_resp", "default_resp_test");

        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("test_default_resp_agent")
                .version("1.0")
                .description("default response test agent")
                .build();
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.addWorkflows(List.of(workflow));

        String conversationId = UUID.randomUUID().toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) agent.invoke(Map.of(
                "query", "hello",
                "conversation_id", conversationId
        ));

        assertThat(result).isInstanceOf(Map.class);
        assertThat(result.get("result_type")).isEqualTo("answer");
    }
}
