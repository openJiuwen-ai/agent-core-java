/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.store;

import com.openjiuwen.core.session.MemoryStore;
import com.openjiuwen.core.session.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryStore class.
 * 
 * <p>Converted from Python: test_store.py</p>
 * <p>Python测试类: TestMemoryStore</p>
 * <p>Python测试方法数: 18</p>
 */
class MemoryStoreTest {
    
    private MemoryStore store;
    
    @BeforeEach
    void setUp() {
        store = new MemoryStore();
    }
    
    @Test
    @DisplayName("construction initializes empty data")
    void testConstructionInitializesEmptyData() {
        // Python: assert store._data == {}
        assertEquals(new HashMap<>(), store.getData());
    }
    
    @Test
    @DisplayName("write simple dict")
    void testWriteSimpleDict() {
        // Python: store.write({"key1": "value1", "key2": "value2"})
        store.write(Map.of("key1", "value1", "key2", "value2"));
        assertEquals("value1", store.read("key1"));
        assertEquals("value2", store.read("key2"));
    }
    
    @Test
    @DisplayName("write nested dict")
    void testWriteNestedDict() {
        // Python: store.write({"level1.level2": "nested_value"})
        store.write(Map.of("level1.level2", "nested_value"));
        assertEquals("nested_value", store.read("level1.level2"));
    }
    
    @Test
    @DisplayName("write updates existing keys")
    void testWriteUpdatesExistingKeys() {
        // Python: store.write({"key": "value1"})
        //         store.write({"key": "value2"})
        store.write(Map.of("key", "value1"));
        store.write(Map.of("key", "value2"));
        assertEquals("value2", store.read("key"));
    }
    
    @Test
    @DisplayName("write merges nested dicts")
    void testWriteMergesNestedDicts() {
        // Python: store.write({"nested": {"a": 1}})
        //         store.write({"nested": {"b": 2}})
        store.write(Map.of("nested", Map.of("a", 1)));
        store.write(Map.of("nested", Map.of("b", 2)));
        assertEquals(1, store.read("nested.a"));
        assertEquals(2, store.read("nested.b"));
    }
    
    @Test
    @DisplayName("read with string key")
    void testReadWithStringKey() {
        // Python: store.write({"key": "value"})
        //         assert store.read("key") == "value"
        store.write(Map.of("key", "value"));
        assertEquals("value", store.read("key"));
    }
    
    @Test
    @DisplayName("read with nested string key")
    void testReadWithNestedStringKey() {
        // Python: store.write({"level1": {"level2": {"level3": "deep_value"}}})
        //         assert store.read("level1.level2.level3") == "deep_value"
        store.write(Map.of("level1", Map.of("level2", Map.of("level3", "deep_value"))));
        assertEquals("deep_value", store.read("level1.level2.level3"));
    }
    
    @Test
    @DisplayName("read with dict schema")
    void testReadWithDictSchema() {
        // Python: store.write({"a": 1, "b": 2, "c": 3})
        //         result = store.read({"x": "${a}", "y": "${b}"})
        //         assert result == {"x": 1, "y": 2}
        store.write(Map.of("a", 1, "b", 2, "c", 3));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) store.read(Map.of("x", "${a}", "y", "${b}"));
        assertEquals(1, result.get("x"));
        assertEquals(2, result.get("y"));
    }
    
    @Test
    @DisplayName("read with list schema")
    void testReadWithListSchema() {
        // Python: store.write({"a": 1, "b": 2})
        //         result = store.read(["${a}", "${b}"])
        //         assert result == [1, 2]
        store.write(Map.of("a", 1, "b", 2));
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) store.read(List.of("${a}", "${b}"));
        assertEquals(List.of(1, 2), result);
    }
    
    @Test
    @DisplayName("read nonexistent key returns null")
    void testReadNonexistentKeyReturnsNone() {
        // Python: assert store.read("nonexistent") is None
        assertNull(store.read("nonexistent"));
    }
    
    @Test
    @DisplayName("read partial nested path returns null")
    void testReadPartialNestedPathReturnsNone() {
        // Python: store.write({"level1": {"level2": "value"}})
        //         assert store.read("level1.nonexistent") is None
        store.write(Map.of("level1", Map.of("level2", "value")));
        assertNull(store.read("level1.nonexistent"));
    }
    
    @Test
    @DisplayName("write and read with list index")
    void testWriteAndReadWithListIndex() {
        // Python: store.write({"items": [1, 2, 3]})
        //         assert store.read("items[0]") == 1
        //         assert store.read("items[1]") == 2
        //         assert store.read("items[2]") == 3
        List<Integer> items = new ArrayList<>();
        items.add(1);
        items.add(2);
        items.add(3);
        store.write(Map.of("items", items));
        assertEquals(1, store.read("items[0]"));
        assertEquals(2, store.read("items[1]"));
        assertEquals(3, store.read("items[2]"));
    }
    
    @Test
    @DisplayName("write null value deletes key")
    void testWriteNoneValueDeletesKey() {
        // Python: store.write({"key": "value"})
        //         store.write({"key": None})
        //         assert store.read("key") is None
        store.write(Map.of("key", "value"));
        Map<String, Object> update = new HashMap<>();
        update.put("key", null);
        store.write(update);
        assertNull(store.read("key"));
    }
    
    @Test
    @DisplayName("read with null key returns null")
    void testReadWithNoneKeyReturnsNone() {
        // Python: store.write({"key": "value"})
        //         assert store.read(None) is None
        store.write(Map.of("key", "value"));
        assertNull(store.read(null));
    }
    
    @Test
    @DisplayName("complex nested structure")
    void testComplexNestedStructure() {
        // Python: store.write({
        //     "users": {
        //         "user1": {"name": "Alice", "age": 30},
        //         "user2": {"name": "Bob", "age": 25},
        //     }
        // })
        // assert store.read("users.user1.name") == "Alice"
        // assert store.read("users.user2.age") == 25
        store.write(Map.of(
            "users", Map.of(
                "user1", Map.of("name", "Alice", "age", 30),
                "user2", Map.of("name", "Bob", "age", 25)
            )
        ));
        assertEquals("Alice", store.read("users.user1.name"));
        assertEquals(25, store.read("users.user2.age"));
    }
    
    @Test
    @DisplayName("is Store instance")
    void testIsStoreInstance() {
        // Python: assert isinstance(store, Store)
        assertInstanceOf(Store.class, store);
    }
    
    @Test
    @DisplayName("multiple writes accumulate")
    void testMultipleWritesAccumulate() {
        // Python: store.write({"a": 1})
        //         store.write({"b": 2})
        //         store.write({"c": 3})
        //         assert store.read("a") == 1
        //         assert store.read("b") == 2
        //         assert store.read("c") == 3
        store.write(Map.of("a", 1));
        store.write(Map.of("b", 2));
        store.write(Map.of("c", 3));
        assertEquals(1, store.read("a"));
        assertEquals(2, store.read("b"));
        assertEquals(3, store.read("c"));
    }
    
    @Test
    @DisplayName("read with mixed schema")
    void testReadWithMixedSchema() {
        // Python: store.write({"x": 10, "y": 20})
        //         result = store.read({"ref_value": "${x}", "literal": "static"})
        //         assert result == {"ref_value": 10, "literal": "static"}
        store.write(Map.of("x", 10, "y", 20));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) store.read(
            Map.of("ref_value", "${x}", "literal", "static")
        );
        assertEquals(10, result.get("ref_value"));
        assertEquals("static", result.get("literal"));
    }
}

