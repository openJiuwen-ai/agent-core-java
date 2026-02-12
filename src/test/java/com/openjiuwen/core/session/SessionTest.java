/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.CommitState;
import com.openjiuwen.core.session.state.ReadableStateLike;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Session, WorkflowSession, and NodeSession.
 * 
 * <p>Converted from Python: test_session.py</p>
 * <p>Python测试类: TestSession</p>
 * <p>Python测试方法数: 5</p>
 */
class SessionTest {
    
    /**
     * Python: test_basic
     * 测试WorkflowSession和NodeSession的基本操作
     */
    @Test
    @DisplayName("test_basic - workflow and node session operations")
    void testBasic() {
        // Workflow context
        WorkflowSession context = new WorkflowSession();
        // Python: context.state().commit_user_inputs({'a': 1, 'b': 2})
        CommitState state = (CommitState) context.getState();
        state.commitUserInputs(Map.of("a", 1, "b", 2));
        assertEquals(1, state.getGlobal("a"));
        assertEquals(2, state.getGlobal("b"));
        
        // node1节点
        // Python: node1_context = NodeSession(context, "node1")
        NodeSession node1Context = new NodeSession(context, "node1", "");
        assertEquals("node1", node1Context.getNodeId());
        assertEquals("node1", node1Context.getExecutableId());
        assertEquals("", node1Context.getParentId());
        assertEquals(1, node1Context.getState().getGlobal("a"));
        assertEquals(2, node1Context.getState().getGlobal("b"));
        
        // 通过input_schema获取inputs
        Map<String, Object> node1InputSchema = Map.of("aa", "${a}", "bb", "${b}");
        Map<String, Object> node1InputSchema2 = Map.of("node_1_inputs", List.of("${a}", "${b}"));
        assertEquals(Map.of("aa", 1, "bb", 2), node1Context.getState().getGlobal(node1InputSchema));
        assertEquals(Map.of("node_1_inputs", List.of(1, 2)), node1Context.getState().getGlobal(node1InputSchema2));
        
        // 通过transformer获取inputs
        // Python: node1_context.state().get_inputs_by_transformer(node1_transformer)
        CommitState node1State = (CommitState) node1Context.getState();
        Object result = node1State.getInputsByTransformer(
            (ReadableStateLike s) -> s.get(node1InputSchema)
        );
        assertEquals(Map.of("aa", 1, "bb", 2), result);
        
        node1State.updateGlobal(Map.of("c", 3));
        node1State.update(Map.of("url", "0.0.0.1"));
        node1State.commit();
        assertEquals(3, node1State.getGlobal("c"));
        assertEquals("0.0.0.1", node1State.get("url"));
        
        NodeSession node2Context = new NodeSession(context, "node2", "");
        assertEquals(3, node2Context.getState().getGlobal("c"));
        assertNull(node2Context.getState().get("url"));
        
        // 嵌套workflow
        NodeSession subWorkflowContext = new NodeSession(context, "sub_workflow1", "");
        CommitState subWorkflowState = (CommitState) subWorkflowContext.getState();
        subWorkflowState.commitUserInputs(Map.of("a", 11, "b", 12));
        subWorkflowState.commit();
        
        NodeSession subNode1Context = new NodeSession(subWorkflowContext, "node1", "");
        assertEquals("node1", subNode1Context.getNodeId());
        assertEquals("sub_workflow1", subNode1Context.getParentId());
        assertEquals("sub_workflow1.node1", subNode1Context.getExecutableId());
        assertEquals(Map.of("aa", 11, "bb", 12), subNode1Context.getState().getGlobal(node1InputSchema));
        
        CommitState subNode1State = (CommitState) subNode1Context.getState();
        subNode1State.updateGlobal(Map.of("c", 4));
        subNode1State.update(Map.of("url", "0.0.0.2"));
        subNode1State.commit();
        assertEquals(4, subNode1State.getGlobal("c"));
        assertEquals("0.0.0.2", subNode1State.get("url"));
    }
    
