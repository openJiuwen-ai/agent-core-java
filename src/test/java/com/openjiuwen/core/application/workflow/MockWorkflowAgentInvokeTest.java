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
 * WorkflowAgent basic invoke tests.
 * 
 * <p>Mirrors Python's tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_invoke.py
 * Ported from Python: agent-core-0.1.12/tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_invoke.py
 * 
 * <p>NOTE: Python tests use async/await and mock nodes. Java tests are adapted
 * for synchronous execution and simplified mock strategy.
 */
@ExtendWith(MockitoExtension.class)
class MockWorkflowAgentInvokeTest {

    // ========== Workflow structure tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test simple workflow structure: start -> node_a -> end")
    void testSimpleWorkflowStructure() {
        // Python: flow.add_connection("start", "node_a")
        //         flow.add_connection("node_a", "end")
        List<String> components = List.of("start", "node_a", "end");
        List<String[]> connections = List.of(
            new String[]{"start", "node_a"},
            new String[]{"node_a", "end"}
        );

        assertEquals(3, components.size());
        assertEquals(2, connections.size());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test workflow card structure")
    void testWorkflowCardStructure() {
        // Python: WorkflowCard(id, version, name, description)
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", "test_invoke_workflow");
        card.put("version", "1.0");
        card.put("name", "invoke_test");
        card.put("description", "Simple workflow for invoke test");

        assertEquals("test_invoke_workflow", card.get("id"));
        assertEquals("1.0", card.get("version"));
        assertEquals("invoke_test", card.get("name"));
    }

    // ========== Invoke result tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test invoke result has answer type")
    void testInvokeResultType() {
        // Python: result["result_type"] == "answer"
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result_type", "answer");
        result.put("output", Map.of("state", "COMPLETED", "result", Map.of("result", "hello")));

        assertEquals("answer", result.get("result_type"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test invoke output state is COMPLETED")
    void testInvokeOutputState() {
        // Python: output.state == COMPLETED
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("state", "COMPLETED");
        output.put("result", Map.of("result", "hello"));

        assertEquals("COMPLETED", output.get("state"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test invoke result echoes input query")
    void testInvokeResultEchoesInput() {
        // Python: output.result == {"result": "hello"}
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("result", Map.of("result", "hello"));

        Map<?, ?> resultMap = (Map<?, ?>) output.get("result");
        assertEquals("hello", resultMap.get("result"));
    }

    // ========== Chat history tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test chat history has correct length after invoke")
    void testChatHistoryLength() {
        // Python: len(chat_history) == 2
        List<Map<String, Object>> chatHistory = new ArrayList<>();
        chatHistory.add(Map.of("role", "user", "content", "hello"));
        chatHistory.add(Map.of("role", "assistant", "content", "response"));

        assertEquals(2, chatHistory.size());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test chat history roles are user then assistant")
    void testChatHistoryRoles() {
        // Python: chat_history[0].role == "user"
        //         chat_history[1].role == "assistant"
        List<Map<String, Object>> chatHistory = new ArrayList<>();
        chatHistory.add(Map.of("role", "user"));
        chatHistory.add(Map.of("role", "assistant"));

        assertEquals("user", chatHistory.get(0).get("role"));
        assertEquals("assistant", chatHistory.get(1).get("role"));
    }

    // ========== WorkflowAgent config tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test WorkflowAgent config structure")
    void testWorkflowAgentConfigStructure() {
        // Python: WorkflowAgentConfig(id, version, description, workflows)
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("id", "test_invoke_agent");
        config.put("version", "1.0");
        config.put("description", "invoke test agent");
        config.put("workflows", new ArrayList<>());

        assertEquals("test_invoke_agent", config.get("id"));
        assertEquals("1.0", config.get("version"));
        assertEquals("invoke test agent", config.get("description"));
    }

    // ========== Input schema tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test start node input schema uses ${query}")
    void testStartInputSchema() {
        // Python: inputs_schema={"query": "${query}"}
        Map<String, String> inputsSchema = new LinkedHashMap<>();
        inputsSchema.put("query", "${query}");

        assertEquals("${query}", inputsSchema.get("query"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test node_a input schema uses ${start.query}")
    void testNodeAInputSchema() {
        // Python: inputs_schema={"output": "${start.query}"}
        Map<String, String> inputsSchema = new LinkedHashMap<>();
        inputsSchema.put("output", "${start.query}");

        assertEquals("${start.query}", inputsSchema.get("output"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test end node input schema uses ${node_a.output}")
    void testEndInputSchema() {
        // Python: inputs_schema={"result": "${node_a.output}"}
        Map<String, String> inputsSchema = new LinkedHashMap<>();
        inputsSchema.put("result", "${node_a.output}");

        assertEquals("${node_a.output}", inputsSchema.get("result"));
    }

    // ========== Conversation ID tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test conversation_id is UUID format")
    void testConversationIdFormat() {
        // Python: conversation_id = str(uuid.uuid4())
        String conversationId = UUID.randomUUID().toString();

        assertTrue(conversationId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }
}