/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent.llm_agent;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.tests.unit_tests.core.workflow.MockNodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Workflow agent basic tests.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent.workflow_agent.test_workflow_agent}.
 */
@DisplayName("TestWorkflowAgent")
class TestWorkflowAgent {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @Tag("level0")
    @DisplayName("invoke single workflow")
    void testInvokeSingle() {
        Workflow workflow = buildWorkflow("test_workflow", "test_workflow", "1");
        WorkflowAgentConfig workflowConfig = WorkflowAgentConfig.builder()
            .id("test_workflow_agent")
            .version("0.1.0")
            .description("test_workflow")
            .workflows(List.of())
            .build();
        WorkflowAgent agent = new WorkflowAgent(workflowConfig);
        agent.addWorkflows(List.of(workflow));

        ControllerOutput result = agent.invoke(Map.of("query", "hi"), null);
        Map<String, Object> data = result.getDataAsMap();
        WorkflowOutput output = (WorkflowOutput) data.get("output");

        assertEquals("answer", data.get("result_type"));
        assertEquals(Map.of("result", "hi"), output.getResult());
        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
    }

    private static Workflow buildWorkflow(String name, String workflowId, String version) {
        WorkflowCard workflowCard = WorkflowCard.builder()
            .id(workflowId)
            .version(version)
            .name(name)
            .build();
        Workflow flow = new Workflow(workflowCard);
        flow.setStartComp(
            "start",
            new MockNodes.MockStartNode("start"),
            Map.of("query", "${query}")
        );
        flow.addWorkflowComp(
            "node_a",
            new MockNodes.Node1("node_a"),
            Map.of("output", "${start.query}")
        );
        flow.setEndComp(
            "end",
            new MockNodes.MockEndNode("end"),
            Map.of("result", "${node_a.output}")
        );
        flow.addConnection("start", "node_a");
        flow.addConnection("node_a", "end");
        return flow;
    }
}