    /**
     * Python: test_get_by_schema
     * 测试SessionUtils.getBySchema方法
     */
    @Test
    @DisplayName("test_get_by_schema - schema-based value extraction")
    void testGetBySchema() {
        var source = new HashMap<String, Object>();
        
        // 增加a.b: nums属性
        // Python: update_dict({"a.b.nums": [1, 2, 3]}, source)
        SessionUtils.updateDict(Map.of("a.b.nums", List.of(1, 2, 3)), source);
        assertEquals(Map.of("a", Map.of("b", Map.of("nums", List.of(1, 2, 3)))), source);
        
        // 增加a.b: name属性
        SessionUtils.updateDict(Map.of("a.b.name", "shanghai"), source);
        @SuppressWarnings("unchecked")
        Map<String, Object> aMap = (Map<String, Object>) source.get("a");
        @SuppressWarnings("unchecked")
        Map<String, Object> bMap = (Map<String, Object>) aMap.get("b");
        assertEquals(List.of(1, 2, 3), bMap.get("nums"));
        assertEquals("shanghai", bMap.get("name"));
        
        // 增加a.b: class属性
        SessionUtils.updateDict(Map.of("a.b", Map.of("class", "hha")), source);
        @SuppressWarnings("unchecked")
        Map<String, Object> bMap2 = (Map<String, Object>) ((Map<String, Object>) source.get("a")).get("b");
        assertEquals("hha", bMap2.get("class"));
        
        // 覆盖a.b所有
        SessionUtils.updateDict(Map.of("a.b", List.of(1, 2, 3)), source);
        assertEquals(Map.of("a", Map.of("b", List.of(1, 2, 3))), source);
        
        // Python: get_by_schema("a", data=source)
        assertEquals(Map.of("b", List.of(1, 2, 3)), SessionUtils.getBySchema("a", source));
        assertEquals(Map.of("a", "b"), SessionUtils.getBySchema(Map.of("a", "b"), source));
        assertEquals(Map.of("result", List.of(1, 2, 3)), SessionUtils.getBySchema(Map.of("result", "${a.b}"), source));
        
        // 带数组索引的引用
        var source2 = new HashMap<String, Object>();
        source2.put("a", Map.of("b", List.of(1, 2, 3)));
        assertEquals(Map.of("a", 3), SessionUtils.getBySchema(Map.of("a", "${a.b[-1]}"), source2));
        
        Map<String, Object> source1 = new HashMap<>();
        source1.put("a", Map.of("b", List.of("cc", "dd", "ee")));
        assertEquals(Map.of("result", "dd"), SessionUtils.getBySchema(Map.of("result", "${a.b[1]}"), source1));
    }
    
    /**
     * Python: test_clean_non_value
     * 测试updateDict删除null值的功能
     */
    @Test
    @DisplayName("test_clean_non_value - removes null entries")
    void testCleanNonValue() {
        var data = new HashMap<String, Object>();
        data.put("a", new HashMap<>(Map.of("a1", 1, "a2", 2)));
        // List.of不接受null，使用Arrays.asList
        var b12List = new ArrayList<Integer>();
        b12List.add(1);
        b12List.add(2);
        b12List.add(null);
        data.put("b", Map.of("b1", Map.of("b11", "1", "b12", b12List, "b13", "2")));
        data.put("c", 2);
        
        // Python: update_dict({"c": None}, data)
        var update = new HashMap<String, Object>();
        update.put("c", null);
        SessionUtils.updateDict(update, data);
        assertFalse(data.containsKey("c"));
        
        // Python: update_dict({"a.a1": None}, data)
        var update2 = new HashMap<String, Object>();
        update2.put("a.a1", null);
        SessionUtils.updateDict(update2, data);
        @SuppressWarnings("unchecked")
        var aMap = (Map<String, Object>) data.get("a");
        assertFalse(aMap.containsKey("a1"));
        assertTrue(aMap.containsKey("a2"));
    }
    
    /**
     * Python: test_root_to_index
     * 测试SessionUtils.rootToIndex方法
     * 注意：Python返回tuple(index, container)，Java返回PathResult(key, container)
     */
    @Nested
    @DisplayName("test_root_to_index - list index navigation")
    class RootToIndexTests {
        
        @Test
        @DisplayName("Test 1: Basic creation with multiple levels")
        void testBasicCreation() {
            var source = new ArrayList<Object>();
            SessionUtils.rootToIndex(List.of(1, 2, 3), source, true);
            // Python: assert source[1][2][3] == {}
            @SuppressWarnings("unchecked")
            var level1 = (List<Object>) source.get(1);
            @SuppressWarnings("unchecked")
            var level2 = (List<Object>) level1.get(2);
            assertEquals(new HashMap<>(), level2.get(3));
            
            // Python: result = root_to_index([1, 2, 3], source)
            //         assert result[1][result[0]] == source[1][2][3]
            var result = SessionUtils.rootToIndex(List.of(1, 2, 3), source, false);
            @SuppressWarnings("unchecked")
            var resultContainer = (List<Object>) result.container();
            assertEquals(level2.get(3), resultContainer.get((int) result.key()));
        }
        
