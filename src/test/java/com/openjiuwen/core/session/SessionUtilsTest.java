/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SessionUtils class.
 */
class SessionUtilsTest {
    
    // ========== isRefPath Tests ==========
    
    @Test
    @DisplayName("isRefPath returns true for valid reference path")
    void testIsRefPathValid() {
        assertTrue(SessionUtils.isRefPath("${path}"));
        assertTrue(SessionUtils.isRefPath("${a.b.c}"));
        assertTrue(SessionUtils.isRefPath("${some_key}"));
    }
    
    @Test
    @DisplayName("isRefPath returns false for invalid paths")
    void testIsRefPathInvalid() {
        assertFalse(SessionUtils.isRefPath(null));
        assertFalse(SessionUtils.isRefPath(""));
        assertFalse(SessionUtils.isRefPath("path"));
        assertFalse(SessionUtils.isRefPath("${}"));  // Empty
        assertFalse(SessionUtils.isRefPath("${a")); // Missing closing brace
        assertFalse(SessionUtils.isRefPath("a}")); // Missing opening
    }
    
    // ========== extractOriginKey Tests ==========
    
    @Test
    @DisplayName("extractOriginKey extracts key from reference path")
    void testExtractOriginKey() {
        assertEquals("start123.p2", SessionUtils.extractOriginKey("${start123.p2}"));
        assertEquals("simple", SessionUtils.extractOriginKey("${simple}"));
        assertEquals("a.b.c", SessionUtils.extractOriginKey("${a.b.c}"));
    }
    
    @Test
    @DisplayName("extractOriginKey returns input for non-reference paths")
    void testExtractOriginKeyNonRef() {
        assertEquals("path", SessionUtils.extractOriginKey("path"));
        assertEquals("a.b.c", SessionUtils.extractOriginKey("a.b.c"));
        assertNull(SessionUtils.extractOriginKey(null));
    }
    
    // ========== splitNestedPath Tests ==========
    
    @Test
    @DisplayName("splitNestedPath splits simple nested path")
    void testSplitNestedPathSimple() {
        List<Object> result = SessionUtils.splitNestedPath("a.b.c");
        assertEquals(List.of("a", "b", "c"), result);
    }
    
    @Test
    @DisplayName("splitNestedPath splits path with array index")
    void testSplitNestedPathWithIndex() {
        List<Object> result = SessionUtils.splitNestedPath("a.b[0].c");
        assertEquals(List.of("a", "b", 0, "c"), result);
    }
    
    @Test
    @DisplayName("splitNestedPath handles multiple array indexes")
    void testSplitNestedPathMultipleIndexes() {
        List<Object> result = SessionUtils.splitNestedPath("a[0][1]");
        assertEquals(List.of("a", 0, 1), result);
    }
    
    @Test
    @DisplayName("splitNestedPath handles string key in brackets")
    void testSplitNestedPathStringKey() {
        List<Object> result = SessionUtils.splitNestedPath("a.b['key']");
        assertEquals(List.of("a", "b", "key"), result);
    }
    
    @Test
    @DisplayName("splitNestedPath handles negative index")
    void testSplitNestedPathNegativeIndex() {
        List<Object> result = SessionUtils.splitNestedPath("a[-1]");
        assertEquals(List.of("a", -1), result);
    }
    
    @Test
    @DisplayName("splitNestedPath returns empty for simple key")
    void testSplitNestedPathSimpleKey() {
        List<Object> result = SessionUtils.splitNestedPath("simple");
        assertEquals(List.of(), result);
    }
    
    // ========== getValueByNestedPath Tests ==========
    
    @Test
    @DisplayName("getValueByNestedPath gets simple value")
    void testGetValueByNestedPathSimple() {
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        
        assertEquals("value", SessionUtils.getValueByNestedPath("key", data));
    }
    
    @Test
    @DisplayName("getValueByNestedPath gets nested value")
    void testGetValueByNestedPathNested() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("c", 3);
        Map<String, Object> middle = new HashMap<>();
        middle.put("b", inner);
        Map<String, Object> data = new HashMap<>();
        data.put("a", middle);
        
