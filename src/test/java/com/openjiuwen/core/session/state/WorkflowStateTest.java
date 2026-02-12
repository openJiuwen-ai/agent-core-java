/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WorkflowStateCollection, CommitState, and InMemoryWorkflowState.
 * 
 * <p>Converted from Python: test_workflow_state.py</p>
 */
class WorkflowStateTest {
    
    @Nested
    @DisplayName("WorkflowStateCollection Tests")
    class WorkflowStateCollectionTests {
        
        private CommitState stateCollection;
        
        @BeforeEach
        void setUp() {
            InMemoryCommitState ioState = new InMemoryCommitState();
            InMemoryCommitState globalState = new InMemoryCommitState();
            InMemoryCommitState compState = new InMemoryCommitState();
            InMemoryCommitState workflowState = new InMemoryCommitState();
            stateCollection = new CommitState(
                ioState, globalState, compState, workflowState,
                new HashMap<>(), "parent1", "node1"
            );
        }
        
        @Test
        @DisplayName("get global from global state")
        void testGetGlobalFromGlobalState() {
            stateCollection.globalState.updateById("node1", Map.of("key", "global_value"));
            stateCollection.globalState.commit();
            Object result = stateCollection.getGlobal("key");
            assertEquals("global_value", result);
        }
        
        @Test
        @DisplayName("get global fallback to io state parent")
        void testGetGlobalFallbackToIoStateParent() {
            stateCollection.ioState.updateById("parent1", Map.of("parent1", Map.of("key", "parent_value")));
            stateCollection.ioState.commit();
            Object result = stateCollection.getGlobal("key");
            assertEquals("parent_value", result);
        }
        
        @Test
        @DisplayName("get global fallback to io state node")
        void testGetGlobalFallbackToIoStateNode() {
            stateCollection.ioState.updateById("node1", Map.of("node1", Map.of("key", "node_value")));
            stateCollection.ioState.commit();
            Object result = stateCollection.getGlobal("key");
            assertEquals("node_value", result);
        }
        
        @Test
        @DisplayName("get global priority order")
        void testGetGlobalPriorityOrder() {
            stateCollection.globalState.updateById("node1", Map.of("key", "global_value"));
            stateCollection.globalState.commit();
            stateCollection.ioState.updateById("parent1", Map.of("parent1", Map.of("key", "parent_value")));
            stateCollection.ioState.commit();
            stateCollection.ioState.updateById("node1", Map.of("node1", Map.of("key", "node_value")));
            stateCollection.ioState.commit();
            // Global should take priority
            Object result = stateCollection.getGlobal("key");
            assertEquals("global_value", result);
        }
        
        @Test
        @DisplayName("get global returns null when not found")
        void testGetGlobalReturnsNullWhenNotFound() {
            Object result = stateCollection.getGlobal("nonexistent");
            assertNull(result);
        }
        
        @Test
        @DisplayName("get global with null key returns null")
        void testGetGlobalWithNullKeyReturnsNull() {
            Object result = stateCollection.getGlobal(null);
            assertNull(result);
        }
        
        @Test
        @DisplayName("update global")
        void testUpdateGlobal() {
            stateCollection.updateGlobal(Map.of("key", "value"));
            stateCollection.globalState.commit();
            Object result = stateCollection.getGlobal("key");
            assertEquals("value", result);
        }
        
        @Test
        @DisplayName("update global with null data")
        void testUpdateGlobalWithNullData() {
            stateCollection.updateGlobal(null); // Should not raise
        }
        
        @Test
        @DisplayName("update component state")
        void testUpdateComponentState() {
            stateCollection.update(Map.of("comp_key", "comp_value"));
            stateCollection.compState.commit();
            Object result = stateCollection.get("comp_key");
            assertEquals("comp_value", result);
        }
        
        @Test
        @DisplayName("get component state")
        void testGetComponentState() {
            stateCollection.compState.updateById("node1", Map.of("node1", Map.of("comp_key", "comp_value")));
            stateCollection.compState.commit();
            Object result = stateCollection.get("comp_key");
            assertEquals("comp_value", result);
        }
        
        @Test
        @DisplayName("get component state all")
        void testGetComponentStateAll() {
            stateCollection.compState.updateById("node1", Map.of("node1", Map.of("a", 1, "b", 2)));
            stateCollection.compState.commit();
            Object result = stateCollection.get(null);
            assertEquals(Map.of("a", 1, "b", 2), result);
        }
        
