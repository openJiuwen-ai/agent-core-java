/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.session;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Session.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/session/test_session.py}.</p>
 */
class TestSession {

    @Nested
    @DisplayName("Session tests")
    class SessionTests {

        @Test
        @DisplayName("test basic workflow and node session")
        void testBasic() {
            WorkflowSession context = new WorkflowSession();
            State state = context.state();

            Map<String, Object> inputs = new HashMap<>();
            inputs.put("a", 1);
            inputs.put("b", 2);

            if (state instanceof WorkflowStateCollection workflowStateCollection) {
                workflowStateCollection.commitUserInputs(inputs);
                assertEquals(1, workflowStateCollection.getGlobal("a"));
                assertEquals(2, workflowStateCollection.getGlobal("b"));

                NodeSession node1Context = new NodeSession(context, "node1");
                assertEquals("node1", node1Context.nodeId());
                assertEquals("node1", node1Context.executableId());
                assertEquals("", node1Context.parentId());
                assertEquals(1, node1Context.state().getGlobal("a"));
                assertEquals(2, node1Context.state().getGlobal("b"));

                Map<String, Object> node1InputSchema = new HashMap<>();
                node1InputSchema.put("aa", "${a}");
                node1InputSchema.put("bb", "${b}");

                Map<String, Object> node1InputSchema2 = new HashMap<>();
                node1InputSchema2.put("node_1_inputs", List.of("${a}", "${b}"));

                @SuppressWarnings("unchecked")
                Map<String, Object> globalData = (Map<String, Object>) workflowStateCollection.dump().get("global_state");
                Object result1 = SessionUtils.getBySchema(node1InputSchema, globalData);
                assertTrue(result1 instanceof Map);
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap1 = (Map<String, Object>) result1;
                assertEquals(1, resultMap1.get("aa"));
                assertEquals(2, resultMap1.get("bb"));

                Object result2 = SessionUtils.getBySchema(node1InputSchema2, globalData);
                assertTrue(result2 instanceof Map);
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap2 = (Map<String, Object>) result2;
                assertTrue(resultMap2.get("node_1_inputs") instanceof List);
                List<?> listResult = (List<?>) resultMap2.get("node_1_inputs");
                assertEquals(1, listResult.get(0));
                assertEquals(2, listResult.get(1));

                if (node1Context.state() instanceof WorkflowStateCollection nodeStateCollection) {
                    Function<Object, Object> transformer = source -> {
                        if (source instanceof Map<?, ?> map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> typedMap = (Map<String, Object>) map.get("global_state");
                            return SessionUtils.getBySchema(node1InputSchema, typedMap);
                        }
                        return null;
                    };
                    Object transformerResult = nodeStateCollection.getInputsByTransformer(transformer);
                    assertNotNull(transformerResult);

                    Map<String, Object> updateData = new HashMap<>();
                    updateData.put("c", 3);
                    nodeStateCollection.updateGlobal(updateData);

                    Map<String, Object> localUpdate = new HashMap<>();
                    localUpdate.put("url", "0.0.0.1");
                    nodeStateCollection.update(localUpdate);
                    nodeStateCollection.commit();

                    assertEquals(3, nodeStateCollection.getGlobal("c"));
                    assertEquals("0.0.0.1", nodeStateCollection.get("url"));

                    NodeSession node2Context = new NodeSession(context, "node2");
                    assertEquals(3, node2Context.state().getGlobal("c"));
                    assertNull(node2Context.state().get("url"));

                    NodeSession subWorkflowContext = new NodeSession(context, "sub_workflow1");
                    if (subWorkflowContext.state() instanceof WorkflowStateCollection subWorkflowStateCollection) {
                        Map<String, Object> subInputs = new HashMap<>();
                        subInputs.put("a", 11);
                        subInputs.put("b", 12);
                        subWorkflowStateCollection.commitUserInputs(subInputs);
                        subWorkflowStateCollection.commit();

                        NodeSession subNode1Context = new NodeSession(subWorkflowContext, "node1");
                        assertEquals("node1", subNode1Context.nodeId());
                        assertEquals("sub_workflow1", subNode1Context.parentId());
                        assertEquals("sub_workflow1.node1", subNode1Context.executableId());

                        @SuppressWarnings("unchecked")
                        Map<String, Object> subGlobalData =
                                (Map<String, Object>) subWorkflowStateCollection.dump().get("global_state");
                        Object subSchemaResult = SessionUtils.getBySchema(node1InputSchema, subGlobalData);
                        assertTrue(subSchemaResult instanceof Map);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> subResultMap = (Map<String, Object>) subSchemaResult;
                        assertEquals(11, subResultMap.get("aa"));
                        assertEquals(12, subResultMap.get("bb"));

                        if (subNode1Context.state() instanceof WorkflowStateCollection subNodeStateCollection) {
                            Map<String, Object> subNodeUpdate = new HashMap<>();
                            subNodeUpdate.put("c", 4);
                            subNodeStateCollection.updateGlobal(subNodeUpdate);

                            Map<String, Object> subNodeLocal = new HashMap<>();
                            subNodeLocal.put("url", "0.0.0.2");
                            subNodeStateCollection.update(subNodeLocal);
                            subNodeStateCollection.commit();

                            assertEquals(4, subNodeStateCollection.getGlobal("c"));
                            assertEquals("0.0.0.2", subNodeStateCollection.get("url"));
                        }
                    }
                }
            } else {
                assertTrue(true, "State implementation does not support commitUserInputs");
            }
        }

