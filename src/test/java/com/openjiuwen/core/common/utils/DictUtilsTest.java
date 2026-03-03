package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DictUtils.
 *
 * @see DictUtils
 */
class DictUtilsTest {

    @Test
    void testExtractLeafNodes() {
        // 创建一个复杂的多层字典
        Map<String, Object> sampleData = Map.of(
                "user", Map.of(
                        "profile", Map.of(
                                "name", "张三",
                                "age", 25,
                                "address", Map.of(
                                        "city", "北京",
                                        "street", "朝阳路"
                                )
                        ),
                        "settings", Map.of(
                                "notifications", true,
                                "language", "中文"
                        )
                ),
                "system", Map.of(
                        "version", "1.0.0",
                        "config", Map.of(
                                "timeout", 30,
                                "retry_count", 3
                        )
                ),
                "status", "active"
        );

        // 提取所有叶子节点
        List<DictUtils.PathValuePair> leaves = DictUtils.extractLeafNodes(sampleData);

        // 验证叶子节点数量
        assertEquals(10, leaves.size());

        // 验证几个关键叶子节点
        assertTrue(leaves.contains(new DictUtils.PathValuePair(List.of("user", "profile", "name"), "张三")));
        assertTrue(leaves.contains(new DictUtils.PathValuePair(List.of("user", "profile", "age"), 25)));
        assertTrue(leaves.contains(new DictUtils.PathValuePair(List.of("status"), "active")));
        assertTrue(leaves.contains(new DictUtils.PathValuePair(List.of("system", "version"), "1.0.0")));
    }

    @Test
    void testExtractLeafNodesWithList() {
        // 包含列表的测试数据
        Map<String, Object> data = Map.of(
                "data", Map.of(
                        "users", List.of(
                                Map.of("name", "Alice", "age", 30),
                                Map.of("name", "Bob", "age", 25)
                        ),
                        "tags", List.of("python", "programming")
                ),
                "metadata", Map.of(
                        "count", 2
                )
        );

        List<DictUtils.PathValuePair> leaves = DictUtils.extractLeafNodes(data);

        // 验证叶子节点数量
        assertEquals(7, leaves.size());

        // 验证列表元素（使用 "[0]" 格式）
        assertTrue(leaves.contains(new DictUtils.PathValuePair(List.of("data", "users", "[0]", "name"), "Alice")));
        assertTrue(leaves.contains(new DictUtils.PathValuePair(List.of("data", "users", "[1]", "age"), 25)));
        assertTrue(leaves.contains(new DictUtils.PathValuePair(List.of("data", "tags", "[1]"), "programming")));
    }

    @Test
    void testExtractLeafNodesEmpty() {
        List<DictUtils.PathValuePair> leaves = DictUtils.extractLeafNodes(null);
        assertTrue(leaves.isEmpty());

        leaves = DictUtils.extractLeafNodes(Map.of());
        assertTrue(leaves.isEmpty());
    }

    @Test
    void testFormatPath() {
        // 测试简单路径
        List<String> path = List.of("a", "b", "c");
        assertEquals("a.b.c", DictUtils.formatPath(path));

        // 测试包含列表索引的路径
        path = List.of("data", "users", "[0]", "name");
        assertEquals("data.users[0].name", DictUtils.formatPath(path));

        // 测试列表索引开头
        path = List.of("[0]", "name");
        assertEquals("[0].name", DictUtils.formatPath(path));

        // 测试只有列表索引
        path = List.of("[0]");
        assertEquals("[0]", DictUtils.formatPath(path));

        // 测试空路径
        assertEquals("", DictUtils.formatPath(List.of()));
    }

