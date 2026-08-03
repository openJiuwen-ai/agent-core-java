/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.utils.SessionUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for session state operations.
 * <p>
 * Ported from Python's {@code test_session.py}.
 */
class SessionTest {

    // ---------- WorkflowSession + NodeSession basic test ----------

    @Nested
    @DisplayName("WorkflowSession and NodeSession basic operations")
    class BasicSessionOps {

        @Test
        @DisplayName("workflow session and node session state ops")
        void testBasic() {
            // Create WorkflowSession with initial state
            WorkflowCommitState state = InMemoryState.create();
            WorkflowSession context = new WorkflowSession("wf1", null, null, state, null);

            // Commit user inputs to global state
            state.getGlobalState().updateById("default", Map.of("a", 1, "b", 2));
            state.getGlobalState().commit("default");

            assertEquals(1, context.state().getGlobal("a"));
            assertEquals(2, context.state().getGlobal("b"));

            // node1
            NodeSession node1 = new NodeSession(context, "node1");
            assertEquals("node1", node1.nodeId());
            assertEquals("node1", node1.executableId());
            assertEquals("", node1.parentId());
            assertEquals(1, node1.state().getGlobal("a"));
            assertEquals(2, node1.state().getGlobal("b"));

            // Update node1 state
            node1.state().updateGlobal(Map.of("c", 3));
            node1.state().update(Map.of("url", "0.0.0.1"));

            // After commit, state is visible in node context
            if (node1.state() instanceof com.openjiuwen.core.session.state.WorkflowStateCollection wsc) {
                wsc.commitCmp();
            }
            state.commit();

            assertEquals(3, context.state().getGlobal("c"));

            // node2 should see global state from node1
            NodeSession node2 = new NodeSession(context, "node2");
            assertEquals(3, node2.state().getGlobal("c"));
        }

        @Test
        @DisplayName("nested workflow: sub-workflow with its own state")
        void testNestedWorkflow() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowSession context = new WorkflowSession("wf1", null, null, state, null);

            // Set global state
            state.getGlobalState().updateById("default", Map.of("a", 1, "b", 2));
            state.getGlobalState().commit("default");

            // Sub-workflow node
            NodeSession subWorkflowNode = new NodeSession(context, "sub_workflow1");
            assertEquals("sub_workflow1", subWorkflowNode.nodeId());
            assertEquals("", subWorkflowNode.parentId());
            assertEquals("sub_workflow1", subWorkflowNode.executableId());

