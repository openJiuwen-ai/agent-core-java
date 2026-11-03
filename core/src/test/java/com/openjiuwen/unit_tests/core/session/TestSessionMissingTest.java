/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.session;

import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <p>Mirrors Python's {@code TestSession} in
 * {@code tests/unit_tests/core/session/test_session.py}.</p>
 */
class TestSessionMissingTest {

    @Test
    void testBasic() {
        WorkflowSession context = new WorkflowSession();
        context.state().commitUserInputs(linkedMapOf("a", 1, "b", 2));
        assertEquals(1, context.state().getGlobal("a"));
        assertEquals(2, context.state().getGlobal("b"));

        NodeSession node1Context = new NodeSession(context, "node1");
        WorkflowCommitState node1State = (WorkflowCommitState) node1Context.state();
        assertEquals("node1", node1Context.nodeId());
        assertEquals("node1", node1Context.executableId());
        assertEquals("", node1Context.parentId());
        assertEquals(1, node1State.getGlobal("a"));
        assertEquals(2, node1State.getGlobal("b"));

        Map<String, Object> node1InputSchema = linkedMapOf("aa", "${a}", "bb", "${b}");
        Map<String, Object> node1InputSchema2 = linkedMapOf("node_1_inputs", mutableList("${a}", "${b}"));
        assertEquals(linkedMapOf("aa", 1, "bb", 2), node1State.getGlobal(node1InputSchema));
        assertEquals(linkedMapOf("node_1_inputs", mutableList(1, 2)), node1State.getGlobal(node1InputSchema2));

        Function<Object, Object> node1Transformer = rawState ->
                SessionUtils.getBySchema(node1InputSchema, castMap(rawState));
        assertEquals(linkedMapOf("aa", 1, "bb", 2), node1State.getInputsByTransformer(node1Transformer));

        node1State.updateGlobal(linkedMapOf("c", 3));
        node1State.update(linkedMapOf("url", "0.0.0.1"));
        node1State.commit();
        assertEquals(3, node1State.getGlobal("c"));
        assertEquals("0.0.0.1", node1State.get("url"));

        NodeSession node2Context = new NodeSession(context, "node2");
        WorkflowCommitState node2State = (WorkflowCommitState) node2Context.state();
        assertEquals(3, node2State.getGlobal("c"));
        assertNull(node2State.get("url"));

        NodeSession subWorkflowContext = new NodeSession(context, "sub_workflow1");
        WorkflowCommitState subWorkflowState = (WorkflowCommitState) subWorkflowContext.state();
        subWorkflowState.commitUserInputs(linkedMapOf("a", 11, "b", 12));
        subWorkflowState.commit();

        NodeSession subNode1Context = new NodeSession(subWorkflowContext, "node1");
        WorkflowCommitState subNode1State = (WorkflowCommitState) subNode1Context.state();
        assertEquals("node1", subNode1Context.nodeId());
        assertEquals("sub_workflow1", subNode1Context.parentId());
        assertEquals("sub_workflow1.node1", subNode1Context.executableId());
        assertEquals(linkedMapOf("aa", 11, "bb", 12), subNode1State.getGlobal(node1InputSchema));

        subNode1State.updateGlobal(linkedMapOf("c", 4));
        subNode1State.update(linkedMapOf("url", "0.0.0.2"));
        subNode1State.commit();
        assertEquals(4, subNode1State.getGlobal("c"));
        assertEquals("0.0.0.2", subNode1State.get("url"));
    }

