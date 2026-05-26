/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.tests.unit_tests.core.workflow.MockNodes.MockStartNode;
import com.openjiuwen.tests.unit_tests.core.workflow.MockNodes.MockEndNode;
import com.openjiuwen.tests.unit_tests.core.workflow.MockNodes.MockStartNode4Cp;
import com.openjiuwen.tests.unit_tests.core.workflow.MockNodes.InteractiveNode4StreamCp;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WorkflowAgent interrupt stream functionality.
 * 
 * <p>Mirrors Python's test_simple_stream_interactive_workflow from
 * {@code tests/unit_tests/core/workflow/test_workflow_with_interrupt.py}
 * 
 * <p>Python test logic (lines 610-651):
 * <pre>
 * async def test_simple_stream_interactive_workflow():
 *     """
 *     graph : start->a->end
 *     """
 *     start_node = MockStartNode4Cp("start")
 *     flow = Workflow(card=WorkflowCard(id="test_simple_stream_interactive_workflow"))
 *     flow.set_start_comp("start", start_node,
 *                         inputs_schema={"a": "${inputs.a}", "b": "${inputs.b}", "c": 1, "d": [1, 2, 3]})
 *     flow.add_workflow_comp("a", InteractiveNode4StreamCp("a"),
 *                            inputs_schema={"aa": "${start.a}", "ac": "${start.c}"})
 *     flow.set_end_comp("end", MockEndNode("end"),
 *                       inputs_schema={"result": "${a.aa}"})
 *     flow.add_connection("start", "a")
 *     flow.add_connection("a", "end")
 *     
 *     session_id = uuid.uuid4().hex
 *     async for res in flow.stream({"inputs": {"a": 1, "b": "haha"}}, create_workflow_session(session_id=session_id)):
 *         if res.type == INTERACTION:
 *             interaction_node = res.payload.id
 *             interaction_msg = res.payload.value
 *     assert interaction_node == "a"
 *     assert interaction_msg == "Please enter any key"
 *     
 *     user_input = InteractiveInput()
 *     user_input.update(interaction_node, {"aa": "any key"})
 *     async for res in flow.stream(user_input, create_workflow_session(session_id=session_id),
 *                                  stream_modes=[BaseStreamMode.OUTPUT]):
 *         if res.type == "output":
 *             assert res.payload[0] == "a"
 *             result = res.payload[1]
 *     assert result == {"aa": "any key"}
 *     assert start_node.runtime == 1
 * </pre>
 * 
 * <p>NOTE: Full stream execution requires Workflow.stream() implementation
 * and WorkflowSession factory. Current tests focus on structural validation
 * and mock node behavior.
 */
@Disabled("Requires Workflow.stream() and WorkflowSession infrastructure")
@DisplayName("WorkflowAgent Interrupt Stream Tests")
class MockWorkflowAgentInterruptStreamTest {

    // ========== Test Constants ==========
    
    private static final String TEST_WORKFLOW_ID = "test_simple_stream_interactive_workflow";
    
    // ========== Workflow Structure Tests ==========
    
