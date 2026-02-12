package com.openjiuwen.core.common.utils;

import java.util.*;

/**
 * 字典/Map操作工具类
 * 
 * 从 Python dict_utils.py 转换
 * 提供嵌套字典的创建、展平、提取叶子节点、重建等功能
 */
public final class DictUtils {

    private DictUtils() {
        // 防止实例化
    }

    /**
     * 从点分路径字符串创建嵌套字典
     *
     * @param path      点分路径（例如 "a.b.c"），如果为空则直接返回value
     * @param value     要放置在叶子节点的值
     * @param separator 路径分隔符（默认'.'）
     * @return 嵌套字典
     */
    public static Map<String, Object> createNestedDict(String path, Object value, String separator) {
        if (path == null || path.isEmpty()) {
            // 如果路径为空，无法创建字典，返回包含value的单层Map
            Map<String, Object> result = new HashMap<>();
            result.put("value", value);
            return result;
        }

        String[] keys = path.split(separator != null ? separator : "\\.");
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> current = result;

        for (int i = 0; i < keys.length; i++) {
            if (i == keys.length - 1) {
                // 最后一个key，设置值
                current.put(keys[i], value);
            } else {
                // 中间key，创建嵌套Map
                Map<String, Object> nested = new HashMap<>();
                current.put(keys[i], nested);
                current = nested;
            }
        }

        return result;
    }

    /**
     * 展平字典为单层键值对，键为完整路径
     *
     * @param data 要展平的字典
     * @return 展平后的字典，键为点分路径
     */
    public static Map<String, Object> flattenDict(Map<String, Object> data) {
        List<Pair<List<String>, Object>> nodes = extractLeafNodes(data, null);
        Map<String, Object> result = new HashMap<>();
        
        for (Pair<List<String>, Object> node : nodes) {
            String path = formatPath(node.getKey());
            result.put(path, node.getValue());
        }
        
        return result;
    }

    /**
     * 从嵌套结构（字典/列表）中提取所有叶子节点
     * 
     * 遍历嵌套的字典和列表，返回路径-值对的列表
     *
     * @param data        嵌套数据结构（字典/列表/值）
     * @param currentPath 当前遍历路径（内部使用）
     * @return 路径-值对的列表，路径表示为字符串列表，列表索引格式为"[index]"
     */
    public static List<Pair<List<String>, Object>> extractLeafNodes(Object data, List<String> currentPath) {
        if (data == null) {
            return Collections.emptyList();
        }
        
        if (currentPath == null) {
            currentPath = new ArrayList<>();
        }

        List<Pair<List<String>, Object>> results = new ArrayList<>();

        // 如果是Map，遍历键并递归
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                List<String> newPath = new ArrayList<>(currentPath);
                newPath.add(entry.getKey());
                results.addAll(extractLeafNodes(entry.getValue(), newPath));
            }
        }
        // 如果是List，处理每个元素并在路径中包含索引
        else if (data instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) data;
            
            for (int index = 0; index < list.size(); index++) {
                List<String> newPath = new ArrayList<>(currentPath);
                newPath.add("[" + index + "]");
                results.addAll(extractLeafNodes(list.get(index), newPath));
            }
        }
        // 否则是叶子节点
        else {
            results.add(new Pair<>(currentPath, data));
        }

        return results;
    }

    /**
     * 将路径列表格式化为点分字符串
     * 
     * 列表索引（如"[0]"）直接附加，字典键用点分隔符连接
     *
     * @param path 路径元素的可迭代对象
     * @return 格式化的路径字符串
     */
    public static String formatPath(Iterable<String> path) {
        StringBuilder pathStr = new StringBuilder();
        
        for (String key : path) {
            if (pathStr.length() == 0 || key.startsWith("[")) {
                pathStr.append(key);
            } else {
                pathStr.append(".").append(key);
            }
        }
        
        return pathStr.toString();
    }

    /**
     * 从路径-值对重建嵌套字典
     * 
     * 此函数假定路径是键列表（不处理列表索引）
     *
     * @param pathValuePairs 路径-值对的可迭代对象，路径是键列表
     * @return 重建的嵌套字典
     */
    public static Map<String, Object> rebuildDictFromPaths(Iterable<Pair<List<String>, Object>> pathValuePairs) {
        Map<String, Object> result = new HashMap<>();

        for (Pair<List<String>, Object> pair : pathValuePairs) {
            List<String> path = pair.getKey();
            Object value = pair.getValue();
            
            Map<String, Object> current = result;

            // 遍历路径（除最后一个键）
            for (int i = 0; i < path.size() - 1; i++) {
                String key = path.get(i);
                
                // 如果缺失则创建字典并深入
                if (!current.containsKey(key)) {
                    current.put(key, new HashMap<String, Object>());
                }
                
                @SuppressWarnings("unchecked")
                Map<String, Object> next = (Map<String, Object>) current.get(key);
                current = next;
            }

            // 设置最终值
            if (!path.isEmpty()) {
                String lastKey = path.get(path.size() - 1);
                current.put(lastKey, value);
            }
        }

        return result;
    }

    /**
     * 从路径-值对重建嵌套结构（字典/列表）
     * 
     * 此函数支持格式为"[index]"的列表索引路径元素
     * 当路径中出现索引元素时，尝试创建列表
     *
     * @param pathValuePairs 路径-值对的可迭代对象，路径元素是字符串，列表索引必须格式化为"[index]"
     * @return 重建的嵌套结构（通常是字典）
     */
    public static Object rebuildDict(Iterable<Pair<List<String>, Object>> pathValuePairs) {
        Map<String, Object> result = new HashMap<>();

        for (Pair<List<String>, Object> pair : pathValuePairs) {
            List<String> path = pair.getKey();
            Object value = pair.getValue();
            
            Object current = result;

            for (int i = 0; i < path.size() - 1; i++) {
                String key = path.get(i);
                
                // 处理列表索引元素如"[0]"
                if (key.startsWith("[") && key.endsWith("]")) {
                    int index = Integer.parseInt(key.substring(1, key.length() - 1));
                    
                    // 确保当前层是列表
                    if (!(current instanceof List)) {
                        // 这种情况通常不应发生，但为了健壮性处理
                        current = new ArrayList<>();
                    }
                    
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) current;
                    
                    // 扩展列表到所需长度
                    while (list.size() <= index) {
                        list.add(new HashMap<String, Object>());
                    }
                    
                    current = list.get(index);
                } else {
                    // 处理字典键
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) current;
                    
                    if (!map.containsKey(key)) {
                        // 查看下一个键以决定创建字典还是列表
                        if (i + 1 < path.size()) {
                            String nextKey = path.get(i + 1);
                            if (nextKey.startsWith("[") && nextKey.endsWith("]")) {
                                map.put(key, new ArrayList<>());
                            } else {
                                map.put(key, new HashMap<String, Object>());
                            }
                        } else {
                            map.put(key, new HashMap<String, Object>());
                        }
                    }
                    
                    current = map.get(key);
                }
            }

            // 设置最终值，处理可能的列表索引
            if (!path.isEmpty()) {
                String lastKey = path.get(path.size() - 1);
                
                if (lastKey.startsWith("[") && lastKey.endsWith("]")) {
                    int index = Integer.parseInt(lastKey.substring(1, lastKey.length() - 1));
                    
                    if (!(current instanceof List)) {
                        current = new ArrayList<>();
                    }
                    
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) current;
                    
                    while (list.size() <= index) {
                        list.add(null);
                    }
                    
                    list.set(index, value);
                } else {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) current;
                    map.put(lastKey, value);
                }
            }
        }

        return result;
    }
}


