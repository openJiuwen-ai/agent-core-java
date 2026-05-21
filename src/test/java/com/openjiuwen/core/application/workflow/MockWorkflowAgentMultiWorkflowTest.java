/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowAgent multi-workflow UT.
 *
 * <p>Mirrors Python's {@code test_mock_workflow_agent_multi_workflow.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.
 */
@DisplayName("WorkflowAgent Multi Workflow")
class MockWorkflowAgentMultiWorkflowTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("invoke with single workflow returns answer")
    void testInvokeSingleWorkflow() {
        Workflow workflow = WorkflowTestHelper.buildSimpleWorkflow("test_multi_wf_1", "multi_wf_1");

        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("test_multi_wf_agent")
                .version("1.0")
                .description("multi workflow test agent")
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

    @Test
    @DisplayName("invoke with multiple workflows returns answer")
    void testInvokeMultipleWorkflows() {
        Workflow workflow1 = WorkflowTestHelper.buildSimpleWorkflow("test_multi_wf_1", "multi_wf_1");
        Workflow workflow2 = WorkflowTestHelper.buildSimpleWorkflow("test_multi_wf_2", "multi_wf_2");

        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("test_multi_wf_agent_2")
                .version("1.0")
                .description("multi workflow test agent 2")
                .build();
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.addWorkflows(List.of(workflow1, workflow2));

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