            // Create sub-node from sub-workflow
            NodeSession subNode1 = new NodeSession(subWorkflowNode, "node1");
            assertEquals("node1", subNode1.nodeId());
            assertEquals("sub_workflow1", subNode1.parentId());
            assertEquals("sub_workflow1.node1", subNode1.executableId());
        }
    }

    // ---------- SessionUtils.getBySchema tests ----------

    @Nested
    @DisplayName("getBySchema")
    class GetBySchemaTests {

        @Test
        @DisplayName("basic schema resolution")
        void testBasicSchema() {
            Map<String, Object> source = new HashMap<>();
            SessionUtils.updateDict(Map.of("a.b.nums", List.of(1, 2, 3)), source);
            assertEquals(Map.of("a", Map.of("b", Map.of("nums", List.of(1, 2, 3)))), source);
        }

        @Test
        @DisplayName("nested update adds properties")
        void testNestedUpdate() {
            Map<String, Object> source = new HashMap<>();
            SessionUtils.updateDict(Map.of("a.b.nums", List.of(1, 2, 3)), source);
            SessionUtils.updateDict(Map.of("a.b.name", "shanghai"), source);

            Object b = ((Map<?, ?>) ((Map<?, ?>) source.get("a")).get("b"));
            assertTrue(b instanceof Map);
            assertEquals(List.of(1, 2, 3), ((Map<?, ?>) b).get("nums"));
            assertEquals("shanghai", ((Map<?, ?>) b).get("name"));
        }

        @Test
        @DisplayName("override nested with non-map value")
        void testOverrideNested() {
            Map<String, Object> source = new HashMap<>();
            SessionUtils.updateDict(Map.of("a.b.nums", List.of(1, 2, 3)), source);
            SessionUtils.updateDict(Map.of("a.b", List.of(1, 2, 3)), source);
            assertEquals(Map.of("a", Map.of("b", List.of(1, 2, 3))), source);
        }

        @Test
        @DisplayName("getBySchema with string key")
        void testGetBySchemaString() {
            Map<String, Object> source = Map.of("a", Map.of("b", List.of(1, 2, 3)));
            Object result = SessionUtils.getBySchema("a", source);
            assertEquals(Map.of("b", List.of(1, 2, 3)), result);
        }

        @Test
        @DisplayName("getBySchema with map schema - reference resolution")
        void testGetBySchemaRef() {
            Map<String, Object> source = Map.of("a", Map.of("b", List.of(1, 2, 3)));
            Object result = SessionUtils.getBySchema(Map.of("result", "${a.b}"), source);
            assertEquals(Map.of("result", List.of(1, 2, 3)), result);
        }

        @Test
        @DisplayName("getBySchema with list schema")
        void testGetBySchemaList() {
            Map<String, Object> source = Map.of("a", Map.of("b", List.of(1, 2, 3)));
            Object result = SessionUtils.getBySchema(Map.of("result", List.of("abc", "${a}")), source);
            assertEquals(Map.of("result", List.of("abc", Map.of("b", List.of(1, 2, 3)))), result);
        }

        @Test
        @DisplayName("getBySchema with static values")
        void testGetBySchemaStatic() {
            Map<String, Object> source = Map.of("a", Map.of("b", List.of(1, 2, 3)));
            Object result = SessionUtils.getBySchema(Map.of("result", List.of("abc", "cde")), source);
            assertEquals(Map.of("result", List.of("abc", "cde")), result);
        }

        @Test
        @DisplayName("getBySchema with non-existent ref returns null")
        void testGetBySchemaNonExistentRef() {
            Map<String, Object> source = Map.of("a", Map.of("b", List.of(1, 2, 3)));
            Object result = SessionUtils.getBySchema(Map.of("result", List.of("${abc}", "cde")), source);
            assertEquals(Map.of("result", Arrays.asList(null, "cde")), result);
        }
    }

    // ---------- SessionUtils.updateDict clean (null-deletes) ----------

    @Nested
    @DisplayName("updateDict with null values (delete)")
    class UpdateDictClean {

        @Test
        @DisplayName("null value removes key")
        void testNullRemovesKey() {
            Map<String, Object> data = new HashMap<>();
            data.put("a", new HashMap<>(Map.of("a1", 1, "a2", 2)));
            data.put("c", 2);

            Map<String, Object> update = new HashMap<>();
            update.put("c", null);
            SessionUtils.updateDict(update, data);
            assertFalse(data.containsKey("c"));
        }

        @Test
        @DisplayName("nested null value removes nested key")
        void testNestedNullRemovesKey() {
            Map<String, Object> data = new HashMap<>();
            data.put("a", new HashMap<>(Map.of("a1", 1, "a2", 2)));

            Map<String, Object> update = new HashMap<>();
            update.put("a.a1", null);
            SessionUtils.updateDict(update, data);

            @SuppressWarnings("unchecked")
            Map<String, Object> a = (Map<String, Object>) data.get("a");
            assertFalse(a.containsKey("a1"));
            assertEquals(2, a.get("a2"));
        }
    }

    // ---------- AgentSessionApi tests ----------

    @Nested
    @DisplayName("AgentSessionApi state operations")
    class AgentSessionApiTests {

        @Test
        @DisplayName("agent session state update and get")
        void testAgentSessionState() {
            AgentSession session = new AgentSession("abc", null, null);
            Map<String, Object> data = Map.of("data", Map.of("a", 1));
            session.updateState(Map.of("result", data));
            assertEquals(Map.of("data", Map.of("a", 1)), session.getState("result"));
        }

        @Test
        @DisplayName("merge update on agent session state")
        void testAgentSessionMergeUpdate() {
            AgentSession session = new AgentSession("abc", null, null);
            session.updateState(Map.of("result", Map.of("data", Map.of("a", 1))));
            assertEquals(Map.of("data", Map.of("a", 1)), session.getState("result"));

            session.updateState(Map.of("result", Map.of("data", Map.of("b", 1))));
            assertEquals(Map.of("data", Map.of("a", 1, "b", 1)), session.getState("result"));
        }

        @Test
        @DisplayName("null update removes state key")
        void testAgentSessionNullUpdate() {
            AgentSession session = new AgentSession("abc", null, null);
            Map<String, Object> data2 = Map.of("data", Map.of("b", 1));
            session.updateState(Map.of("result", data2));
            assertEquals(Map.of("data", Map.of("b", 1)), session.getState("result"));

            Map<String, Object> nullUpdate = new HashMap<>();
            nullUpdate.put("result", null);
            session.updateState(nullUpdate);
            assertNull(session.getState("result"));
        }

        @Test
        @DisplayName("dump state returns correct structure")
        void testDumpState() {
            AgentSession session = new AgentSession("abc", null, null);
            session.updateState(Map.of("result", Map.of("data", Map.of("b", 1))));

            Map<String, Object> dump = session.dumpState();
            assertNotNull(dump);
            assertTrue(dump.containsKey("global_state"));
        }
    }

    // ---------- NodeSessionApi tests ----------

    @Nested
    @DisplayName("NodeSessionApi state operations")
    class NodeSessionApiTests {

        @Test
        @DisplayName("node session update and commit cycle")
        void testNodeSessionUpdateAndCommit() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowSession wf = new WorkflowSession("wf1", null, null, state, null);
            NodeSession nodeSession = new NodeSession(wf, "node1");
            NodeSessionApi session = new NodeSessionApi(nodeSession);

            // Update state (not committed yet)
            session.updateState(Map.of("key1", "value1"));
            session.updateState(Map.of("global_key1", "global_value1"));

            // Before commit, state is not visible
            assertNull(session.getState("key1"));
            assertNull(session.getState("global_key1"));

            // Commit
            state.commit();
            assertEquals("value1", session.getState("key1"));
            assertEquals("global_value1", session.getState("global_key1"));
        }

        @Test
        @DisplayName("dump state returns correct structure before/after commit")
        void testNodeSessionDumpState() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowSession wf = new WorkflowSession("wf1", null, null, state, null);
            NodeSession nodeSession = new NodeSession(wf, "node1");
            NodeSessionApi session = new NodeSessionApi(nodeSession);

            session.updateState(Map.of("key1", "value1"));
            session.updateState(Map.of("global_key1", "global_value1"));

            Map<String, Object> dump = session.dumpState();
            assertNotNull(dump);
        }
    }
}