    @Test
    void testGetBySchema() {
        Map<String, Object> source = new LinkedHashMap<>();
        SessionUtils.updateDict(linkedMapOf("a.b.nums", mutableList(1, 2, 3)), source);
        assertEquals(linkedMapOf("a", linkedMapOf("b", linkedMapOf("nums", mutableList(1, 2, 3)))), source);

        SessionUtils.updateDict(linkedMapOf("a.b.name", "shanghai"), source);
        assertEquals(
                linkedMapOf("a", linkedMapOf("b", linkedMapOf("nums", mutableList(1, 2, 3), "name", "shanghai"))),
                source
        );

        SessionUtils.updateDict(linkedMapOf("a.b", linkedMapOf("class", "hha")), source);
        assertEquals(
                linkedMapOf("a", linkedMapOf(
                        "b",
                        linkedMapOf("nums", mutableList(1, 2, 3), "name", "shanghai", "class", "hha")
                )),
                source
        );

        SessionUtils.updateDict(linkedMapOf("a.b", mutableList(1, 2, 3)), source);
        assertEquals(linkedMapOf("a", linkedMapOf("b", mutableList(1, 2, 3))), source);
        assertEquals(linkedMapOf("b", mutableList(1, 2, 3)), SessionUtils.getBySchema("a", source));
        assertEquals(linkedMapOf("a", "b"), SessionUtils.getBySchema(linkedMapOf("a", "b"), source));
        assertEquals(
                linkedMapOf("result", mutableList(1, 2, 3)),
                SessionUtils.getBySchema(linkedMapOf("result", "${a.b}"), source)
        );
        assertEquals(
                linkedMapOf("result", mutableList("abc", linkedMapOf("b", mutableList(1, 2, 3)))),
                SessionUtils.getBySchema(linkedMapOf("result", mutableList("abc", "${a}")), source)
        );
        assertEquals(
                linkedMapOf("result", mutableList("abc", "cde")),
                SessionUtils.getBySchema(linkedMapOf("result", mutableList("abc", "cde")), source)
        );
        assertEquals(
                linkedMapOf("result", linkedMapOf("abc", "cde", "result", null)),
                SessionUtils.getBySchema(linkedMapOf("result", linkedMapOf("abc", "cde", "result", "${1}")), source)
        );
        assertEquals(linkedMapOf("a", 3), SessionUtils.getBySchema(linkedMapOf("a", "${a.b[-1]}"), source));

        Map<String, Object> source1 = linkedMapOf("a", linkedMapOf("b", mutableList("cc", "dd", "ee")));
        assertEquals(
                linkedMapOf("result", "dd"),
                SessionUtils.getBySchema(linkedMapOf("result", "${a.b[1]}"), source1)
        );

        assertEquals(
                linkedMapOf("result", mutableList(null, "cde")),
                SessionUtils.getBySchema(linkedMapOf("result", mutableList("${abc}", "cde")), source)
        );
        assertEquals(
                linkedMapOf("result", linkedMapOf("abc", "cde", "result", linkedMapOf("b", mutableList(1, 2, 3)))),
                SessionUtils.getBySchema(linkedMapOf("result", linkedMapOf("abc", "cde", "result", "${a}")), source)
        );
    }

    @Test
    void testCleanNonValue() {
        Map<String, Object> data = linkedMapOf(
                "a", linkedMapOf("a1", 1, "a2", 2),
                "b", linkedMapOf("b1", linkedMapOf("b11", "1", "b12", mutableList(1, 2, null), "b13", "2")),
                "c", 2
        );

        SessionUtils.updateDict(linkedMapOf("c", null), data);
        assertEquals(
                linkedMapOf(
                        "a", linkedMapOf("a1", 1, "a2", 2),
                        "b", linkedMapOf("b1", linkedMapOf("b11", "1", "b12", mutableList(1, 2, null), "b13", "2"))
                ),
                data
        );

        SessionUtils.updateDict(linkedMapOf("a.a1", null), data);
        assertEquals(
                linkedMapOf(
                        "a", linkedMapOf("a2", 2),
                        "b", linkedMapOf("b1", linkedMapOf("b11", "1", "b12", mutableList(1, 2, null), "b13", "2"))
                ),
                data
        );
    }