        @Test
        @DisplayName("update trace")
        void testUpdateTrace() {
            Map<String, Object> mockSpan = Map.of("trace_id", "123", "name", "test_span");
            stateCollection.updateTrace(mockSpan);
            assertEquals(mockSpan, stateCollection.traceState.get("node1"));
        }
    }
    
    @Nested
    @DisplayName("CommitState Tests")
    class CommitStateTests {
        
        private InMemoryWorkflowState commitState;
        
        @BeforeEach
        void setUp() {
            commitState = new InMemoryWorkflowState();
        }
        
        @Test
        @DisplayName("get workflow state")
        void testGetWorkflowState() {
            commitState.workflowState.updateById("workflow", Map.of("wf_key", "wf_value"));
            commitState.workflowState.commit();
            Object result = commitState.getWorkflowState("wf_key");
            assertEquals("wf_value", result);
        }
        
        @Test
        @DisplayName("get workflow state with null key")
        void testGetWorkflowStateWithNullKey() {
            Object result = commitState.getWorkflowState(null);
            assertNull(result);
        }
        
        @Test
        @DisplayName("update and commit workflow state")
        void testUpdateAndCommitWorkflowState() {
            commitState.updateAndCommitWorkflowState(Map.of("wf_key", "wf_value"));
            Object result = commitState.getWorkflowState("wf_key");
            assertEquals("wf_value", result);
        }
        
        @Test
        @DisplayName("set outputs")
        void testSetOutputs() {
            commitState.setOutputs(Map.of("output_key", "output_value"));
            commitState.ioState.commit();
            Object result = commitState.ioState.get("default");
            assertEquals(Map.of("output_key", "output_value"), result);
        }
        
        @Test
        @DisplayName("set outputs with null")
        void testSetOutputsWithNull() {
            commitState.setOutputs(null); // Should not raise
        }
        
        @Test
        @DisplayName("get inputs")
        void testGetInputs() {
            commitState.ioState.updateById("default", Map.of("default", Map.of("input_key", "input_value")));
            commitState.ioState.commit();
            Object result = commitState.getInputs(null);
            assertEquals(Map.of("input_key", "input_value"), result);
        }
        
        @Test
        @DisplayName("commit user inputs")
        void testCommitUserInputs() {
            commitState.commitUserInputs(Map.of("user_input", "value"));
            // Check io_state
            Object ioResult = commitState.ioState.get("user_input");
            assertEquals("value", ioResult);
            // Check global_state
            Object globalResult = commitState.globalState.get("user_input");
            assertEquals("value", globalResult);
        }
        
        @Test
        @DisplayName("commit user inputs with null")
        void testCommitUserInputsWithNull() {
            commitState.commitUserInputs(null); // Should not raise
        }
        
        @Test
        @DisplayName("commit all states")
        void testCommitAllStates() {
            commitState.ioState.updateById("default", Map.of("io", 1));
            commitState.globalState.updateById("default", Map.of("global", 2));
            commitState.compState.updateById("default", Map.of("comp", 3));
            commitState.workflowState.updateById("workflow", Map.of("wf", 4));
            commitState.commit();
            assertEquals(1, commitState.ioState.get("io"));
            assertEquals(2, commitState.globalState.get("global"));
            assertEquals(3, commitState.compState.get("comp"));
            assertEquals(4, commitState.workflowState.get("wf"));
        }
        
        @Test
        @DisplayName("rollback all states")
        void testRollbackAllStates() {
            commitState.ioState.updateById("default", Map.of("io", 1));
            commitState.globalState.updateById("default", Map.of("global", 2));
            commitState.compState.updateById("default", Map.of("comp", 3));
            commitState.workflowState.updateById("default", Map.of("wf", 4));
            commitState.rollback();
            // After rollback, updates should be cleared
            assertTrue(commitState.ioState.getUpdates().get("default") == null || 
                       commitState.ioState.getUpdates().get("default").isEmpty());
        }
        
