/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for WorkflowSession and NodeSession basic operations.
 * <p>
 * Ported from Python's {@code test_session.py::TestSession::test_basic}.
 */
class SessionBasicTest {

    @Nested
    @DisplayName("WorkflowSession + NodeSession basic state ops")
    class BasicOps {

        @Test
        @DisplayName("workflow context global state access via commit_user_inputs")
        void testWorkflowContextGlobalState() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowSession context = new WorkflowSession("wf1", null, null, state, null);

            // Simulate commit_user_inputs({'a': 1, 'b': 2})
            state.commitUserInputs(Map.of("a", 1, "b", 2));

            assertEquals(1, context.state().getGlobal("a"));
            assertEquals(2, context.state().getGlobal("b"));
        }

        @Test
        @DisplayName("node1 inherits workflow global state")
        void testNodeInheritsGlobalState() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowSession context = new WorkflowSession("wf1", null, null, state, null);
            // Use globalState directly (not commitUserInputs which goes to ioState)
            state.getGlobalState().updateById("default", Map.of("a", 1, "b", 2));
            state.getGlobalState().commit("default");

            NodeSession node1 = new NodeSession(context, "node1");
            assertEquals("node1", node1.nodeId());
            assertEquals("node1", node1.executableId());
            assertEquals("", node1.parentId());
            assertEquals(1, node1.state().getGlobal("a"));
            assertEquals(2, node1.state().getGlobal("b"));
        }

        @Test
        @DisplayName("node1 update global and component state, commit then verify")
        void testNodeUpdateAndCommit() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowSession context = new WorkflowSession("wf1", null, null, state, null);
            state.commitUserInputs(Map.of("a", 1, "b", 2));

            NodeSession node1 = new NodeSession(context, "node1");
            node1.state().updateGlobal(Map.of("c", 3));
            node1.state().update(Map.of("url", "0.0.0.1"));

            // Commit node state
            if (node1.state() instanceof WorkflowStateCollection wsc) {
                wsc.commitCmp();
            }
            state.commit();

            assertEquals(3, context.state().getGlobal("c"));
            assertEquals("0.0.0.1", node1.state().get("url"));
        }

        @Test
        @DisplayName("node2 sees global state from node1 but not component state")
        void testNode2SeesGlobalNotComponent() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowSession context = new WorkflowSession("wf1", null, null, state, null);
            state.commitUserInputs(Map.of("a", 1, "b", 2));

            NodeSession node1 = new NodeSession(context, "node1");
            node1.state().updateGlobal(Map.of("c", 3));
            node1.state().update(Map.of("url", "0.0.0.1"));
            if (node1.state() instanceof WorkflowStateCollection wsc) {
                wsc.commitCmp();
            }
            state.commit();

            NodeSession node2 = new NodeSession(context, "node2");
            assertEquals(3, node2.state().getGlobal("c"));
            // node2 should not see node1's component state
            assertNull(node2.state().get("url"));
        }

        @Test
        @DisplayName("nested workflow: sub-workflow node session hierarchy")
        void testNestedWorkflow() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowSession context = new WorkflowSession("wf1", null, null, state, null);
            state.commitUserInputs(Map.of("a", 1, "b", 2));

            // Sub-workflow node
            NodeSession subWorkflow = new NodeSession(context, "sub_workflow1");

            // Sub-node1 under sub-workflow
            NodeSession subNode1 = new NodeSession(subWorkflow, "node1");
            assertEquals("node1", subNode1.nodeId());
            assertEquals("sub_workflow1", subNode1.parentId());
            assertEquals("sub_workflow1.node1", subNode1.executableId());
        }

        @Test
        @DisplayName("nested sub-node state update and commit")
        void testNestedSubNodeStateUpdate() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowSession context = new WorkflowSession("wf1", null, null, state, null);
            state.commitUserInputs(Map.of("a", 11, "b", 12));

            NodeSession subWorkflow = new NodeSession(context, "sub_workflow1");
            NodeSession subNode1 = new NodeSession(subWorkflow, "node1");

            subNode1.state().updateGlobal(Map.of("c", 4));
            subNode1.state().update(Map.of("url", "0.0.0.2"));

            if (subNode1.state() instanceof WorkflowStateCollection wsc) {
                wsc.commitCmp();
            }
            state.commit();

            assertEquals(4, subNode1.state().getGlobal("c"));
            assertEquals("0.0.0.2", subNode1.state().get("url"));
        }

        @Test
        @DisplayName("workflow session generates unique session ID when not provided")
        void testAutoGenerateSessionId() {
            WorkflowSession session = new WorkflowSession("wf1");
            assertNotNull(session.sessionId());
            assertFalse(session.sessionId().isEmpty());
        }

        @Test
        @DisplayName("workflow session preserves parent session ID")
        void testPreservesParentSessionId() {
            WorkflowSession parent = new WorkflowSession("wf1", null, "parent-session-id", null, null);
            WorkflowSession child = new WorkflowSession("wf2", parent);
            assertEquals("parent-session-id", child.sessionId());
        }

        @Test
        @DisplayName("workflow nesting depth is 0 for root session")
        void testWorkflowNestingDepth() {
            WorkflowSession session = new WorkflowSession("wf1");
            assertEquals(0, session.workflowNestingDepth());
        }

        @Test
        @DisplayName("node session uses parent config")
        void testNodeUsesParentConfig() {
            WorkflowSession parent = new WorkflowSession("wf1");
            NodeSession node = new NodeSession(parent, "node1");
            assertSame(parent.config(), node.config());
        }
    }
}