    @Test
    void testRootToIndex() {
        List<Object> source = new ArrayList<>();
        SessionUtils.rootToIndex(mutableList(1, 2, 3), source, true);
        assertEquals(new LinkedHashMap<>(), ((List<?>) ((List<?>) source.get(1)).get(2)).get(3));
        assertEquals(mutableList(null, mutableList(null, null, mutableList(null, null, null, linkedMapOf()))), source);
        Object[] result = SessionUtils.rootToIndex(mutableList(1, 2, 3), source);
        assertEquals(3, result[0]);
        assertEquals(((List<?>) source.get(1)).get(2), result[1]);

        List<Object> existing = mutableList(1, mutableList(2, mutableList(2, mutableList(3, 4, 5,
                mutableList(7, 8, 9)))));
        assertArrayEquals(new Object[]{2, ((List<?>) ((List<?>) ((List<?>) existing.get(1)).get(1)).get(1)).get(3)},
                SessionUtils.rootToIndex(mutableList(1, 1, 1, 3, 2), existing));

        assertArrayEquals(new Object[]{2, mutableList(1, 2, 3)}, SessionUtils.rootToIndex(mutableList(-1),
                mutableList(1, 2, 3)));
        assertArrayEquals(new Object[]{null, null}, SessionUtils.rootToIndex(mutableList(-5), mutableList(1, 2, 3)));

        source = new ArrayList<>();
        result = SessionUtils.rootToIndex(mutableList(0), source, true);
        assertArrayEquals(new Object[]{0, source}, result);
        assertEquals(mutableList(linkedMapOf()), source);

        source = new ArrayList<>();
        result = SessionUtils.rootToIndex(mutableList(0, 1), source, true);
        assertArrayEquals(new Object[]{1, source.get(0)}, result);
        assertEquals(mutableList(mutableList(null, linkedMapOf())), source);

        source = new ArrayList<>();
        result = SessionUtils.rootToIndex(mutableList(0, 1), source, false);
        assertArrayEquals(new Object[]{null, null}, result);
        assertEquals(mutableList(), source);

        List<Object> tupleLikeSource = List.of(1, new ArrayList<>(List.of(2, 3)));
        result = SessionUtils.rootToIndex(mutableList(1, 5), tupleLikeSource, true);
        assertArrayEquals(new Object[]{5, tupleLikeSource.get(1)}, result);
        assertEquals(mutableList(2, 3, null, null, null, linkedMapOf()), tupleLikeSource.get(1));

        source = mutableList(1, mutableList(2, mutableList(3, 4, 5)), 6);
        result = SessionUtils.rootToIndex(mutableList(1, 1, 0), source);
        assertArrayEquals(new Object[]{0, ((List<?>) source.get(1)).get(1)}, result);
        assertEquals(3, ((List<?>) result[1]).get((Integer) result[0]));

        source = new ArrayList<>();
        List<Integer> deepPath = mutableList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        result = SessionUtils.rootToIndex(deepPath, source, true);
        assertEquals(0, result[0]);

        IllegalArgumentException tooDeep = assertThrows(IllegalArgumentException.class,
                () -> SessionUtils.rootToIndex(mutableList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), new ArrayList<>()));
        assertEquals("Nesting level too deep, level limit is 10", tooDeep.getMessage());

        source = new ArrayList<>();
        result = SessionUtils.rootToIndex(mutableList(100), source, true);
        assertEquals(100, result[0]);
        assertEquals(101, source.size());
        assertEquals(new LinkedHashMap<>(), source.get(100));

        IllegalArgumentException largeIndex = assertThrows(IllegalArgumentException.class,
                () -> SessionUtils.rootToIndex(mutableList(10001), new ArrayList<>()));
        assertEquals("Index must be between [0,10000]", largeIndex.getMessage());