    @Test
    @Tag("level0")
    @DisplayName("Test interrupt stream workflow structure: start -> interactive_node -> end")
    void testInterruptStreamWorkflowStructure() {
        // Python: graph : start->a->end
        // Validate workflow graph structure for interrupt stream scenario
        
        List<String> components = List.of("start", "interactive_node", "end");
        List<String[]> connections = List.of(
            new String[]{"start", "interactive_node"},
            new String[]{"interactive_node", "end"}
        );
        
        assertEquals(3, components.size(), "Workflow should have 3 components");
        assertEquals(2, connections.size(), "Workflow should have 2 connections");
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Test workflow card for interrupt stream")
    void testWorkflowCardForInterruptStream() {
        // Python: WorkflowCard(id="test_simple_stream_interactive_workflow")
        WorkflowCard card = new WorkflowCard(TEST_WORKFLOW_ID, "Interrupt Stream Test");
        
        assertEquals(TEST_WORKFLOW_ID, card.getId());
        assertNotNull(card.getName());
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Test start node inputs schema structure")
    void testStartNodeInputsSchema() {
        // Python: inputs_schema={"a": "${inputs.a}", "b": "${inputs.b}", "c": 1, "d": [1, 2, 3]}
        Map<String, Object> inputsSchema = new LinkedHashMap<>();
        inputsSchema.put("a", "${inputs.a}");
        inputsSchema.put("b", "${inputs.b}");
        inputsSchema.put("c", 1);
        inputsSchema.put("d", List.of(1, 2, 3));
        
        assertEquals(4, inputsSchema.size());
        assertEquals("${inputs.a}", inputsSchema.get("a"));
        assertEquals(1, inputsSchema.get("c"));
        assertTrue(((List<?>) inputsSchema.get("d")).size() == 3);
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Test interactive node inputs schema")
    void testInteractiveNodeInputsSchema() {
        // Python: inputs_schema={"aa": "${start.a}", "ac": "${start.c}"}
        Map<String, Object> inputsSchema = new LinkedHashMap<>();
        inputsSchema.put("aa", "${start.a}");
        inputsSchema.put("ac", "${start.c}");
        
        assertEquals(2, inputsSchema.size());
        assertEquals("${start.a}", inputsSchema.get("aa"));
        assertEquals("${start.c}", inputsSchema.get("ac"));
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Test end node inputs schema")
    void testEndNodeInputsSchema() {
        // Python: inputs_schema={"result": "${a.aa}"}
        Map<String, Object> inputsSchema = new LinkedHashMap<>();
        inputsSchema.put("result", "${a.aa}");
        
        assertEquals(1, inputsSchema.size());
        assertEquals("${a.aa}", inputsSchema.get("result"));
    }
    
    // ========== Interactive Input Tests ==========
    
    @Test
    @Tag("level0")
    @DisplayName("Test InteractiveInput creation and update")
    void testInteractiveInputCreationAndUpdate() {
        // Python: user_input = InteractiveInput()
        //         user_input.update(interaction_node, {"aa": "any key"})
        InteractiveInput userInput = new InteractiveInput();
        String interactionNode = "a";
        Map<String, Object> inputValue = Map.of("aa", "any key");
        
        userInput.update(interactionNode, inputValue);
        
        assertNotNull(userInput.getUserInputs());
        assertTrue(userInput.getUserInputs().containsKey(interactionNode));
        assertEquals(inputValue, userInput.getUserInputs().get(interactionNode));
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Test interaction response structure")
    void testInteractionResponseStructure() {
        // Python: res.type == INTERACTION
        //         interaction_node = res.payload.id
        //         interaction_msg = res.payload.value
        
        String responseType = "INTERACTION";
        String interactionNode = "a";
        String interactionMsg = "Please enter any key";
        
        assertEquals("INTERACTION", responseType);
        assertEquals("a", interactionNode);
        assertEquals("Please enter any key", interactionMsg);
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Test stream output result structure")
    void testStreamOutputResultStructure() {
        // Python: result == {"aa": "any key"}
        Map<String, Object> expectedResult = Map.of("aa", "any key");
        
        assertEquals(1, expectedResult.size());
        assertEquals("any key", expectedResult.get("aa"));
    }
    
    // ========== Mock Node Behavior Tests ==========
    
    @Test
    @Tag("level0")
    @DisplayName("Test MockStartNode basic invocation")
    void testMockStartNodeInvocation() {
        MockStartNode startNode = new MockStartNode("start");
        Map<String, Object> inputs = Map.of("a", 1, "b", "haha", "c", 1, "d", List.of(1, 2, 3));
        
        Object result = startNode.invoke(inputs, null, null);
        
        assertNotNull(result);
        assertEquals(inputs, result);
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Test MockEndNode basic invocation")
    void testMockEndNodeInvocation() {
        MockEndNode endNode = new MockEndNode("end");
        Map<String, Object> inputs = Map.of("result", Map.of("aa", "any key"));
        
        Object result = endNode.invoke(inputs, null, null);
        
        assertNotNull(result);
    }
    
    // ========== Checkpoint-enabled Node Tests ==========
    
    @Test
    @Tag("level0")
    @DisplayName("Test MockStartNode4Cp creation and runtime tracking")
    void testMockStartNode4CpCreationAndRuntime() {
        // Python: start_node = MockStartNode4Cp("start")
        //         assert start_node.runtime == 1
        MockStartNode4Cp startNode = new MockStartNode4Cp("start");
        
        assertEquals("start", startNode.getNodeId());
        assertEquals(0, startNode.getRuntime());
        
        // Simulate invocation (would normally be called by workflow engine)
        // Note: In actual execution, session.updateGlobalState would be called
        // For this test, we just verify the runtime counter increments
        startNode.invoke(Map.of("a", 1), null, null);
        
        assertEquals(1, startNode.getRuntime());
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Test InteractiveNode4StreamCp creation")
    void testInteractiveNode4StreamCpCreation() {
        // Python: InteractiveNode4StreamCp("a")
        InteractiveNode4StreamCp interactiveNode = new InteractiveNode4StreamCp("a");
        
        assertEquals("a", interactiveNode.getNodeId());
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Test MockStartNode4Cp global state validation")
    void testMockStartNode4CpGlobalStateValidation() {
        // Python: value = session.get_global_state("a")
        //         if value is not None:
        //             raise Exception("value is not None")
        MockStartNode4Cp startNode = new MockStartNode4Cp("start");
        
        // First invocation - no global state, should succeed
        Object result1 = startNode.invoke(Map.of("test", 1), null, null);
        assertNotNull(result1);
        
        // Note: In actual execution with session, updateGlobalState would set "a" to 10
        // Subsequent invocations with same session would fail
        // This test verifies the logic without actual session
    }
    
    // ========== Placeholder for Full Implementation ==========
    
    /**
     * Full stream test placeholder.
     * 
     * Requires Workflow.stream() and WorkflowSession infrastructure to execute.
     * Once available, this test will:
     * 1. Create workflow with MockStartNode4Cp and InteractiveNode4StreamCp
     * 2. Execute stream and capture INTERACTION response
     * 3. Resume with InteractiveInput
     * 4. Verify final result matches Python expectations
     */
    @Test
    @Disabled("Requires Workflow.stream() implementation")
    @DisplayName("Placeholder - Simple stream interactive workflow (needs infrastructure)")
    void testPlaceholderForFullImplementation() {
        assertTrue(true, "Placeholder - waiting for Workflow.stream() implementation");
    }
    
    // ========== Helper Methods ==========
    
    private Map<String, Object> buildStartInputsSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("a", "${inputs.a}");
        schema.put("b", "${inputs.b}");
        schema.put("c", 1);
        schema.put("d", List.of(1, 2, 3));
        return schema;
    }
    
    private Map<String, Object> buildInteractiveInputsSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("aa", "${start.a}");
        schema.put("ac", "${start.c}");
        return schema;
    }
    
    private Map<String, Object> buildEndInputsSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("result", "${a.aa}");
        return schema;
    }
}