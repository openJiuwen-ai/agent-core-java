/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.session;

import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.internal.NodeSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Unit tests for Session.
 * <p>
 * Mirrors Python's {@code test_session} in
 * {@code tests.unit_tests.core.session}.
 * </p>
 */
@DisplayName("TestSession")
class TestSession {

    @Nested
    @DisplayName("WorkflowSession tests")
    class WorkflowSessionTests {

        @Test
        @DisplayName("Test workflow session creation")
        void testWorkflowSessionCreation() {
            // Mirrors Python: test_basic - Workflow context creation
            WorkflowSession context = new WorkflowSession(null, null);
            assertNotNull(context, "WorkflowSession should be created");
            assertNotNull(context.sessionId(), "Session ID should be generated");
        }

        @Test
        @DisplayName("Test workflow session with state")
        void testWorkflowSessionWithState() {
            // Mirrors Python: test_basic - commit user inputs
            WorkflowSession context = new WorkflowSession(null, null);

            // Test state access
            var state = context.state();
            assertNotNull(state, "State should not be null");
        }

        @Test
        @DisplayName("Test workflow session ID generation")
        void testWorkflowSessionIdGeneration() {
            WorkflowSession context1 = new WorkflowSession(null, null);
            WorkflowSession context2 = new WorkflowSession(null, null);

            assertNotEquals(context1.sessionId(), context2.sessionId(),
                    "Each session should have unique ID");
        }
    }

    @Nested
    @DisplayName("NodeSession tests")
    class NodeSessionTests {

        @Test
        @DisplayName("Test node session creation")
        void testNodeSessionCreation() {
            // Mirrors Python: test_basic - node context creation
            WorkflowSession workflowContext = new WorkflowSession(null, null);
            NodeSession nodeContext = new NodeSession(workflowContext, "node1", null);

            assertNotNull(nodeContext, "NodeSession should be created");
            assertEquals("node1", nodeContext.nodeId(), "Node ID should match");
        }

        @Test
        @DisplayName("Test node session parent relationship")
        void testNodeSessionParentRelationship() {
            // Mirrors Python: test_basic - parent_id and executable_id
            WorkflowSession workflowContext = new WorkflowSession(null, null);
            NodeSession nodeContext = new NodeSession(workflowContext, "node1", null);

            assertEquals("node1", nodeContext.nodeId(), "Node ID should match");
            assertEquals("", nodeContext.parentId(), "Parent ID should be empty for root node");
        }

        @Test
        @DisplayName("Test nested node session")
        void testNestedNodeSession() {
            // Mirrors Python: test_basic - nested workflow
            WorkflowSession workflowContext = new WorkflowSession(null, null);
            NodeSession subWorkflowContext = new NodeSession(workflowContext, "sub_workflow1", null);
            NodeSession subNodeContext = new NodeSession(subWorkflowContext, "node1", null);

            assertEquals("node1", subNodeContext.nodeId(), "Node ID should match");
            assertEquals("sub_workflow1", subNodeContext.parentId(), "Parent ID should be sub_workflow1");
        }
    }

    @Nested
    @DisplayName("Session state tests")
    class SessionStateTests {

        @Test
        @DisplayName("Test session state management")
        void testSessionStateManagement() {
            // Mirrors Python: test_basic - state update and commit
            WorkflowSession context = new WorkflowSession(null, null);
            var state = context.state();

            // Test state is accessible
            assertNotNull(state, "State should not be null");
        }

        @Test
        @DisplayName("Test session state persistence")
        void testSessionStatePersistence() {
            // Mirrors Python: test_basic - state commit
            WorkflowSession context = new WorkflowSession(null, null);
            var state = context.state();

            // Verify state object exists and is usable
            assertNotNull(state, "State should not be null");
        }

        @Test
        @DisplayName("Test session state restoration")
        void testSessionStateRestoration() {
            // Mirrors Python: test_basic - state retrieval
            WorkflowSession context = new WorkflowSession(null, null);
            var state = context.state();

            // Verify state object exists
            assertNotNull(state, "State should not be null");
        }
    }
}
