/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryStateLike and InMemoryCommitState classes.
 */
class StateBaseTest {
    
    @Nested
    @DisplayName("InMemoryStateLike Tests")
    class InMemoryStateLikeTests {
        
        private InMemoryStateLike state;
        
        @BeforeEach
        void setUp() {
            state = new InMemoryStateLike();
        }
        
        @Test
        @DisplayName("get with string key returns corresponding value")
        void testGetWithStrKey() {
            state.state = new HashMap<>(Map.of("a", 1, "b", Map.of("c", 2)));
            assertEquals(1, state.get("a"));
            assertEquals(Map.of("c", 2), state.get("b"));
        }
        
        @Test
        @DisplayName("get with nested string key")
        void testGetWithStrKeyNested() {
            state.state = new HashMap<>();
            state.state.put("a", new HashMap<>(Map.of("b", new HashMap<>(Map.of("c", 3)))));
            assertEquals(3, state.get("a.b.c"));
            assertEquals(Map.of("c", 3), state.get("a.b"));
        }
        
        @Test
        @DisplayName("get with list key (schema form)")
        void testGetWithListKey() {
            state.state = new HashMap<>(Map.of("a", 1, "b", 2, "c", 3));
            Object result = state.get(List.of("${a}", "${b}"));
            assertEquals(List.of(1, 2), result);
        }
        
        @Test
        @DisplayName("get with dict key (schema form)")
        void testGetWithDictKey() {
            state.state = new HashMap<>(Map.of("a", 1, "b", 2));
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) state.get(Map.of("x", "${a}", "y", "${b}"));
            assertEquals(1, result.get("x"));
            assertEquals(2, result.get("y"));
        }
        
        @Test
        @DisplayName("get with nonexistent key returns null")
        void testGetNonexistentKeyReturnsNull() {
            state.state = new HashMap<>(Map.of("a", 1));
            assertNull(state.get("nonexistent"));
            assertNull(state.get("a.b.c"));
        }
        
