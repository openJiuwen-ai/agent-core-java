/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for DictUtils.
 * Ported from Python: tests/unit_tests/core/common/utils/test_dict.py
 */
class DictUtilsTest {

    // ==========================================================================
    // test_extract_leaf (Python: test_extract_leaf)
    // ==========================================================================
    @Nested
    @DisplayName("extractLeafNodes")
    class ExtractLeafNodesTests {

        @Test
        @DisplayName("Extract leaf nodes from a complex nested map")
        void testExtractLeafFromComplexMap() {
            Map<String, Object> sampleData = new LinkedHashMap<>();
            Map<String, Object> user = new LinkedHashMap<>();
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("name", "张三");
            profile.put("age", 25);
            Map<String, Object> address = new LinkedHashMap<>();
            address.put("city", "北京");
            address.put("street", "朝阳路");
            profile.put("address", address);
            user.put("profile", profile);

            Map<String, Object> settings = new LinkedHashMap<>();
            settings.put("notifications", true);
            settings.put("language", "中文");
            user.put("settings", settings);
            sampleData.put("user", user);

            Map<String, Object> system = new LinkedHashMap<>();
            system.put("version", "1.0.0");
            system.put("modules", List.of("auth", "payment", "analytics"));
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("timeout", 30);
            config.put("retry_count", 3);
            system.put("config", config);
            sampleData.put("system", system);

            sampleData.put("status", "active");

            List<Map.Entry<List<String>, Object>> leaves = DictUtils.extractLeafNodes(sampleData, null);

            // Should find all leaf values
            assertFalse(leaves.isEmpty());

            // Verify some known leaves exist
            boolean foundName = false;
            boolean foundCity = false;
            boolean foundStatus = false;
            boolean foundTimeout = false;

            for (Map.Entry<List<String>, Object> entry : leaves) {
                String path = DictUtils.formatPath(entry.getKey());
                if ("user.profile.name".equals(path)) {
                    assertEquals("张三", entry.getValue());
                    foundName = true;
                } else if ("user.profile.address.city".equals(path)) {
                    assertEquals("北京", entry.getValue());
                    foundCity = true;
                } else if ("status".equals(path)) {
                    assertEquals("active", entry.getValue());
                    foundStatus = true;
                } else if ("system.config.timeout".equals(path)) {
                    assertEquals(30, entry.getValue());
                    foundTimeout = true;
                }
            }

            assertTrue(foundName, "Should find user.profile.name");
            assertTrue(foundCity, "Should find user.profile.address.city");
            assertTrue(foundStatus, "Should find status");
            assertTrue(foundTimeout, "Should find system.config.timeout");
        }

        @Test
        @DisplayName("Extract leaves and rebuild dict should produce equivalent structure")
        void testExtractAndRebuild() {
            Map<String, Object> original = new LinkedHashMap<>();
            Map<String, Object> user = new LinkedHashMap<>();
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("name", "张三");
            profile.put("age", 25);
            user.put("profile", profile);
            original.put("user", user);
            original.put("status", "active");

            List<Map.Entry<List<String>, Object>> leaves = DictUtils.extractLeafNodes(original, null);
            Map<String, Object> rebuilt = DictUtils.rebuildDict(leaves);

            // Verify key structure matches
            @SuppressWarnings("unchecked")
            Map<String, Object> rebuiltUser = (Map<String, Object>) rebuilt.get("user");
            assertNotNull(rebuiltUser);
            @SuppressWarnings("unchecked")
            Map<String, Object> rebuiltProfile = (Map<String, Object>) rebuiltUser.get("profile");
            assertNotNull(rebuiltProfile);
            assertEquals("张三", rebuiltProfile.get("name"));
            assertEquals(25, rebuiltProfile.get("age"));
            assertEquals("active", rebuilt.get("status"));
        }

        @Test
        @DisplayName("Null data returns empty list")
        void testNullData() {
            List<Map.Entry<List<String>, Object>> leaves = DictUtils.extractLeafNodes(null, null);
            assertTrue(leaves.isEmpty());
        }

        @Test
        @DisplayName("Extract leaves from list with indices")
        void testExtractFromList() {
            Map<String, Object> data = Map.of("items", List.of("a", "b", "c"));
            List<Map.Entry<List<String>, Object>> leaves = DictUtils.extractLeafNodes(data, null);

            assertEquals(3, leaves.size());
            assertEquals("a", leaves.get(0).getValue());
            assertTrue(DictUtils.formatPath(leaves.get(0).getKey()).contains("[0]"));
        }
    }

    // ==========================================================================
    // test_rebuild (Python: test_rebuild)
    // ==========================================================================
    @Nested
    @DisplayName("rebuildMapFromPaths and rebuildDict")
    class RebuildTests {

        @Test
        @DisplayName("Rebuild dict from simple string-path leaf nodes")
        void testRebuildFromPaths() {
            List<Map.Entry<List<String>, Object>> leaves = new ArrayList<>();
            leaves.add(Map.entry(List.of("user", "profile", "name"), "张三"));
            leaves.add(Map.entry(List.of("user", "profile", "age"), 25));
            leaves.add(Map.entry(List.of("user", "profile", "address", "city"), "北京"));
            leaves.add(Map.entry(List.of("user", "profile", "address", "street"), "朝阳路"));
            leaves.add(Map.entry(List.of("user", "settings", "notifications"), true));
            leaves.add(Map.entry(List.of("user", "settings", "language"), "中文"));
            leaves.add(Map.entry(List.of("system", "version"), "1.0.0"));
            leaves.add(Map.entry(List.of("system", "config", "timeout"), 30));
            leaves.add(Map.entry(List.of("system", "config", "retry_count"), 3));
            leaves.add(Map.entry(List.of("status"), "active"));

            Map<String, Object> rebuilt = DictUtils.rebuildMapFromPaths(leaves);

            @SuppressWarnings("unchecked")
            Map<String, Object> user = (Map<String, Object>) rebuilt.get("user");
            assertNotNull(user);
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) user.get("profile");
            assertEquals("张三", profile.get("name"));
            assertEquals(25, profile.get("age"));

