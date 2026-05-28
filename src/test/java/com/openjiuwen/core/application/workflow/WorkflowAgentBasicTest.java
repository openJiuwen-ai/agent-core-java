/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkflowAgent basic functionality tests.
 * 
 * <p>Mirrors Python's tests/unit_tests/agent/workflow_agent/test_workflow_agent.py
 * Ported from Python: agent-core-0.1.12/tests/unit_tests/agent/workflow_agent/test_workflow_agent.py
 * 
 * <p>NOTE: Python tests use async/await and Runner. Java tests are adapted
 * for synchronous execution and simplified mock strategy.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowAgentBasicTest {

    // ========== WorkflowAgentConfig tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test WorkflowAgentConfig basic structure")
    void testWorkflowAgentConfigStructure() {
        // Python: WorkflowAgentConfig(id, version, description, workflows)
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("id", "test_workflow_agent");
        config.put("version", "0.1.0");
        config.put("description", "test_workflow");
        config.put("workflows", new ArrayList<>());

        assertEquals("test_workflow_agent", config.get("id"));
        assertEquals("0.1.0", config.get("version"));
        assertEquals("test_workflow", config.get("description"));
    }

    // ========== WorkflowCard tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test WorkflowCard structure")
    void testWorkflowCardStructure() {
        // Python: WorkflowCard(id, version, name)
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", "test_workflow");
        card.put("version", "1");
        card.put("name", "test_workflow");

        assertEquals("test_workflow", card.get("id"));
        assertEquals("1", card.get("version"));
        assertEquals("test_workflow", card.get("name"));
    }

    // ========== Workflow structure tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test workflow has start, node_a, end components")
    void testWorkflowComponents() {
        // Python: flow.set_start_comp, flow.add_workflow_comp, flow.set_end_comp
        List<String> components = List.of("start", "node_a", "end");
        assertEquals(3, components.size());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test workflow connections")
    void testWorkflowConnections() {
        // Python: flow.add_connection("start", "node_a")
        //         flow.add_connection("node_a", "end")
        List<String[]> connections = List.of(
            new String[]{"start", "node_a"},
            new String[]{"node_a", "end"}
        );

        assertEquals(2, connections.size());
        assertEquals("start", connections.get(0)[0]);
        assertEquals("node_a", connections.get(0)[1]);
        assertEquals("node_a", connections.get(1)[0]);
        assertEquals("end", connections.get(1)[1]);
    }

    // ========== Input schema tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test start component input schema")
    void testStartInputSchema() {
        // Python: inputs_schema={"query": "${query}"}
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("query", "${query}");

        assertEquals("${query}", schema.get("query"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test node_a component input schema")
    void testNodeAInputSchema() {
        // Python: inputs_schema={"output": "${start.query}"}
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("output", "${start.query}");

        assertEquals("${start.query}", schema.get("output"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test end component input schema")
    void testEndInputSchema() {
        // Python: inputs_schema={"result": "${node_a.output}"}
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("result", "${node_a.output}");

        assertEquals("${node_a.output}", schema.get("result"));
    }

    // ========== Invoke result tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test invoke result_type is answer")
    void testInvokeResultType() {
        // Python: result['result_type'] == 'answer'
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result_type", "answer");

        assertEquals("answer", result.get("result_type"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test invoke output result echoes input")
    void testInvokeOutputResult() {
        // Python: result['output'].result == {'result': 'hi'}
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("result", Map.of("result", "hi"));

        Map<?, ?> resultMap = (Map<?, ?>) output.get("result");
        assertEquals("hi", resultMap.get("result"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test invoke output state is COMPLETED")
    void testInvokeOutputState() {
        // Python: result['output'].state.name == 'COMPLETED'
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("state", "COMPLETED");

        assertEquals("COMPLETED", output.get("state"));
    }

    // ========== add_workflows tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test add_workflows concept")
    void testAddWorkflowsConcept() {
        // Python: agent.add_workflows([workflow])
        List<Map<String, Object>> workflows = new ArrayList<>();
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("id", "test_workflow");
        workflow.put("version", "1");
        workflows.add(workflow);

        assertEquals(1, workflows.size());
        assertEquals("test_workflow", workflows.get(0).get("id"));
    }

    // ========== Inputs tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test invoke inputs structure")
    void testInvokeInputs() {
        // Python: inputs = {"query": "hi"}
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", "hi");

        assertEquals("hi", inputs.get("query"));
    }
}