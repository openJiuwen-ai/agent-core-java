/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowAgent interrupt and resume UT (invoke mode).
 *
 * <p>Mirrors Python's {@code test_mock_workflow_agent_interrupt_invoke.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.
 */
@DisplayName("WorkflowAgent Interrupt Invoke")
class MockWorkflowAgentInterruptInvokeTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("invoke direct with simple workflow returns answer")
    void testInvokeDirectSimpleWorkflow() {
        Workflow workflow = WorkflowTestHelper.buildSimpleWorkflow("test_interrupt_invoke", "interrupt_invoke_test");

        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("test_interrupt_agent")
                .version("1.0")
                .description("interrupt test agent")
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
    @DisplayName("invoke via runner with simple workflow returns answer")
    void testInvokeViaRunnerSimpleWorkflow() {
        Workflow workflow = WorkflowTestHelper.buildSimpleWorkflow("test_interrupt_invoke", "interrupt_invoke_test");

        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("test_interrupt_runner_agent")
                .version("1.0")
                .description("interrupt test agent (runner)")
                .build();
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.addWorkflows(List.of(workflow));

        String conversationId = UUID.randomUUID().toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) Runner.runAgent(agent, Map.of(
                "query", "hello",
                "conversation_id", conversationId
        ));

        assertThat(result).isInstanceOf(Map.class);
        assertThat(result.get("result_type")).isEqualTo("answer");
    }
}