        @Test
        @DisplayName("get state exports all snapshots")
        void testGetStateExportsAllSnapshots() {
            commitState.ioState.updateById("default", Map.of("io", 1));
            commitState.ioState.commit();
            commitState.globalState.updateById("default", Map.of("global", 2));
            commitState.globalState.commit();
            Map<String, Object> state = commitState.getState();
            assertTrue(state.containsKey(StateConstants.IO_STATE_KEY));
            assertTrue(state.containsKey(StateConstants.GLOBAL_STATE_KEY));
            assertTrue(state.containsKey(StateConstants.COMP_STATE_KEY));
            assertTrue(state.containsKey(StateConstants.WORKFLOW_STATE_KEY));
        }
        
        @Test
        @DisplayName("set state restores all snapshots")
        void testSetStateRestoresAllSnapshots() {
            Map<String, Object> stateSnapshot = new HashMap<>();
            stateSnapshot.put(StateConstants.IO_STATE_KEY, Map.of("io", 1));
            stateSnapshot.put(StateConstants.GLOBAL_STATE_KEY, Map.of("global", 2));
            stateSnapshot.put(StateConstants.COMP_STATE_KEY, Map.of("comp", 3));
            stateSnapshot.put(StateConstants.WORKFLOW_STATE_KEY, Map.of("wf", 4));
            commitState.setState(stateSnapshot);
            assertEquals(1, commitState.ioState.get("io"));
            assertEquals(2, commitState.globalState.get("global"));
            assertEquals(3, commitState.compState.get("comp"));
            assertEquals(4, commitState.workflowState.get("wf"));
        }
        
        @Test
        @DisplayName("get updates exports all updates")
        void testGetUpdatesExportsAllUpdates() {
            commitState.ioState.updateById("default", Map.of("io", 1));
            commitState.globalState.updateById("default", Map.of("global", 2));
            Map<String, Object> updates = commitState.getUpdates();
            assertTrue(updates.containsKey(StateConstants.IO_STATE_UPDATES_KEY));
            assertTrue(updates.containsKey(StateConstants.GLOBAL_STATE_UPDATES_KEY));
            assertTrue(updates.containsKey(StateConstants.COMP_STATE_UPDATES_KEY));
            assertTrue(updates.containsKey(StateConstants.WORKFLOW_STATE_UPDATES_KEY));
        }
        
        @Test
        @DisplayName("create node state")
        void testCreateNodeState() {
            State nodeState = commitState.createNodeState("child_node", "parent_node");
            assertInstanceOf(CommitState.class, nodeState);
            CommitState childState = (CommitState) nodeState;
            // Should share the same underlying states
            assertSame(childState.ioState, commitState.ioState);
            assertSame(childState.globalState, commitState.globalState);
            assertSame(childState.compState, commitState.compState);
            assertSame(childState.workflowState, commitState.workflowState);
        }
        
        @Test
        @DisplayName("commit cmp")
        void testCommitCmp() {
            CommitState nodeState = (CommitState) commitState.createNodeState("node1", "");
            nodeState.compState.updateById("node1", Map.of("comp", 1));
            nodeState.ioState.updateById("node1", Map.of("io", 2));
            nodeState.commitCmp();
            // Only node1's updates should be committed
            var compUpdates = nodeState.compState.getUpdates().get("node1");
            var ioUpdates = nodeState.ioState.getUpdates().get("node1");
            assertTrue(compUpdates == null || compUpdates.isEmpty());
            assertTrue(ioUpdates == null || ioUpdates.isEmpty());
        }
    }
    
    @Nested
    @DisplayName("InMemoryWorkflowState Tests")
    class InMemoryWorkflowStateTests {
        
        @Test
        @DisplayName("default construction")
        void testDefaultConstruction() {
            InMemoryWorkflowState state = new InMemoryWorkflowState();
            // Should work without errors
            assertNotNull(state);
        }
        
        @Test
        @DisplayName("construction with shared global state")
        void testConstructionWithSharedGlobalState() {
            InMemoryCommitState sharedGlobal = new InMemoryCommitState();
            InMemoryWorkflowState state = new InMemoryWorkflowState(sharedGlobal);
            assertSame(sharedGlobal, state.globalState);
        }
        
        @Test
        @DisplayName("inherits commit state behavior")
        void testInheritsCommitStateBehavior() {
            InMemoryWorkflowState state = new InMemoryWorkflowState();
            // Test basic operations work
            state.commitUserInputs(Map.of("a", 1));
            assertEquals(1, state.getGlobal("a"));
            state.setOutputs(Map.of("b", 2));
            state.commit();
            assertEquals(Map.of("b", 2), state.ioState.get("default"));
        }
    }
}

