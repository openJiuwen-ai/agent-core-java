package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DictUtils 测试类
 * 
 * 从 Python test_dict.py 转换
 */
public class DictUtilsTest {

    @Test
    public void testExtractLeaf() {
        // 创建一个复杂的多层字典
        Map<String, Object> sampleData = new HashMap<>();
        
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", "张三");
        profile.put("age", 25);
        
        Map<String, Object> address = new HashMap<>();
        address.put("city", "北京");
        address.put("street", "朝阳路");
        profile.put("address", address);
        
        Map<String, Object> settings = new HashMap<>();
        settings.put("notifications", true);
        settings.put("language", "中文");
        
        Map<String, Object> user = new HashMap<>();
        user.put("profile", profile);
        user.put("settings", settings);
        
        Map<String, Object> config = new HashMap<>();
        config.put("timeout", 30);
        config.put("retry_count", 3);
        
        List<String> modules = Arrays.asList("auth", "payment", "analytics");
        
        Map<String, Object> system = new HashMap<>();
        system.put("version", "1.0.0");
        system.put("modules", modules);
        system.put("config", config);
        
        sampleData.put("user", user);
        sampleData.put("system", system);
        sampleData.put("status", "active");
        
        // 提取所有叶子节点
        List<Pair<List<String>, Object>> leaves = DictUtils.extractLeafNodes(sampleData, null);
        
        // 验证叶子节点数量
        assertTrue(leaves.size() > 0, "应该找到多个叶子节点");
        
        // 验证包含特定路径
        boolean foundName = leaves.stream()
            .anyMatch(p -> p.getKey().contains("name") && "张三".equals(p.getValue()));
        assertTrue(foundName, "应该找到 name=张三 的叶子节点");
        
        // 重建字典
        Object tree = DictUtils.rebuildDict(leaves);
        assertNotNull(tree, "重建的树不应该为null");
        
        // 验证简洁格式（路径字符串）
        for (Pair<List<String>, Object> pair : leaves) {
            String pathStr = DictUtils.formatPath(pair.getKey());
            assertNotNull(pathStr, "格式化后的路径不应该为null");
            assertFalse(pathStr.isEmpty(), "格式化后的路径不应该为空");
        }
    }

    @Test
    public void testRebuild() {
        // 从叶子节点重建字典（不含列表）
        List<Pair<List<String>, Object>> sampleLeaves = Arrays.asList(
            new Pair<>(Arrays.asList("user", "profile", "name"), "张三"),
            new Pair<>(Arrays.asList("user", "profile", "age"), 25),
            new Pair<>(Arrays.asList("user", "profile", "address", "city"), "北京"),
            new Pair<>(Arrays.asList("user", "profile", "address", "street"), "朝阳路"),
            new Pair<>(Arrays.asList("user", "settings", "notifications"), true),
            new Pair<>(Arrays.asList("user", "settings", "language"), "中文"),
            new Pair<>(Arrays.asList("system", "version"), "1.0.0"),
            new Pair<>(Arrays.asList("system", "config", "timeout"), 30),
            new Pair<>(Arrays.asList("system", "config", "retry_count"), 3),
            new Pair<>(Arrays.asList("status"), "active")
        );
        
        // 重建字典（使用 rebuildDictFromPaths 适用于简单路径）
        Map<String, Object> rebuiltDict = DictUtils.rebuildDictFromPaths(sampleLeaves);
        
        assertNotNull(rebuiltDict, "重建的字典不应该为null");
        assertTrue(rebuiltDict.containsKey("user"), "应该包含user键");
        assertTrue(rebuiltDict.containsKey("system"), "应该包含system键");
        assertTrue(rebuiltDict.containsKey("status"), "应该包含status键");
        assertEquals("active", rebuiltDict.get("status"), "status值应该为active");
        
        // 测试包含列表的情况
        List<Pair<List<String>, Object>> listLeaves = Arrays.asList(
            new Pair<>(Arrays.asList("data", "users", "[0]", "name"), "Alice"),
            new Pair<>(Arrays.asList("data", "users", "[0]", "age"), 30),
            new Pair<>(Arrays.asList("data", "users", "[1]", "name"), "Bob"),
            new Pair<>(Arrays.asList("data", "users", "[1]", "age"), 25),
            new Pair<>(Arrays.asList("data", "tags", "[0]"), "python"),
            new Pair<>(Arrays.asList("data", "tags", "[1]"), "programming"),
            new Pair<>(Arrays.asList("metadata", "count"), 2)
        );
        
        // 重建包含列表的字典
        Object rebuiltWithLists = DictUtils.rebuildDict(listLeaves);
        
        assertNotNull(rebuiltWithLists, "重建的包含列表的字典不应该为null");
        assertTrue(rebuiltWithLists instanceof Map, "重建结果应该是Map类型");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> mapWithLists = (Map<String, Object>) rebuiltWithLists;
        assertTrue(mapWithLists.containsKey("data"), "应该包含data键");
        assertTrue(mapWithLists.containsKey("metadata"), "应该包含metadata键");
        
        // 验证重建是否正确
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) mapWithLists.get("data");
        assertNotNull(data, "data不应该为null");
        
        Object usersObj = data.get("users");
        assertTrue(usersObj instanceof List, "users应该是List类型");
        
        @SuppressWarnings("unchecked")
        List<Object> users = (List<Object>) usersObj;
        assertEquals(2, users.size(), "users列表应该有2个元素");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> user0 = (Map<String, Object>) users.get(0);
        assertEquals("Alice", user0.get("name"), "用户0姓名应该是Alice");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> user1 = (Map<String, Object>) users.get(1);
        assertEquals(25, user1.get("age"), "用户1年龄应该是25");
        
        Object tagsObj = data.get("tags");
        assertTrue(tagsObj instanceof List, "tags应该是List类型");
        
        @SuppressWarnings("unchecked")
        List<Object> tags = (List<Object>) tagsObj;
        assertEquals("programming", tags.get(1), "标签1应该是programming");
    }
}