    @Test
    void testRebuildDictFromPaths() {
        // 从路径重建字典
        List<DictUtils.PathValuePair> sampleLeaves = Arrays.asList(
                new DictUtils.PathValuePair(List.of("user", "profile", "name"), "张三"),
                new DictUtils.PathValuePair(List.of("user", "profile", "age"), 25),
                new DictUtils.PathValuePair(List.of("user", "profile", "address", "city"), "北京"),
                new DictUtils.PathValuePair(List.of("user", "profile", "address", "street"), "朝阳路"),
                new DictUtils.PathValuePair(List.of("user", "settings", "notifications"), true),
                new DictUtils.PathValuePair(List.of("user", "settings", "language"), "中文"),
                new DictUtils.PathValuePair(List.of("system", "version"), "1.0.0"),
                new DictUtils.PathValuePair(List.of("system", "config", "timeout"), 30),
                new DictUtils.PathValuePair(List.of("system", "config", "retry_count"), 3),
                new DictUtils.PathValuePair(List.of("status"), "active")
        );

        Map<String, Object> rebuiltDict = DictUtils.rebuildDictFromPaths(sampleLeaves);

        // 验证重建结果
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) rebuiltDict.get("user");
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) user.get("profile");
        assertEquals("张三", profile.get("name"));
        assertEquals(25, profile.get("age"));
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) profile.get("address");
        assertEquals("北京", address.get("city"));
        assertEquals("朝阳路", address.get("street"));
        assertEquals("active", rebuiltDict.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> system = (Map<String, Object>) rebuiltDict.get("system");
        assertEquals("1.0.0", system.get("version"));
    }

    @Test
    void testRebuildDict() {
        // 测试包含列表的情况
        List<DictUtils.PathValuePair> listLeaves = Arrays.asList(
                new DictUtils.PathValuePair(List.of("data", "users", "[0]", "name"), "Alice"),
                new DictUtils.PathValuePair(List.of("data", "users", "[0]", "age"), 30),
                new DictUtils.PathValuePair(List.of("data", "users", "[1]", "name"), "Bob"),
                new DictUtils.PathValuePair(List.of("data", "users", "[1]", "age"), 25),
                new DictUtils.PathValuePair(List.of("data", "tags", "[0]"), "python"),
                new DictUtils.PathValuePair(List.of("data", "tags", "[1]"), "programming"),
                new DictUtils.PathValuePair(List.of("metadata", "count"), 2)
        );

        Object rebuiltWithLists = DictUtils.rebuildDict(listLeaves);

        // 验证重建结果
        assertInstanceOf(Map.class, rebuiltWithLists);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) rebuiltWithLists;

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) data.get("users");

        assertEquals("Alice", users.get(0).get("name"));
        assertEquals(30, users.get(0).get("age"));
        assertEquals("Bob", users.get(1).get("name"));
        assertEquals(25, users.get(1).get("age"));

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) data.get("tags");
        assertEquals("python", tags.get(0));
        assertEquals("programming", tags.get(1));
    }

    @Test
    void testCreateNestedDict() {
        // 测试简单路径
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) DictUtils.createNestedDict("a.b", 1);
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) result.get("a");
        assertEquals(1, inner.get("b"));

        // 测试三层嵌套
        result = (Map<String, Object>) DictUtils.createNestedDict("a.b.c", 42);
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) result.get("a");
        @SuppressWarnings("unchecked")
        Map<String, Object> b = (Map<String, Object>) a.get("b");
        assertEquals(42, b.get("c"));

        // 测试空路径 - 返回 value 本身
        Object value = DictUtils.createNestedDict("", "value");
        assertEquals("value", value);

        // 测试自定义分隔符
        result = DictUtils.createNestedDict("a:b:c", "test", ":");
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> a2 = (Map<String, Object>) result.get("a");
        @SuppressWarnings("unchecked")
        Map<String, Object> b2 = (Map<String, Object>) a2.get("b");
        assertEquals("test", b2.get("c"));
    }

    @Test
    void testFlattenDict() {
        // 测试展平字典
        Map<String, Object> data = Map.of(
                "a", Map.of(
                        "b", 1,
                        "c", Map.of(
                                "d", 2
                        )
                ),
                "e", 3
        );

        Map<String, Object> flattened = DictUtils.flattenDict(data);

        assertEquals(3, flattened.size());
        assertEquals(1, flattened.get("a.b"));
        assertEquals(2, flattened.get("a.c.d"));
        assertEquals(3, flattened.get("e"));
    }

    @Test
    void testRebuildDictRoundTrip() {
        // 测试提取和重建的往返一致性
        Map<String, Object> original = Map.of(
                "user", Map.of(
                        "name", "张三",
                        "age", 25
                ),
                "status", "active"
        );

        List<DictUtils.PathValuePair> leaves = DictUtils.extractLeafNodes(original);
        Object rebuilt = DictUtils.rebuildDict(leaves);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) rebuilt;

        assertEquals(original.get("status"), result.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> origUser = (Map<String, Object>) original.get("user");
        @SuppressWarnings("unchecked")
        Map<String, Object> rebuiltUser = (Map<String, Object>) result.get("user");
        assertEquals(origUser.get("name"), rebuiltUser.get("name"));
        assertEquals(origUser.get("age"), rebuiltUser.get("age"));
    }
}