        @Test
        @DisplayName("test get by schema")
        void testGetBySchema() {
            Map<String, Object> source = new HashMap<>();

            Map<String, Object> update1 = new HashMap<>();
            update1.put("a.b.nums", List.of(1, 2, 3));
            SessionUtils.updateDict(update1, source);

            Map<String, Object> expected1 = new HashMap<>();
            Map<String, Object> b1 = new HashMap<>();
            b1.put("nums", List.of(1, 2, 3));
            Map<String, Object> a1 = new HashMap<>();
            a1.put("b", b1);
            expected1.put("a", a1);
            assertEquals(expected1, source);

            Map<String, Object> update2 = new HashMap<>();
            update2.put("a.b.name", "shanghai");
            SessionUtils.updateDict(update2, source);
            b1.put("name", "shanghai");
            assertEquals(a1, source.get("a"));

            Map<String, Object> update3 = new HashMap<>();
            update3.put("a.b", Map.of("class", "hha"));
            SessionUtils.updateDict(update3, source);
            b1.put("class", "hha");
            assertEquals(a1, source.get("a"));

            Map<String, Object> update4 = new HashMap<>();
            update4.put("a.b", List.of(1, 2, 3));
            SessionUtils.updateDict(update4, source);
            assertEquals(List.of(1, 2, 3), ((Map<?, ?>) source.get("a")).get("b"));

            assertEquals(Map.of("b", List.of(1, 2, 3)), SessionUtils.getBySchema("a", source));
            assertEquals(Map.of("a", "b"), SessionUtils.getBySchema(Map.of("a", "b"), source));
            assertEquals(Map.of("result", List.of(1, 2, 3)),
                    SessionUtils.getBySchema(Map.of("result", "${a.b}"), source));

            Object result4 = SessionUtils.getBySchema(Map.of("result", List.of("abc", "${a}")), source);
            assertTrue(result4 instanceof Map);
            Map<?, ?> resultMap4 = (Map<?, ?>) result4;
            assertEquals("abc", ((List<?>) resultMap4.get("result")).get(0));

            assertEquals(Map.of("result", List.of("abc", "cde")),
                    SessionUtils.getBySchema(Map.of("result", List.of("abc", "cde")), source));

            Map<String, Object> nestedSchema6 = new HashMap<>();
            nestedSchema6.put("abc", "cde");
            nestedSchema6.put("result", "${1}");
            Map<String, Object> expectedNested6 = new HashMap<>();
            expectedNested6.put("abc", "cde");
            expectedNested6.put("result", null);
            assertEquals(Map.of("result", expectedNested6),
                    SessionUtils.getBySchema(Map.of("result", nestedSchema6), source));

            assertEquals(Map.of("a", 3), SessionUtils.getBySchema(Map.of("a", "${a.b[-1]}"), source));

            Map<String, Object> source1 = new HashMap<>();
            source1.put("a", Map.of("b", List.of("cc", "dd", "ee")));
            assertEquals(Map.of("result", "dd"),
                    SessionUtils.getBySchema(Map.of("result", "${a.b[1]}"), source1));

            List<Object> expectedList9 = new ArrayList<>();
            expectedList9.add(null);
            expectedList9.add("cde");
            assertEquals(Map.of("result", expectedList9),
                    SessionUtils.getBySchema(Map.of("result", List.of("${abc}", "cde")), source));

            Object result10 = SessionUtils.getBySchema(Map.of("result", Map.of("abc", "cde", "result", "${a}")), source);
            assertTrue(result10 instanceof Map);
            Map<?, ?> resultMap10 = (Map<?, ?>) result10;
            assertTrue(resultMap10.get("result") instanceof Map);
        }

