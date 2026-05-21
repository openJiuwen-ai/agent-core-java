/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowAgent basic invoke UT (mock-based).
 *
 * <p>Mirrors Python's {@code test_mock_workflow_agent_invoke.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.
 */
@DisplayName("WorkflowAgent Invoke")
class MockWorkflowAgentInvokeTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    private static Workflow buildSimpleWorkflow() {
        WorkflowCard card = WorkflowCard.builder()
                .id("test_invoke_workflow")
                .version("1.0")
                .name("invoke_test")
                .description("Simple workflow for invoke test")
                .build();
        Workflow flow = new Workflow(card);

        flow.setStartComp("start", new Start(),
                Map.of("query", "${query}"), null);
        flow.addWorkflowComp("node_a", new IdentityNode(),
                Map.of("output", "${start.query}"), null);
        flow.setEndComp("end", new PassThroughEndNode(),
                Map.of("result", "${node_a.output}"), null);

        flow.addConnection("start", "node_a");
        flow.addConnection("node_a", "end");
        return flow;
    }

    @Test
    @DisplayName("agent.invoke() completes a simple workflow end-to-end")
    void testInvokeDirect() {
        Workflow workflow = buildSimpleWorkflow();

        com.openjiuwen.core.application.schema.WorkflowAgentConfig config =
                com.openjiuwen.core.application.schema.WorkflowAgentConfig.builder()
                        .id("test_invoke_agent")
                        .version("1.0")
                        .description("invoke test agent")
                        .build();

        WorkflowAgent agent = new WorkflowAgent(config);
        agent.addWorkflows(java.util.List.of(workflow));

        String conversationId = UUID.randomUUID().toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) agent.invoke(Map.of(
                "query", "hello",
                "conversation_id", conversationId
        ));

        assertThat(result).isInstanceOf(Map.class);
        assertThat(result.get("result_type")).isEqualTo("answer");
    }

    private static class IdentityNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    private static class PassThroughEndNode extends End {
        public PassThroughEndNode() {
            super(Map.of("responseTemplate", "hello:{{end_input}}"));
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }
}
