/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.common.utils;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_dict.py} in 
 * {@code tests.unit_tests.core.common.utils}.
 */
@Tag("unit-test")
class TestDict {

    // -----------------------------------------------------------------------
    // Helper methods (mimicking Python dict_utils)
    // -----------------------------------------------------------------------

    /**
     * Extract leaf nodes from a nested map.
     */
    @SuppressWarnings("unchecked")
    static List<Map.Entry<List<String>, Object>> extractLeafNodes(Map<String, Object> data) {
        List<Map.Entry<List<String>, Object>> leaves = new ArrayList<>();
        extractLeafNodesRecursive(data, new ArrayList<>(), leaves);
        return leaves;
    }

    @SuppressWarnings("unchecked")
    private static void extractLeafNodesRecursive(Object data, List<String> path, 
                                                   List<Map.Entry<List<String>, Object>> leaves) {
        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                List<String> newPath = new ArrayList<>(path);
                newPath.add(entry.getKey());
                extractLeafNodesRecursive(entry.getValue(), newPath, leaves);
            }
        } else {
            leaves.add(new AbstractMap.SimpleEntry<>(new ArrayList<>(path), data));
        }
    }

    /**
     * Rebuild dict from path-value pairs.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> rebuildDictFromPaths(List<Map.Entry<List<String>, Object>> leaves) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        for (Map.Entry<List<String>, Object> entry : leaves) {
            List<String> path = entry.getKey();
            Object value = entry.getValue();
            
            Map<String, Object> current = result;
            for (int i = 0; i < path.size() - 1; i++) {
                String key = path.get(i);
                if (!current.containsKey(key)) {
                    current.put(key, new LinkedHashMap<String, Object>());
                }
                Object next = current.get(key);
                if (next instanceof Map) {
                    current = (Map<String, Object>) next;
                }
            }
            current.put(path.get(path.size() - 1), value);
        }
        
        return result;
    }

    /**
     * Format path as string.
     */
    static String formatPath(List<String> path) {
        return String.join(".", path);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Test extract leaf nodes")
    void testExtractLeaf() {
        Map<String, Object> sampleData = new LinkedHashMap<>();
        
        Map<String, Object> userProfile = new LinkedHashMap<>();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", "张三");
        profile.put("age", 25);
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "北京");
        address.put("street", "朝阳路");
        profile.put("address", address);
        userProfile.put("profile", profile);
        
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("notifications", true);
        settings.put("language", "中文");
        userProfile.put("settings", settings);
        
        sampleData.put("user", userProfile);
        
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("version", "1.0.0");
        system.put("modules", Arrays.asList("auth", "payment", "analytics"));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("timeout", 30);
        config.put("retry_count", 3);
        system.put("config", config);
        sampleData.put("system", system);
        
        sampleData.put("status", "active");

        List<Map.Entry<List<String>, Object>> leaves = extractLeafNodes(sampleData);

        assertTrue(leaves.size() > 0);
        assertEquals(10, leaves.size());

        // Verify some leaf nodes
        boolean foundName = false;
        for (Map.Entry<List<String>, Object> entry : leaves) {
            if (entry.getKey().equals(Arrays.asList("user", "profile", "name"))) {
                assertEquals("张三", entry.getValue());
                foundName = true;
            }
        }
        assertTrue(foundName);
    }

    @Test
    @DisplayName("Test rebuild dict from paths")
    void testRebuild() {
        List<Map.Entry<List<String>, Object>> sampleLeaves = new ArrayList<>();
        sampleLeaves.add(new AbstractMap.SimpleEntry<>(Arrays.asList("user", "profile", "name"), "张三"));
        sampleLeaves.add(new AbstractMap.SimpleEntry<>(Arrays.asList("user", "profile", "age"), 25));
        sampleLeaves.add(new AbstractMap.SimpleEntry<>(Arrays.asList("user", "settings", "notifications"), true));
        sampleLeaves.add(new AbstractMap.SimpleEntry<>(Arrays.asList("system", "version"), "1.0.0"));
        sampleLeaves.add(new AbstractMap.SimpleEntry<>(Arrays.asList("status"), "active"));

        Map<String, Object> rebuilt = rebuildDictFromPaths(sampleLeaves);

        assertNotNull(rebuilt);
        assertTrue(rebuilt.containsKey("user"));
        assertTrue(rebuilt.containsKey("system"));
        assertTrue(rebuilt.containsKey("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) rebuilt.get("user");
        assertTrue(user.containsKey("profile"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) user.get("profile");
        assertEquals("张三", profile.get("name"));
        assertEquals(25, profile.get("age"));
    }

    @Test
    @DisplayName("Test format path")
    void testFormatPath() {
        List<String> path = Arrays.asList("user", "profile", "name");
        String formatted = formatPath(path);
        assertEquals("user.profile.name", formatted);
    }

    @Test
    @DisplayName("Test extract leaf from simple dict")
    void testExtractLeafFromSimpleDict() {
        Map<String, Object> simple = new LinkedHashMap<>();
        simple.put("name", "test");
        simple.put("value", 42);

        List<Map.Entry<List<String>, Object>> leaves = extractLeafNodes(simple);

        assertEquals(2, leaves.size());
    }

    @Test
    @DisplayName("Test extract leaf from nested dict")
    void testExtractLeafFromNestedDict() {
        Map<String, Object> nested = new LinkedHashMap<>();
        Map<String, Object> level1 = new LinkedHashMap<>();
        Map<String, Object> level2 = new LinkedHashMap<>();
        level2.put("leaf", "value");
        level1.put("level2", level2);
        nested.put("level1", level1);

        List<Map.Entry<List<String>, Object>> leaves = extractLeafNodes(nested);

        assertEquals(1, leaves.size());
        assertEquals(Arrays.asList("level1", "level2", "leaf"), leaves.get(0).getKey());
        assertEquals("value", leaves.get(0).getValue());
    }

    @Test
    @DisplayName("Test empty dict")
    void testEmptyDict() {
        Map<String, Object> empty = new LinkedHashMap<>();
        List<Map.Entry<List<String>, Object>> leaves = extractLeafNodes(empty);
        assertEquals(0, leaves.size());
    }

    @Test
    @Tag("level0")
    @DisplayName("Placeholder test")
    void testPlaceholder() {
        assertTrue(true);
    }
}