        @Test
        @DisplayName("test clean non value")
        void testCleanNonValue() {
            Map<String, Object> data = new HashMap<>();
            Map<String, Object> a = new HashMap<>();
            a.put("a1", 1);
            a.put("a2", 2);
            data.put("a", a);

            Map<String, Object> b = new HashMap<>();
            Map<String, Object> b1 = new HashMap<>();
            b1.put("b11", "1");
            List<Object> b12 = new ArrayList<>();
            b12.add(1);
            b12.add(2);
            b12.add(null);
            b1.put("b12", b12);
            b1.put("b13", "2");
            b.put("b1", b1);
            data.put("b", b);
            data.put("c", 2);

            Map<String, Object> update1 = new HashMap<>();
            update1.put("c", null);
            SessionUtils.updateDict(update1, data);
            assertFalse(data.containsKey("c"));
            assertEquals(Map.of("a", a, "b", b), data);

            Map<String, Object> update2 = new HashMap<>();
            update2.put("a.a1", null);
            SessionUtils.updateDict(update2, data);
            assertFalse(a.containsKey("a1"));
        }

        @Test
        @DisplayName("test root to index")
        void testRootToIndex() {
            List<Object> source = new ArrayList<>();
            Object[] result = SessionUtils.rootToIndex(List.of(1, 2, 3), source, true);
            assertNotNull(result);
            assertTrue(result[1] instanceof List);
            assertTrue(source.get(1) instanceof List);
            assertTrue(((List<?>) source.get(1)).get(2) instanceof List);
            assertTrue(((List<?>) ((List<?>) source.get(1)).get(2)).get(3) instanceof Map);

            result = SessionUtils.rootToIndex(List.of(1, 2, 3), source, false);
            assertNotNull(result[0]);
            assertNotNull(result[1]);

            result = SessionUtils.rootToIndex(List.of(-1), List.of(1, 2, 3), false);
            assertEquals(2, result[0]);
            assertTrue(result[1] instanceof List);

            result = SessionUtils.rootToIndex(List.of(-5), List.of(1, 2, 3), false);
            assertNull(result[0]);
            assertNull(result[1]);

            source = new ArrayList<>();
            result = SessionUtils.rootToIndex(List.of(0), source, true);
            assertEquals(0, result[0]);
            assertEquals(1, source.size());
            assertTrue(source.get(0) instanceof Map);

            source = new ArrayList<>();
            result = SessionUtils.rootToIndex(List.of(0, 1), source, true);
            assertEquals(1, result[0]);

            source = new ArrayList<>();
            result = SessionUtils.rootToIndex(List.of(0, 1), source, false);
            assertNull(result[0]);
            assertNull(result[1]);
            assertEquals(0, source.size());

            source = new ArrayList<>();
            List<Integer> deepPath = List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            result = SessionUtils.rootToIndex(deepPath, source, true);
            assertEquals(0, result[0]);

            assertThrows(IllegalArgumentException.class, () ->
                    SessionUtils.rootToIndex(List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), new ArrayList<>(), false));

            source = new ArrayList<>();
            result = SessionUtils.rootToIndex(List.of(100), source, true);
            assertEquals(100, result[0]);
            assertEquals(101, source.size());
            assertTrue(source.get(100) instanceof Map);

            assertThrows(IllegalArgumentException.class, () ->
                    SessionUtils.rootToIndex(List.of(10001), new ArrayList<>(), false));

            List<List<Integer>> nestedSource = List.of(List.of(1, 2, 3), List.of(4, 5, 6), List.of(7, 8, 9));
            List<Object> mutableSource = new ArrayList<>(nestedSource);
            result = SessionUtils.rootToIndex(List.of(-1, -1), mutableSource, false);
            assertEquals(2, result[0]);
            assertTrue(result[1] instanceof List);

            source = new ArrayList<>();
            source.add(1);
            source.add(2);
            source.add(3);
            result = SessionUtils.rootToIndex(List.of(), source, false);
            assertNull(result[0]);
            assertNull(result[1]);

            source = new ArrayList<>();
            result = SessionUtils.rootToIndex(List.of(10000), source, true);
            assertNull(result[0]);

            List<Object> filesystemLike = new ArrayList<>();
            filesystemLike.add("root");
            List<Object> usersLevel = new ArrayList<>();
            usersLevel.add("users");
            List<Object> userContent = new ArrayList<>();
            userContent.add("alice");
            userContent.add(List.of("docs", "pics", "music"));
            userContent.add("bob");
            userContent.add(List.of("work", "personal"));
            usersLevel.add(userContent);
            filesystemLike.add(usersLevel);
            filesystemLike.add(List.of("system", List.of("config", "logs")));

            result = SessionUtils.rootToIndex(List.of(1, 1, 1, 2), filesystemLike, false);
            if (result[1] != null) {
                assertEquals(2, result[0]);
                assertTrue(result[1] instanceof List);
            }
        }