        @Test
        @DisplayName("Test 2: Navigation in existing complex structure")
        void testNavigationInExistingStructure() {
            // Python: source = [1, [2, (2, [3, 4, 5, [7, 8, 9]])]]
            // Note: Java doesn't have tuples, using nested lists instead
            var innerMost = new ArrayList<>(List.of(7, 8, 9));
            var level3 = new ArrayList<Object>(List.of(3, 4, 5, innerMost));
            var level2 = new ArrayList<Object>(List.of(2, level3));  // Simulating tuple as list
            var level1 = new ArrayList<Object>(List.of(2, level2));
            var source = new ArrayList<Object>(List.of(1, level1));
            
            // Python: assert root_to_index([1, 1, 1, 3, 2], source=source) == (2, source[1][1][1][3])
            var result = SessionUtils.rootToIndex(List.of(1, 1, 1, 3, 2), source, false);
            assertEquals(2, result.key());
            assertSame(innerMost, result.container());
        }
        
        @Test
        @DisplayName("Test 3: Negative index access")
        void testNegativeIndexAccess() {
            var result = SessionUtils.rootToIndex(List.of(-1), new ArrayList<>(List.of(1, 2, 3)), false);
            // Python: assert root_to_index([-1], [1, 2, 3]) == (2, [1, 2, 3])
            assertEquals(2, result.key());
        }
        
        @Test
        @DisplayName("Test 4: Negative index out of bounds")
        void testNegativeIndexOutOfBounds() {
            var result = SessionUtils.rootToIndex(List.of(-5), new ArrayList<>(List.of(1, 2, 3)), false);
            // Python: assert root_to_index([-5], [1, 2, 3]) == (None, None)
            assertNull(result.key());
        }
        
        @Test
        @DisplayName("Test 5: Single level access with creation")
        void testSingleLevelAccessWithCreation() {
            var source = new ArrayList<Object>();
            var result = SessionUtils.rootToIndex(List.of(0), source, true);
            // Python: assert result == (0, source)
            assertEquals(0, result.key());
            assertSame(source, result.container());
            assertEquals(1, source.size());
        }
        
        @Test
        @DisplayName("Test 6: Two level access with creation")
        void testTwoLevelAccessWithCreation() {
            var source = new ArrayList<Object>();
            var result = SessionUtils.rootToIndex(List.of(0, 1), source, true);
            // Python: assert result == (1, source[0])
            //         assert source == [[None, {}]]
            assertEquals(1, result.key());
            @SuppressWarnings("unchecked")
            var innerList = (List<Object>) source.get(0);
            assertSame(innerList, result.container());
        }
        
        @Test
        @DisplayName("Test 7: Access without creation (should fail)")
        void testAccessWithoutCreation() {
            var source = new ArrayList<Object>();
            var result = SessionUtils.rootToIndex(List.of(0, 1), source, false);
            // Python: assert result == (None, None)
            assertNull(result.key());
            assertEquals(0, source.size()); // Source should remain unchanged
        }
        
        @Test
        @DisplayName("Test 10: Maximum depth test (exactly at limit)")
        void testMaximumDepthExactlyAtLimit() {
            var source = new ArrayList<Object>();
            var deepPath = List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0); // 10 levels - exactly at limit
            var result = SessionUtils.rootToIndex(deepPath, source, true);
            assertEquals(0, result.key());
        }
        
