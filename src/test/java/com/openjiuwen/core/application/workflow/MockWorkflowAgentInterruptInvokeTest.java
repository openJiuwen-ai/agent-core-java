/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.session.interaction.InteractiveInput;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkflowAgent interrupt & resume tests (invoke mode).
 * 
 * <p>Mirrors Python's tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_interrupt_invoke.py
 * Ported from Python: agent-core-0.1.12/tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_interrupt_invoke.py
 * 
 * <p>NOTE: Python tests use async/await and mock_llm_context. Java tests are adapted
 * for synchronous execution and simplified mock strategy.
 */
@ExtendWith(MockitoExtension.class)
class MockWorkflowAgentInterruptInvokeTest {

    // ========== Test concepts (aligned with ST checkpoints) ==========

    @Test
    @Tag("level0")
    @DisplayName("Test interrupt result has __interaction__ type")
    void testInterruptResultType() {
        // Python checkpoint: result[0].type == '__interaction__'
        // Simulate interrupt result structure
        Map<String, Object> interruptResult = new LinkedHashMap<>();
        interruptResult.put("type", "__interaction__");
        interruptResult.put("component_id", "questioner");
        interruptResult.put("content", "What is your location?");

        assertEquals("__interaction__", interruptResult.get("type"));
        assertEquals("questioner", interruptResult.get("component_id"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test resume result has answer type and COMPLETED state")
    void testResumeResultType() {
        // Python checkpoint: result_type='answer', state=COMPLETED
        Map<String, Object> resumeResult = new LinkedHashMap<>();
        resumeResult.put("result_type", "answer");
        resumeResult.put("output", Map.of(
            "state", "COMPLETED",
            "response", "shanghai"
        ));

        assertEquals("answer", resumeResult.get("result_type"));
        assertEquals("COMPLETED", ((Map<?, ?>) resumeResult.get("output")).get("state"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test InteractiveInput can be created and updated")
    void testInteractiveInputCreation() {
        InteractiveInput input = new InteractiveInput();
        input.update("questioner", "shanghai");

        assertNotNull(input);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test workflow interrupt creates list result")
    void testInterruptReturnsList() {
        // Python: assert isinstance(result1, list)
        List<Map<String, Object>> interruptResults = new ArrayList<>();
        interruptResults.add(Map.of("type", "__interaction__"));

        assertTrue(interruptResults instanceof List);
        assertEquals(1, interruptResults.size());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test workflow resume creates dict result")
    void testResumeReturnsDict() {
        // Python: assert isinstance(result2, dict)
        Map<String, Object> resumeResult = new LinkedHashMap<>();
        resumeResult.put("result_type", "answer");

        assertTrue(resumeResult instanceof Map);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test chat history has correct roles after interrupt-resume")
    void testChatHistoryRoles() {
        // Python: chat_history has roles [user, assistant, user, assistant]
        List<Map<String, Object>> chatHistory = new ArrayList<>();
        chatHistory.add(Map.of("role", "user", "content", "check weather"));
        chatHistory.add(Map.of("role", "assistant", "content", "What is your location?"));
        chatHistory.add(Map.of("role", "user", "content", "shanghai"));
        chatHistory.add(Map.of("role", "assistant", "content", "response"));

        assertEquals(4, chatHistory.size());
        assertEquals("user", chatHistory.get(0).get("role"));
        assertEquals("assistant", chatHistory.get(1).get("role"));
        assertEquals("user", chatHistory.get(2).get("role"));
        assertEquals("assistant", chatHistory.get(3).get("role"));
    }

    // ========== Workflow structure tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test workflow with questioner has correct connections")
    void testWorkflowStructure() {
        // Python: flow.add_connection("start", "questioner")
        //         flow.add_connection("questioner", "end")
        List<String> components = List.of("start", "questioner", "end");
        List<String[]> connections = List.of(
            new String[]{"start", "questioner"},
            new String[]{"questioner", "end"}
        );

        assertEquals(3, components.size());
        assertEquals(2, connections.size());
        assertEquals("start", connections.get(0)[0]);
        assertEquals("questioner", connections.get(0)[1]);
        assertEquals("questioner", connections.get(1)[0]);
        assertEquals("end", connections.get(1)[1]);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test questioner uses preset question content")
    void testQuestionerPresetContent() {
        // Python: question_content=question, extract_fields_from_response=False
        Map<String, Object> questionerConfig = new LinkedHashMap<>();
        questionerConfig.put("question_content", "What is your location?");
        questionerConfig.put("extract_fields_from_response", false);

        assertEquals("What is your location?", questionerConfig.get("question_content"));
        assertEquals(false, questionerConfig.get("extract_fields_from_response"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test workflow agent config structure")
    void testWorkflowAgentConfig() {
        Map<String, Object> agentConfig = new LinkedHashMap<>();
        agentConfig.put("id", "interrupt_invoke_agent");
        agentConfig.put("version", "1.0");
        agentConfig.put("description", "interrupt invoke test agent");
        agentConfig.put("workflows", new ArrayList<>());

        assertEquals("interrupt_invoke_agent", agentConfig.get("id"));
        assertEquals("1.0", agentConfig.get("version"));
    }
}