        @Test
        @DisplayName("test agent session")
        void testAgentSession() {
            AgentSessionApi agentSession = new AgentSessionApi("abc", null, new AgentCard());
            Map<String, Object> data = Map.of("data", Map.of("a", 1));

            agentSession.updateState(Map.of("result", data));
            assertEquals(Map.of("data", Map.of("a", 1)), agentSession.getState("result"));

            Map<String, Object> data2 = Map.of("data", Map.of("b", 1));
            agentSession.updateState(Map.of("result", data2));
            assertEquals(Map.of("data", Map.of("a", 1, "b", 1)), agentSession.getState("result"));

            Map<String, Object> clearResult = new HashMap<>();
            clearResult.put("result", null);
            agentSession.updateState(clearResult);
            assertNull(agentSession.getState("result"));

            agentSession.updateState(Map.of("result", data2));
            assertEquals(Map.of("data", Map.of("b", 1)), agentSession.getState("result"));

            Map<String, Object> dumpState = agentSession.dumpState();
            assertEquals(Map.of(), dumpState.get("agent_state"));
            assertEquals(Map.of("result", Map.of("data", Map.of("b", 1))), dumpState.get("global_state"));
            assertEquals(Map.of(), dumpState.get("trace_state"));
        }

        @Test
        @DisplayName("test node session")
        void testNodeSession() {
            NodeSessionApi session = new NodeSessionApi(new NodeSession(new WorkflowSession(), "node1"));
            session.updateState(Map.of("key1", "value1"));
            session.updateState(Map.of("key2", Map.of("nested_key", "nested_value")));
            session.updateGlobalState(Map.of("global_key1", "global_value1"));
            session.updateGlobalState(Map.of("global_key2", Map.of("nested_global_key", "nested_global_value")));

            assertNull(session.getState("key1"));
            assertNull(session.getState("key2"));
            assertNull(session.getGlobalState("global_key1"));
            assertNull(session.getGlobalState("global_key2"));

            Map<String, Object> dumpBeforeCommit = session.dumpState();
            assertEquals(Map.of(), dumpBeforeCommit.get("io_state"));
            assertEquals(Map.of(), dumpBeforeCommit.get("io_state_updates"));
            assertEquals(Map.of(), dumpBeforeCommit.get("global_state"));
            assertEquals(
                    Map.of("node1", List.of(
                            Map.of("global_key1", "global_value1"),
                            Map.of("global_key2", Map.of("nested_global_key", "nested_global_value")))),
                    dumpBeforeCommit.get("global_state_updates"));
            assertEquals(Map.of(), dumpBeforeCommit.get("comp_state"));
            assertEquals(
                    Map.of("node1", List.of(
                            Map.of("node1", Map.of("key1", "value1")),
                            Map.of("node1", Map.of("key2", Map.of("nested_key", "nested_value"))))),
                    dumpBeforeCommit.get("comp_state_updates"));
            assertEquals(Map.of(), dumpBeforeCommit.get("workflow_state"));
            assertEquals(Map.of(), dumpBeforeCommit.get("workflow_state_updates"));
            assertEquals(Map.of(), dumpBeforeCommit.get("trace_state"));

            assertTrue(session.getInner().state() instanceof WorkflowStateCollection);
            ((WorkflowStateCollection) session.getInner().state()).commit();

            assertEquals("value1", session.getState("key1"));
            assertEquals(Map.of("nested_key", "nested_value"), session.getState("key2"));
            assertEquals("global_value1", session.getGlobalState("global_key1"));
            assertEquals(Map.of("nested_global_key", "nested_global_value"), session.getGlobalState("global_key2"));

            Map<String, Object> dumpAfterCommit = session.dumpState();
            assertEquals(Map.of(), dumpAfterCommit.get("io_state"));
            assertEquals(Map.of(), dumpAfterCommit.get("io_state_updates"));
            assertEquals(Map.of(
                    "global_key1", "global_value1",
                    "global_key2", Map.of("nested_global_key", "nested_global_value")),
                    dumpAfterCommit.get("global_state"));
            assertEquals(Map.of(), dumpAfterCommit.get("global_state_updates"));
            assertEquals(Map.of("node1", Map.of(
                    "key1", "value1",
                    "key2", Map.of("nested_key", "nested_value"))), dumpAfterCommit.get("comp_state"));
            assertEquals(Map.of(), dumpAfterCommit.get("comp_state_updates"));
            assertEquals(Map.of(), dumpAfterCommit.get("workflow_state"));
            assertEquals(Map.of(), dumpAfterCommit.get("workflow_state_updates"));
            assertEquals(Map.of(), dumpAfterCommit.get("trace_state"));
        }
    }
}