        source = mutableList(mutableList(1, 2, 3), mutableList(4, 5, 6), mutableList(7, 8, 9));
        result = SessionUtils.rootToIndex(mutableList(-1, -1), source);
        assertArrayEquals(new Object[]{2, source.get(2)}, result);
        assertEquals(9, ((List<?>) result[1]).get((Integer) result[0]));

        assertArrayEquals(new Object[]{null, null}, SessionUtils.rootToIndex(mutableList(), mutableList(1, 2, 3)));
        assertArrayEquals(new Object[]{null, null}, SessionUtils.rootToIndex(mutableList(0), null));

        List<Object> originalData = mutableList(1, mutableList(2, 3));
        List<Object> originalCopy = mutableList(1, mutableList(2, 3));
        result = SessionUtils.rootToIndex(mutableList(1, 5), originalData, false);
        assertArrayEquals(new Object[]{null, null}, result);
        assertEquals(originalCopy, originalData);

        source = mutableList(1, mutableList(2, 3, mutableList(4, 5, 6)), 7);
        result = SessionUtils.rootToIndex(mutableList(1, -1, -1), source);
        assertArrayEquals(new Object[]{2, ((List<?>) source.get(1)).get(2)}, result);
        assertEquals(6, ((List<?>) result[1]).get((Integer) result[0]));

        source = new ArrayList<>();
        result = SessionUtils.rootToIndex(mutableList(0), source, true);
        assertArrayEquals(new Object[]{0, source}, result);
        assertEquals(mutableList(linkedMapOf()), source);

        source = new ArrayList<>();
        result = SessionUtils.rootToIndex(mutableList(10000), source, true);
        assertNull(result[0]);

        source = new ArrayList<>();
        SessionUtils.rootToIndex(mutableList(1, 2, 3), source, true);
        assertInstanceOf(List.class, source.get(1));
        assertInstanceOf(List.class, ((List<?>) source.get(1)).get(2));
        assertInstanceOf(Map.class, ((List<?>) ((List<?>) source.get(1)).get(2)).get(3));

        source = mutableList(1, mutableList(2, mutableList(3, 4)), 5);
        result = SessionUtils.rootToIndex(mutableList(1, 1, 0), source);
        assertArrayEquals(new Object[]{0, ((List<?>) source.get(1)).get(1)}, result);
        assertEquals(3, ((List<?>) result[1]).get((Integer) result[0]));
        assertEquals(mutableList(1, mutableList(2, mutableList(3, 4)), 5), source);

        List<Object> filesystemLike = mutableList(
                "root",
                mutableList("users", mutableList("alice", mutableList("docs", "pics", "music"),
                        "bob", mutableList("work", "personal"))),
                mutableList("system", mutableList("config", "logs"))
        );
        result = SessionUtils.rootToIndex(mutableList(1, 1, 1, 2), filesystemLike);
        assertArrayEquals(new Object[]{2, ((List<?>) ((List<?>) filesystemLike.get(1)).get(1)).get(1)}, result);
        assertEquals("music", ((List<?>) result[1]).get((Integer) result[0]));

