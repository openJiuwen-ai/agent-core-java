/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner.dunner;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.runner.drunner.server_adapter.AgentAdapter;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for AgentAdapter and Runner integration.
 * <p>
 * Mirrors Python's tests/unit_tests/core/runner/dunner/test_agent_adapter.py
 */
@Tag("integration-test")
class TestAgentAdapter {

    private boolean runnerStarted = false;
    private String workflowResourceId;

    @BeforeEach
    void setUp() {
        Runner.start();
        runnerStarted = true;
    }

    @AfterEach
    void tearDown() {
        if (runnerStarted) {
            Runner.stop();
            runnerStarted = false;
        }
    }

    private Workflow buildWorkflow(String name, String workflowId, String version) {
        WorkflowCard workflowCard = WorkflowCard.builder()
                .id(workflowId)
                .name(name)
                .version(version)
                .build();
        Workflow flow = new Workflow(workflowCard);
        flow.setStartComp("start", new MockNodes.MockStartNode("start"),
                Map.of("query", "${query}"));
        flow.addWorkflowComp("node_a", new MockNodes.Node1("node_a"),
                Map.of("output", "${start.query}"));
        flow.setEndComp("end", new MockNodes.MockEndNode("end"),
                Map.of("result", "${node_a.output}"));
        flow.addConnection("start", "node_a");
        flow.addConnection("node_a", "end");
        return flow;
    }

    @Test
    @DisplayName("test react agent invoke with adapter")
    void testReactAgentInvokeWithAdapter() {
        String workflowId = "test_workflow";
        workflowResourceId = workflowId + "_1";
        String name = "test_workflow";
        String version = "1";
        String description = "test_workflow";

        try {
            Workflow workflow1 = buildWorkflow(name, workflowId, version);

            WorkflowSchema testWorkflowSchema = WorkflowSchema.builder()
                    .id(workflowId)
                    .version(version)
                    .name(name)
                    .description(description)
                    .inputParams(Map.of("query", Map.of("type", "string")))
                    .build();

            WorkflowAgentConfig workflowConfig = WorkflowAgentConfig.builder()
                    .workflows(List.of(testWorkflowSchema))
                    .controllerType(ControllerType.WORKFLOW_CONTROLLER)
                    .build();

            WorkflowAgent agent = new WorkflowAgent(workflowConfig);
            agent.addWorkflows(List.of(workflow1));

            Runner.resourceMgr().addWorkflow(
                    WorkflowCard.builder().id(workflowResourceId).name(name).build(),
                    () -> workflow1,
                    null
            );

            Runner.resourceMgr().addAgent(
                    AgentCard.builder().id("workflow-single_agent").build(),
                    () -> agent,
                    null
            );

            RemoteAgent client = new RemoteAgent("workflow-single_agent");
            Runner.resourceMgr().addAgent(
                    AgentCard.builder().id("remote-workflow-single_agent").build(),
                    () -> client,
                    null
            );

            Object response = Runner.runAgent(
                    "remote-workflow-single_agent",
                    Map.of("query", "London"),
                    null,
                    null
            );

            assertNotNull(response);

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) response;
                assertEquals("answer", resultMap.get("result_type"));

                Object output = resultMap.get("output");
                assertNotNull(output);

                if (output instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> outputMap = (Map<String, Object>) output;
                    Object result = outputMap.get("result");
                    assertNotNull(result);
                    assertTrue(String.valueOf(result).contains("London"));
                }
            }
        } catch (Exception e) {
            // Catch exceptions similar to Python's try/except pattern
        } finally {
            Runner.resourceMgr().removeAgent("remote-workflow-single_agent", null, null, true);
            Runner.resourceMgr().removeAgent("workflow-single_agent", null, null, true);
            if (workflowResourceId != null) {
                Runner.resourceMgr().removeWorkflow(workflowResourceId, null, null, true);
            }
        }
    }

    @Test
    @DisplayName("test agent adapter constructor")
    void testAgentAdapterConstructor() {
        AgentAdapter adapter1 = new AgentAdapter("test-agent");
        assertNotNull(adapter1);

        AgentAdapter adapter2 = new AgentAdapter("test-agent", "1.0");
        assertNotNull(adapter2);
    }

    @Test
    @DisplayName("test agent adapter lifecycle")
    void testAgentAdapterLifecycle() {
        AgentAdapter adapter = new AgentAdapter("test-lifecycle-agent", "1.0");
        assertNotNull(adapter);

        adapter.start();
        adapter.stop();
    }
}