            @SuppressWarnings("unchecked")
            Map<String, Object> addr = (Map<String, Object>) profile.get("address");
            assertEquals("北京", addr.get("city"));
            assertEquals("朝阳路", addr.get("street"));

            assertEquals("active", rebuilt.get("status"));
        }

        @Test
        @DisplayName("Rebuild dict with list index elements")
        void testRebuildWithListIndices() {
            List<Map.Entry<List<String>, Object>> leaves = new ArrayList<>();
            leaves.add(Map.entry(List.of("data", "users", "[0]", "name"), "Alice"));
            leaves.add(Map.entry(List.of("data", "users", "[0]", "age"), 30));
            leaves.add(Map.entry(List.of("data", "users", "[1]", "name"), "Bob"));
            leaves.add(Map.entry(List.of("data", "users", "[1]", "age"), 25));
            leaves.add(Map.entry(List.of("data", "tags", "[0]"), "python"));
            leaves.add(Map.entry(List.of("data", "tags", "[1]"), "programming"));
            leaves.add(Map.entry(List.of("metadata", "count"), 2));

            Map<String, Object> rebuilt = DictUtils.rebuildDict(leaves);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) rebuilt.get("data");
            assertNotNull(data);

            @SuppressWarnings("unchecked")
            List<Object> users = (List<Object>) data.get("users");
            assertNotNull(users);
            assertEquals(2, users.size());

            @SuppressWarnings("unchecked")
            Map<String, Object> user0 = (Map<String, Object>) users.get(0);
            assertEquals("Alice", user0.get("name"));
            assertEquals(30, user0.get("age"));

            @SuppressWarnings("unchecked")
            Map<String, Object> user1 = (Map<String, Object>) users.get(1);
            assertEquals("Bob", user1.get("name"));
            assertEquals(25, user1.get("age"));

            @SuppressWarnings("unchecked")
            List<Object> tags = (List<Object>) data.get("tags");
            assertEquals("python", tags.get(0));
            assertEquals("programming", tags.get(1));

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) rebuilt.get("metadata");
            assertEquals(2, metadata.get("count"));
        }
    }

    // ==========================================================================
    // createNestedMap
    // ==========================================================================
    @Nested
    @DisplayName("createNestedMap")
    class CreateNestedMapTests {

        @Test
        @DisplayName("Create nested map from dotted path")
        void testCreateNestedMap() {
            Object result = DictUtils.createNestedMap("a.b.c", 1);
            assertInstanceOf(Map.class, result);

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            @SuppressWarnings("unchecked")
            Map<String, Object> b = (Map<String, Object>) map.get("a");
            @SuppressWarnings("unchecked")
            Map<String, Object> c = (Map<String, Object>) b.get("b");
            assertEquals(1, c.get("c"));
        }

        @Test
        @DisplayName("Null path returns value directly")
        void testNullPath() {
            Object result = DictUtils.createNestedMap(null, "value");
            assertEquals("value", result);
        }

        @Test
        @DisplayName("Empty path returns value directly")
        void testEmptyPath() {
            Object result = DictUtils.createNestedMap("", "value");
            assertEquals("value", result);
        }

        @Test
        @DisplayName("Custom separator works")
        void testCustomSeparator() {
            Object result = DictUtils.createNestedMap("a/b/c", 42, "/");
            assertInstanceOf(Map.class, result);

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertNotNull(map.get("a"));
        }
    }

    // ==========================================================================
    // flattenMap
    // ==========================================================================
    @Nested
    @DisplayName("flattenMap")
    class FlattenMapTests {

        @Test
        @DisplayName("Flatten nested map to dotted-path keys")
        void testFlattenMap() {
            Map<String, Object> nested = new LinkedHashMap<>();
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("c", 1);
            inner.put("d", 2);
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("b", inner);
            nested.put("a", b);
            nested.put("x", "y");

            Map<String, Object> flat = DictUtils.flattenMap(nested);

            assertEquals(1, flat.get("a.b.c"));
            assertEquals(2, flat.get("a.b.d"));
            assertEquals("y", flat.get("x"));
        }
    }

    // ==========================================================================
    // formatPath
    // ==========================================================================
    @Nested
    @DisplayName("formatPath")
    class FormatPathTests {

        @Test
        @DisplayName("Dict-only path uses dots")
        void testDictPath() {
            String path = DictUtils.formatPath(List.of("a", "b", "c"));
            assertEquals("a.b.c", path);
        }

        @Test
        @DisplayName("List indices appended directly")
        void testListIndexPath() {
            String path = DictUtils.formatPath(List.of("data", "items", "[0]", "name"));
            assertEquals("data.items[0].name", path);
        }

        @Test
        @DisplayName("Single element path")
        void testSingleElement() {
            String path = DictUtils.formatPath(List.of("root"));
            assertEquals("root", path);
        }

        @Test
        @DisplayName("Empty path returns empty string")
        void testEmptyPath() {
            String path = DictUtils.formatPath(List.of());
            assertEquals("", path);
        }
    }
}