        assertEquals(3, SessionUtils.getValueByNestedPath("a.b.c", data));
    }
    
    @Test
    @DisplayName("getValueByNestedPath gets value from array")
    void testGetValueByNestedPathArray() {
        List<Object> list = new ArrayList<>();
        list.add("first");
        list.add("second");
        Map<String, Object> data = new HashMap<>();
        data.put("items", list);
        
        assertEquals("first", SessionUtils.getValueByNestedPath("items[0]", data));
        assertEquals("second", SessionUtils.getValueByNestedPath("items[1]", data));
    }
    
    @Test
    @DisplayName("getValueByNestedPath returns null for nonexistent key")
    void testGetValueByNestedPathNonexistent() {
        Map<String, Object> data = new HashMap<>();
        data.put("a", 1);
        
        assertNull(SessionUtils.getValueByNestedPath("nonexistent", data));
        assertNull(SessionUtils.getValueByNestedPath("a.b.c", data));
    }
    
    // ========== getBySchema Tests ==========
    
    @Test
    @DisplayName("getBySchema with string reference path")
    void testGetBySchemaStringRef() {
        Map<String, Object> data = new HashMap<>();
        data.put("a", 1);
        data.put("b", 2);
        
        assertEquals(1, SessionUtils.getBySchema("${a}", data));
        assertEquals(2, SessionUtils.getBySchema("${b}", data));
    }
    
    @Test
    @DisplayName("getBySchema with list schema")
    void testGetBySchemaList() {
        Map<String, Object> data = new HashMap<>();
        data.put("a", 1);
        data.put("b", 2);
        
        List<String> schema = List.of("${a}", "${b}");
        Object result = SessionUtils.getBySchema(schema, data);
        
        assertEquals(List.of(1, 2), result);
    }
    
    @Test
    @DisplayName("getBySchema with map schema")
    void testGetBySchemaMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("a", 1);
        data.put("b", 2);
        
        Map<String, String> schema = Map.of("x", "${a}", "y", "${b}");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) SessionUtils.getBySchema(schema, data);
        
        assertEquals(1, result.get("x"));
        assertEquals(2, result.get("y"));
    }
    
    @Test
    @DisplayName("getBySchema with nested prefix")
    void testGetBySchemaWithPrefix() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("x", 1);
        inner.put("y", 2);
        Map<String, Object> data = new HashMap<>();
        data.put("node1", inner);
        
        Map<String, String> schema = Map.of("a", "${x}", "b", "${y}");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) SessionUtils.getBySchema(schema, data, "node1");
        
        assertEquals(1, result.get("a"));
        assertEquals(2, result.get("b"));
    }
    
    // ========== updateDict Tests ==========
    
    @Test
    @DisplayName("updateDict updates simple key")
    void testUpdateDictSimple() {
        Map<String, Object> source = new HashMap<>();
        source.put("a", 1);
        
        Map<String, Object> update = new HashMap<>();
        update.put("b", 2);
        
        SessionUtils.updateDict(update, source);
        
        assertEquals(1, source.get("a"));
        assertEquals(2, source.get("b"));
    }
    
    @Test
    @DisplayName("updateDict updates nested key")
    void testUpdateDictNested() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("b", 1);
        Map<String, Object> source = new HashMap<>();
        source.put("a", inner);
        
        Map<String, Object> update = new HashMap<>();
        update.put("a.c", 2);
        
        SessionUtils.updateDict(update, source);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) source.get("a");
        assertEquals(1, a.get("b"));
        assertEquals(2, a.get("c"));
    }
    
    @Test
    @DisplayName("updateDict deletes key with null value")
    void testUpdateDictDeleteNull() {
        Map<String, Object> source = new HashMap<>();
        source.put("a", 1);
        source.put("b", 2);
        
        Map<String, Object> update = new HashMap<>();
        update.put("a", null);
        
        SessionUtils.updateDict(update, source);
        
        assertNull(source.get("a"));
        assertEquals(2, source.get("b"));
    }
    
    @Test
    @DisplayName("updateDict ignores delete when ignoreDelete is true")
    void testUpdateDictIgnoreDelete() {
        Map<String, Object> source = new HashMap<>();
        source.put("a", 1);
        
        Map<String, Object> update = new HashMap<>();
        update.put("a", null);
        
        SessionUtils.updateDict(update, source, true);
        
        // Key should still exist (though value is null)
        assertTrue(source.containsKey("a"));
    }
    
    // ========== deepCopyMap Tests ==========
    
    @Test
    @DisplayName("deepCopyMap creates independent copy")
    void testDeepCopyMap() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("b", 1);
        Map<String, Object> source = new HashMap<>();
        source.put("a", inner);
        
        Map<String, Object> copy = SessionUtils.deepCopyMap(source);
        
        // Modify copy
        @SuppressWarnings("unchecked")
        Map<String, Object> copyInner = (Map<String, Object>) copy.get("a");
        copyInner.put("b", 999);
        
        // Original should be unchanged
        @SuppressWarnings("unchecked")
        Map<String, Object> originalInner = (Map<String, Object>) source.get("a");
        assertEquals(1, originalInner.get("b"));
    }
    
    @Test
    @DisplayName("deepCopyMap handles null")
    void testDeepCopyMapNull() {
        assertNull(SessionUtils.deepCopyMap(null));
    }
    
    // ========== rootToIndex Tests ==========
    
    @Test
    @DisplayName("rootToIndex navigates to index")
    void testRootToIndex() {
        List<Object> source = new ArrayList<>();
        source.add("first");
        source.add("second");
        
        SessionUtils.PathResult result = SessionUtils.rootToIndex(List.of(1), source, false);
        
        assertEquals(1, result.key());
        assertSame(source, result.container());
    }
    
    @Test
    @DisplayName("rootToIndex creates missing elements")
    void testRootToIndexCreateIfAbsent() {
        List<Object> source = new ArrayList<>();
        
        SessionUtils.PathResult result = SessionUtils.rootToIndex(List.of(2), source, true);
        
        assertEquals(2, result.key());
        assertEquals(3, source.size()); // Extended to accommodate index 2
    }
    
    @Test
    @DisplayName("rootToIndex throws for deep nesting")
    void testRootToIndexDeepNesting() {
        List<Object> source = new ArrayList<>();
        List<Integer> deepIndexes = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        
        assertThrows(IllegalArgumentException.class, () -> 
            SessionUtils.rootToIndex(deepIndexes, source, false));
    }
    
    @Test
    @DisplayName("rootToIndex handles negative index")
    void testRootToIndexNegative() {
        List<Object> source = new ArrayList<>();
        source.add("first");
        source.add("second");
        source.add("third");
        
        SessionUtils.PathResult result = SessionUtils.rootToIndex(List.of(-1), source, false);
        
        assertEquals(2, result.key()); // -1 becomes 2 (last index)
    }
    
    // ========== EndFrame Tests ==========
    
    @Test
    @DisplayName("EndFrame record works correctly")
    void testEndFrame() {
        EndFrame frame = new EndFrame("test_source");
        assertEquals("test_source", frame.source());
        
        EndFrame frame2 = new EndFrame("test_source");
        assertEquals(frame, frame2);
    }
    
    // ========== SessionConstants Tests ==========
    
    @Test
    @DisplayName("SessionConstants has expected values")
    void testSessionConstants() {
        assertEquals("_execute_timeout", SessionConstants.WORKFLOW_EXECUTE_TIMEOUT);
        assertEquals("_stream_frame_timeout", SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT);
        assertEquals(1000, SessionConstants.LOOP_NUMBER_MAX_LIMIT_DEFAULT);
        assertEquals("WORKFLOW_EXECUTE_TIMEOUT", SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY);
    }
}