        source = mutableList(1, mutableList(2, 3));
        result = SessionUtils.rootToIndex(mutableList(1, 5, 2), source, true);
        assertArrayEquals(new Object[]{2, ((List<?>) source.get(1)).get(5)}, result);
        assertEquals(mutableList(null, null, linkedMapOf()), ((List<?>) source.get(1)).get(5));
        assertNull(((List<?>) source.get(1)).get(3));
        assertNull(((List<?>) source.get(1)).get(4));
    }

    @Test
    void testAgentSession() {
        AgentSession agentSession = new AgentSession("abc", null, new AgentCard());
        Map<String, Object> data = linkedMapOf("data", linkedMapOf("a", 1));
        agentSession.updateState(linkedMapOf("result", data));
        assertEquals(linkedMapOf("data", linkedMapOf("a", 1)), agentSession.getState("result"));
        assertEquals(linkedMapOf("data", linkedMapOf("a", 1)), agentSession.getState("result"));

        Map<String, Object> data2 = linkedMapOf("data", linkedMapOf("b", 1));
        agentSession.updateState(linkedMapOf("result", data2));
        assertEquals(linkedMapOf("data", linkedMapOf("a", 1, "b", 1)), agentSession.getState("result"));

        agentSession.updateState(linkedMapOf("result", null));
        assertNull(agentSession.getState("result"));

        agentSession.updateState(linkedMapOf("result", data2));
        assertEquals(linkedMapOf("data", linkedMapOf("b", 1)), agentSession.getState("result"));

        assertEquals(
                linkedMapOf(
                        "agent_state", linkedMapOf(),
                        "global_state", linkedMapOf("result", linkedMapOf("data", linkedMapOf("b", 1))),
                        "trace_state", linkedMapOf()
                ),
                agentSession.dumpState()
        );
    }

    @Test
    void testNodeSession() {
        NodeSessionApi session = new NodeSessionApi(new NodeSession(new WorkflowSession(), "node1"));
        session.updateState(linkedMapOf("key1", "value1"));
        session.updateState(linkedMapOf("key2", linkedMapOf("nested_key", "nested_value")));
        session.updateGlobalState(linkedMapOf("global_key1", "global_value1"));
        session.updateGlobalState(linkedMapOf("global_key2", linkedMapOf("nested_global_key", "nested_global_value")));

        assertNull(session.getState("key1"));
        assertNull(session.getState("key2"));
        assertNull(session.getGlobalState("global_key1"));
        assertNull(session.getGlobalState("global_key2"));

        assertEquals(
                linkedMapOf(
                        "io_state", linkedMapOf(),
                        "io_state_updates", linkedMapOf(),
                        "global_state", linkedMapOf(),
                        "global_state_updates", linkedMapOf("node1", mutableList(
                                linkedMapOf("global_key1", "global_value1"),
                                linkedMapOf("global_key2", linkedMapOf("nested_global_key", "nested_global_value"))
                        )),
                        "comp_state", linkedMapOf(),
                        "comp_state_updates", linkedMapOf("node1", mutableList(
                                linkedMapOf("node1", linkedMapOf("key1", "value1")),
                                linkedMapOf("node1", linkedMapOf("key2", linkedMapOf("nested_key", "nested_value")))
                        )),
                        "workflow_state", linkedMapOf(),
                        "workflow_state_updates", linkedMapOf(),
                        "trace_state", linkedMapOf()
                ),
                session.dumpState()
        );

        ((WorkflowCommitState) session.getInner().state()).commit();
        assertEquals("value1", session.getState("key1"));
        assertEquals(linkedMapOf("nested_key", "nested_value"), session.getState("key2"));
        assertEquals("global_value1", session.getGlobalState("global_key1"));
        assertEquals(linkedMapOf("nested_global_key", "nested_global_value"), session.getGlobalState("global_key2"));

        assertEquals(
                linkedMapOf(
                        "io_state", linkedMapOf(),
                        "io_state_updates", linkedMapOf(),
                        "global_state", linkedMapOf(
                                "global_key1", "global_value1",
                                "global_key2", linkedMapOf("nested_global_key", "nested_global_value")
                        ),
                        "global_state_updates", linkedMapOf(),
                        "comp_state", linkedMapOf(
                                "node1", linkedMapOf(
                                        "key1", "value1",
                                        "key2", linkedMapOf("nested_key", "nested_value")
                                )
                        ),
                        "comp_state_updates", linkedMapOf(),
                        "workflow_state", linkedMapOf(),
                        "workflow_state_updates", linkedMapOf(),
                        "trace_state", linkedMapOf()
                ),
                session.dumpState()
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> linkedMapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @SafeVarargs
    private static <T> List<T> mutableList(T... values) {
        return new ArrayList<>(Arrays.asList(values));
    }
}