        @Test
        @DisplayName("Test 11: Exceed maximum depth raises exception")
        void testExceedMaximumDepth() {
            var source = new ArrayList<Object>();
            var overDeepPath = List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0); // 11 levels
            // Python: raises ValueError with "Nesting level too deep"
            assertThrows(IllegalArgumentException.class, () -> 
                SessionUtils.rootToIndex(overDeepPath, source, false)
            );
        }
        
        @Test
        @DisplayName("Test 12: Large index within bounds")
        void testLargeIndexWithinBounds() {
            var source = new ArrayList<Object>();
            var result = SessionUtils.rootToIndex(List.of(100), source, true);
            // Python: assert result[0] == 100
            //         assert len(source) == 101
            //         assert source[100] == {}
            assertEquals(100, result.key());
            assertEquals(101, source.size());
            assertEquals(new HashMap<>(), source.get(100));
        }
        
        @Test
        @DisplayName("Test 13: Large index out of bounds raises exception")
        void testLargeIndexOutOfBounds() {
            // Python: raises ValueError with "Index must be between"
            assertThrows(IllegalArgumentException.class, () -> 
                SessionUtils.rootToIndex(List.of(10001), new ArrayList<>(), false)
            );
        }
        
        @Test
        @DisplayName("Test 14: Complex negative index chain")
        void testComplexNegativeIndexChain() {
            // Python: source = [[1, 2, 3], [4, 5, 6], [7, 8, 9]]
            var source = new ArrayList<Object>(List.of(
                new ArrayList<>(List.of(1, 2, 3)),
                new ArrayList<>(List.of(4, 5, 6)),
                new ArrayList<>(List.of(7, 8, 9))
            ));
            var result = SessionUtils.rootToIndex(List.of(-1, -1), source, false);
            // Python: assert result == (2, source[2])  # source[2][2] = 9
            //         assert result[1][result[0]] == 9
            assertEquals(2, result.key());
            @SuppressWarnings("unchecked")
            var container = (List<Object>) result.container();
            assertEquals(9, container.get((int) result.key()));
        }
        
        @Test
        @DisplayName("Test 15: Empty source and indexes")
        void testEmptySourceAndIndexes() {
            // Python: assert root_to_index([], [1, 2, 3]) == (None, None)
            var result1 = SessionUtils.rootToIndex(List.of(), new ArrayList<>(List.of(1, 2, 3)), false);
            assertNull(result1.key());
            
            // Python: assert root_to_index([0], None) == (None, None)
            var result2 = SessionUtils.rootToIndex(List.of(0), null, false);
            assertNull(result2.key());
        }
        
        @Test
        @DisplayName("Test 16: Data integrity - verify original data not modified when create_if_absent=False")
        void testDataIntegrity() {
            // Python: original_data = [1, [2, 3]]
            var originalData = new ArrayList<Object>(List.of(1, new ArrayList<>(List.of(2, 3))));
            var originalCopy = new ArrayList<Object>(List.of(1, new ArrayList<>(List.of(2, 3))));
            
            var result = SessionUtils.rootToIndex(List.of(1, 5), originalData, false);
            // Python: assert result == (None, None)
            //         assert original_data == original_copy  # Data should remain unchanged
            assertNull(result.key());
            assertEquals(originalCopy.size(), originalData.size());
        }
        
        @Test
        @DisplayName("Test 17: Multiple negative indexes in path")
        void testMultipleNegativeIndexesInPath() {
            // Python: source = [1, [2, 3, [4, 5, 6]], 7]
            var innerList = new ArrayList<>(List.of(4, 5, 6));
            var middleList = new ArrayList<Object>(List.of(2, 3, innerList));
            var source = new ArrayList<Object>(List.of(1, middleList, 7));
            
            var result = SessionUtils.rootToIndex(List.of(1, -1, -1), source, false);
            // Python: assert result == (2, source[1][2])  # source[1][2][2] = 6
            //         assert result[1][result[0]] == 6
            assertEquals(2, result.key());
            @SuppressWarnings("unchecked")
            var container = (List<Object>) result.container();
            assertEquals(6, container.get((int) result.key()));
        }
        
        @Test
        @DisplayName("Test 18: Boundary case - index 0 with empty list")
        void testBoundaryCaseIndex0WithEmptyList() {
            var source = new ArrayList<Object>();
            var result = SessionUtils.rootToIndex(List.of(0), source, true);
            // Python: assert result == (0, source)
            //         assert source == [{}]
            assertEquals(0, result.key());
            assertSame(source, result.container());
            assertEquals(1, source.size());
        }
        
        @Test
        @DisplayName("Test 20: Verify intermediate containers are lists, not dicts")
        void testIntermediateContainersAreLists() {
            var source = new ArrayList<Object>();
            SessionUtils.rootToIndex(List.of(1, 2, 3), source, true);
            // Python: assert isinstance(source[1], list)
            //         assert isinstance(source[1][2], list)
            //         assert isinstance(source[1][2][3], dict)  # Only the final container should be dict
            assertInstanceOf(List.class, source.get(1));
            @SuppressWarnings("unchecked")
            var level1 = (List<Object>) source.get(1);
            assertInstanceOf(List.class, level1.get(2));
            @SuppressWarnings("unchecked")
            var level2 = (List<Object>) level1.get(2);
            assertInstanceOf(Map.class, level2.get(3));
        }
        
        @Test
        @DisplayName("Test 21: Access existing nested structure without modification")
        void testAccessExistingNestedStructure() {
            // Python: source = [1, [2, [3, 4]], 5]
            var innerList = new ArrayList<>(List.of(3, 4));
            var middleList = new ArrayList<Object>(List.of(2, innerList));
            var source = new ArrayList<Object>(List.of(1, middleList, 5));
            
            var result = SessionUtils.rootToIndex(List.of(1, 1, 0), source, false);
            // Python: assert result == (0, source[1][1])
            //         assert result[1][result[0]] == 3
            assertEquals(0, result.key());
            assertSame(innerList, result.container());
            @SuppressWarnings("unchecked")
            var container = (List<Object>) result.container();
            assertEquals(3, container.get((int) result.key()));
            
            // Verify source structure unchanged
            assertEquals(3, source.size());
        }
        
        @Test
        @DisplayName("Test 22: Complex real-world like structure")
        void testComplexRealWorldLikeStructure() {
            // Python: filesystem_like = [
            //     "root",
            //     ["users", ["alice", ["docs", "pics", "music"], "bob", ["work", "personal"]]],
            //     ["system", ["config", "logs"]]
            // ]
            var aliceFiles = new ArrayList<>(List.of("docs", "pics", "music"));
            var bobFiles = new ArrayList<>(List.of("work", "personal"));
            var usersContent = new ArrayList<Object>(List.of("alice", aliceFiles, "bob", bobFiles));
            var users = new ArrayList<Object>(List.of("users", usersContent));
            var system = new ArrayList<>(List.of("system", List.of("config", "logs")));
            var filesystemLike = new ArrayList<Object>(List.of("root", users, system));
            
            // Navigate to alice's music folder
            var result = SessionUtils.rootToIndex(List.of(1, 1, 1, 2), filesystemLike, false);
            // Python: assert result == (2, filesystem_like[1][1][1])
            //         assert result[1][result[0]] == "music"
            assertEquals(2, result.key());
            assertSame(aliceFiles, result.container());
            @SuppressWarnings("unchecked")
            var container = (List<Object>) result.container();
            assertEquals("music", container.get((int) result.key()));
        }
        
        @Test
        @DisplayName("Test 23: Partial path creation")
        void testPartialPathCreation() {
            // Python: source = [1, [2, 3]]  # Existing structure
            var innerList = new ArrayList<Object>(List.of(2, 3));
            var source = new ArrayList<Object>(List.of(1, innerList));
            
            var result = SessionUtils.rootToIndex(List.of(1, 5, 2), source, true);
            // Python: assert result == (2, source[1][5])
            assertEquals(2, result.key());
            
            // Verify the created structure
            // Python: assert source[1][5] == [None, None, {}]
            @SuppressWarnings("unchecked")
            var expandedInner = (List<Object>) source.get(1);
            assertTrue(expandedInner.size() >= 6);
            @SuppressWarnings("unchecked")
            var createdList = (List<Object>) expandedInner.get(5);
            assertEquals(new HashMap<>(), createdList.get(2));
        }
    }
    
    /**
     * Python: test_agent_session
     * 测试AgentSession的状态操作
     * 注意：Python使用Session类（来自agent.py），有_inner属性
     * Python: agent_session = getattr(Session(session_id="abc"), "_inner")
     * 实际调用的是 TaskSession -> self._inner.state().update(data)
     * 其中 self._inner 是 AgentSession，state() 返回 StateCollection
     */
    @Test
    @DisplayName("test_agent_session - agent session state operations")
    void testAgentSession() {
        // Python: agent_session = getattr(Session(session_id="abc"), "_inner")
        // 在Java中，我们直接使用AgentSession，其state()返回AgentStateCollection
        var agentSession = new com.openjiuwen.core.session.internal.AgentSession("abc", null);
        var state = agentSession.getState();
        
        var data = Map.of("data", Map.of("a", 1));
        // Python: agent_session.update_state({"result": data}) -> self._inner.state().update(data)
        state.update(Map.of("result", data));
        // Python: agent_session.get_state("result") -> self._inner.state().get("result")
        assertEquals(Map.of("data", Map.of("a", 1)), state.get("result"));
        
        var data2 = Map.of("data", Map.of("b", 1));
        state.update(Map.of("result", data2));
        // Python: assert agent_session.get_state("result") == {"data": {"a": 1, "b": 1}}
        @SuppressWarnings("unchecked")
        var resultState = (Map<String, Object>) state.get("result");
        @SuppressWarnings("unchecked")
        var dataMap = (Map<String, Object>) resultState.get("data");
        assertEquals(1, dataMap.get("a"));
        assertEquals(1, dataMap.get("b"));
        
        // Python: agent_session.update_state({"result": None})
        var nullUpdate = new HashMap<String, Object>();
        nullUpdate.put("result", null);
        state.update(nullUpdate);
        assertNull(state.get("result"));
        
        state.update(Map.of("result", data2));
        assertEquals(Map.of("data", Map.of("b", 1)), state.get("result"));
    }
}
