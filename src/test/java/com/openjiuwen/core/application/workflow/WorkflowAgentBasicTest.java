/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
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
 * Tests for WorkflowAgent basic functionality.
 *
 * <p>Mirrors Python's {@code test_workflow_agent.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.
 */
@DisplayName("WorkflowAgent Basic")
class WorkflowAgentTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    private static Workflow buildWorkflow(String name, String workflowId, String version) {
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .version(version)
                .name(name)
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
    @DisplayName("invoke single workflow via agent.invoke()")
    void testInvokeSingle() {
        String workflowId = "test_workflow";
        Workflow workflow = buildWorkflow("test_workflow", workflowId, "1");

        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("test_workflow_agent")
                .version("0.1.0")
                .description("test_workflow")
                .build();

        WorkflowAgent agent = new WorkflowAgent(config);
        agent.addWorkflows(java.util.List.of(workflow));

        Map<String, Object> inputs = Map.of(
                "query", "hi",
                "conversation_id", UUID.randomUUID().toString()
        );

        Map<String, Object> result = agent.invoke(inputs);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(result.get("result_type")).isEqualTo("answer");

        Object output = result.get("output");
        assertThat(output).isNotNull();
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
