/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.common.utils;

import com.openjiuwen.core.common.utils.DictUtils;

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

        List<Map.Entry<List<String>, Object>> leaves = DictUtils.extractLeafNodes(sampleData, null);

        assertTrue(leaves.size() > 0);
        assertEquals(13, leaves.size());

        // Verify some leaf nodes
        boolean foundName = false;
        boolean foundModule = false;
        for (Map.Entry<List<String>, Object> entry : leaves) {
            if (entry.getKey().equals(Arrays.asList("user", "profile", "name"))) {
                assertEquals("张三", entry.getValue());
                foundName = true;
            } else if (entry.getKey().equals(Arrays.asList("system", "modules", "[1]"))) {
                assertEquals("payment", entry.getValue());
                foundModule = true;
            }
        }
        assertTrue(foundName);
        assertTrue(foundModule);
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

        Map<String, Object> rebuilt = DictUtils.rebuildMapFromPaths(sampleLeaves);

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
        String formatted = DictUtils.formatPath(path);
        assertEquals("user.profile.name", formatted);
    }

    @Test
    @DisplayName("Test extract leaf from simple dict")
    void testExtractLeafFromSimpleDict() {
        Map<String, Object> simple = new LinkedHashMap<>();
        simple.put("name", "test");
        simple.put("value", 42);

        List<Map.Entry<List<String>, Object>> leaves = DictUtils.extractLeafNodes(simple, null);

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

        List<Map.Entry<List<String>, Object>> leaves = DictUtils.extractLeafNodes(nested, null);

        assertEquals(1, leaves.size());
        assertEquals(Arrays.asList("level1", "level2", "leaf"), leaves.get(0).getKey());
        assertEquals("value", leaves.get(0).getValue());
    }

    @Test
    @DisplayName("Test empty dict")
    void testEmptyDict() {
        Map<String, Object> empty = new LinkedHashMap<>();
        List<Map.Entry<List<String>, Object>> leaves = DictUtils.extractLeafNodes(empty, null);
        assertEquals(0, leaves.size());
    }

    @Test
    @DisplayName("Test format path with list index")
    void testFormatPathWithListIndex() {
        List<String> path = Arrays.asList("system", "modules", "[0]");
        String formatted = DictUtils.formatPath(path);
        assertEquals("system.modules[0]", formatted);
    }
}