        @Test
        @DisplayName("get returns deep copy")
        void testGetReturnsDeepCopy() {
            state.state = new HashMap<>();
            state.state.put("a", new HashMap<>(Map.of("b", 1)));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) state.get("a");
            result.put("b", 999);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> original = (Map<String, Object>) state.state.get("a");
            assertEquals(1, original.get("b"));
        }
        
        @Test
        @DisplayName("getByPrefix extracts value from nested prefix path")
        void testGetByPrefix() {
            state.state = new HashMap<>();
            state.state.put("node1", new HashMap<>(Map.of("x", 1, "y", 2)));
            state.state.put("node2", new HashMap<>(Map.of("x", 3)));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) state.getByPrefix(
                Map.of("a", "${x}", "b", "${y}"), "node1");
            assertEquals(1, result.get("a"));
            assertEquals(2, result.get("b"));
        }
        
        @Test
        @DisplayName("getByPrefix with nonexistent prefix returns null")
        void testGetByPrefixNonexistent() {
            state.state = new HashMap<>();
            state.state.put("node1", new HashMap<>(Map.of("x", 1)));
            
            Object result = state.getByPrefix("x", "nonexistent");
            assertNull(result);
        }
        
        @Test
        @DisplayName("getByTransformer calls transformer with internal state")
        void testGetByTransformer() {
            state.state = new HashMap<>(Map.of("a", 1, "b", 2));
            
            Transformer<Integer> transformer = s -> {
                Integer a = (Integer) s.get("a");
                Integer b = (Integer) s.get("b");
                return (a != null ? a : 0) + (b != null ? b : 0);
            };
            
            Integer result = state.getByTransformer(transformer);
            assertEquals(3, result);
        }
        
        @Test
        @DisplayName("update with nested dict uses merge semantics")
        void testUpdateWithNestedDict() {
            state.state = new HashMap<>();
            state.state.put("a", new HashMap<>(Map.of("b", 1)));
            
            state.update(Map.of("a.c", 2));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> a = (Map<String, Object>) state.state.get("a");
            assertEquals(1, a.get("b"));
            assertEquals(2, a.get("c"));
        }
        
        @Test
        @DisplayName("update with empty dict keeps state unchanged")
        void testUpdateWithEmptyDict() {
            state.state = new HashMap<>(Map.of("a", 1));
            state.update(Map.of());
            assertEquals(Map.of("a", 1), state.state);
        }
        
        @Test
        @DisplayName("update with null value deletes key")
        void testUpdateWithNullValueDeletesKey() {
            state.state = new HashMap<>(Map.of("a", 1, "b", 2));
            
            Map<String, Object> update = new HashMap<>();
            update.put("a", null);
            state.update(update);
            
            assertNull(state.state.get("a"));
            assertEquals(2, state.state.get("b"));
        }
        
        @Test
        @DisplayName("getState returns deep copy")
        void testGetStateReturnsDeepCopy() {
            state.state = new HashMap<>();
            state.state.put("a", new HashMap<>(Map.of("b", 1)));
            
            Map<String, Object> result = state.getState();
            @SuppressWarnings("unchecked")
            Map<String, Object> resultA = (Map<String, Object>) result.get("a");
            resultA.put("b", 999);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> originalA = (Map<String, Object>) state.state.get("a");
            assertEquals(1, originalA.get("b"));
        }
        
        @Test
        @DisplayName("setState replaces state")
        void testSetStateReplacesState() {
            state.state = new HashMap<>(Map.of("old", 1));
            state.setState(new HashMap<>(Map.of("new", 2)));
            assertEquals(Map.of("new", 2), state.state);
        }
        
        @Test
        @DisplayName("setState with null keeps original")
        void testSetStateWithNullKeepsOriginal() {
            state.state = new HashMap<>(Map.of("a", 1));
            state.setState(null);
            assertEquals(Map.of("a", 1), state.state);
        }
        
        @Test
        @DisplayName("setState with empty dict keeps original")
        void testSetStateWithEmptyDictKeepsOriginal() {
            state.state = new HashMap<>(Map.of("a", 1));
            state.setState(Map.of());
            assertEquals(Map.of("a", 1), state.state);
        }
    }
    
    @Nested
    @DisplayName("InMemoryCommitState Tests")
    class InMemoryCommitStateTests {
        
        private InMemoryCommitState commitState;
        
        @BeforeEach
        void setUp() {
            commitState = new InMemoryCommitState();
        }
        
        @Test
        @DisplayName("update direct call raises exception")
        void testUpdateDirectCallRaisesException() {
            JiuWenBaseException exception = assertThrows(JiuWenBaseException.class,
                () -> commitState.update(Map.of("a", 1)));
            assertEquals(-1, exception.getErrorCode());
        }
        
        @Test
        @DisplayName("updateById with null nodeId raises exception")
        void testUpdateByIdWithNullNodeIdRaises() {
            JiuWenBaseException exception = assertThrows(JiuWenBaseException.class,
                () -> commitState.updateById(null, Map.of("a", 1)));
            assertEquals(1, exception.getErrorCode());
        }
        
        @Test
        @DisplayName("updateById appends to updates list")
        void testUpdateByIdAppendsToUpdatesList() {
            commitState.updateById("node1", Map.of("a", 1));
            commitState.updateById("node1", Map.of("b", 2));
            
            var updates = commitState.getUpdates();
            assertEquals(2, updates.get("node1").size());
            assertEquals(Map.of("a", 1), updates.get("node1").get(0));
            assertEquals(Map.of("b", 2), updates.get("node1").get(1));
        }
        
        @Test
        @DisplayName("updateById with multiple nodes keeps them separate")
        void testUpdateByIdMultipleNodes() {
            commitState.updateById("node1", Map.of("a", 1));
            commitState.updateById("node2", Map.of("b", 2));
            
            var updates = commitState.getUpdates();
            assertTrue(updates.containsKey("node1"));
            assertTrue(updates.containsKey("node2"));
            assertEquals(Map.of("a", 1), updates.get("node1").get(0));
            assertEquals(Map.of("b", 2), updates.get("node2").get(0));
        }
        
        @Test
        @DisplayName("commit global merges all updates")
        void testCommitGlobalMergesAllUpdates() {
            commitState.updateById("node1", Map.of("a", 1));
            commitState.updateById("node2", Map.of("b", 2));
            commitState.commit();
            
            assertEquals(1, commitState.get("a"));
            assertEquals(2, commitState.get("b"));
            assertTrue(commitState.getUpdates().isEmpty());
        }
        
        @Test
        @DisplayName("commit node level merges specific node")
        void testCommitNodeLevelMergesSpecificNode() {
            commitState.updateById("node1", Map.of("a", 1));
            commitState.updateById("node2", Map.of("b", 2));
            commitState.commit("node1");
            
            assertEquals(1, commitState.get("a"));
            assertNull(commitState.get("b"));
            
            var updates = commitState.getUpdates();
            assertTrue(updates.get("node1").isEmpty());
            assertEquals(1, updates.get("node2").size());
        }
        
        @Test
        @DisplayName("commit empty node updates no error")
        void testCommitEmptyNodeUpdatesNoError() {
            assertDoesNotThrow(() -> commitState.commit("nonexistent_node"));
        }
        
        @Test
        @DisplayName("rollback clears node updates")
        void testRollbackClearsNodeUpdates() {
            commitState.updateById("node1", Map.of("a", 1));
            commitState.updateById("node1", Map.of("b", 2));
            commitState.rollback("node1");
            
            assertTrue(commitState.getUpdates().get("node1").isEmpty());
        }
        
        @Test
        @DisplayName("rollback keeps stable state")
        void testRollbackKeepsStableState() {
            commitState.updateById("node1", Map.of("a", 1));
            commitState.commit();
            commitState.updateById("node1", Map.of("b", 2));
            commitState.rollback("node1");
            
            assertEquals(1, commitState.get("a"));
            assertNull(commitState.get("b"));
        }
        
        @Test
        @DisplayName("getUpdates returns current updates")
        void testGetUpdatesReturnsCurrent() {
            commitState.updateById("node1", Map.of("a", 1));
            
            var updates = commitState.getUpdates();
            assertTrue(updates.containsKey("node1"));
            assertEquals(Map.of("a", 1), updates.get("node1").get(0));
        }
        
        @Test
        @DisplayName("setUpdates replaces updates")
        void testSetUpdatesReplacesUpdates() {
            commitState.updateById("node1", Map.of("a", 1));
            
            Map<String, java.util.List<Map<String, Object>>> newUpdates = new HashMap<>();
            newUpdates.put("node2", java.util.List.of(Map.of("b", 2)));
            commitState.setUpdates(newUpdates);
            
            assertEquals(newUpdates, commitState.getUpdates());
        }
        
        @Test
        @DisplayName("setUpdates with null keeps original")
        void testSetUpdatesWithNullKeepsOriginal() {
            commitState.updateById("node1", Map.of("a", 1));
            var original = new HashMap<>(commitState.getUpdates());
            commitState.setUpdates(null);
            assertEquals(original, commitState.getUpdates());
        }
        
        @Test
        @DisplayName("getState returns stable state")
        void testGetStateReturnsStableState() {
            commitState.updateById("node1", Map.of("a", 1));
            commitState.commit();
            
            assertEquals(Map.of("a", 1), commitState.getState());
        }
        
        @Test
        @DisplayName("setState replaces stable state")
        void testSetStateReplacesStableState() {
            commitState.updateById("node1", Map.of("a", 1));
            commitState.commit();
            commitState.setState(new HashMap<>(Map.of("b", 2)));
            
            assertEquals(2, commitState.get("b"));
            assertNull(commitState.get("a"));
        }
        
        @Test
        @DisplayName("getByTransformer works with underlying state")
        void testGetByTransformer() {
            commitState.updateById("node1", Map.of("a", 1, "b", 2));
            commitState.commit();
            
            Transformer<Integer> transformer = s -> {
                Integer a = (Integer) s.get("a");
                Integer b = (Integer) s.get("b");
                return (a != null ? a : 0) + (b != null ? b : 0);
            };
            
            Integer result = commitState.getByTransformer(transformer);
            assertEquals(3, result);
        }
        
        @Test
        @DisplayName("getByPrefix delegates to underlying state")
        void testGetByPrefix() {
            commitState.updateById("node1", Map.of("prefix", Map.of("x", 1, "y", 2)));
            commitState.commit();
            
            Object result = commitState.getByPrefix("x", "prefix");
            assertEquals(1, result);
        }
        
        @Test
        @DisplayName("updateById makes deep copy, original data is isolated")
        void testUpdateDeepCopyIsolation() {
            Map<String, Object> nested = new HashMap<>();
            nested.put("value", 1);
            Map<String, Object> data = new HashMap<>();
            data.put("nested", nested);
            
            commitState.updateById("node1", data);
            nested.put("value", 999);
            
            var updates = commitState.getUpdates();
            @SuppressWarnings("unchecked")
            Map<String, Object> storedNested = (Map<String, Object>) updates.get("node1").get(0).get("nested");
            assertEquals(1, storedNested.get("value"));
        }
    }
}

