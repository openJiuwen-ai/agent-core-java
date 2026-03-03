// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dictionary utility methods for nested data structures.
 *
 * <p>This class provides utilities for:
 * <ul>
 *   <li>Creating nested dictionaries from dotted paths</li>
 *   <li>Flattening nested dictionaries</li>
 *   <li>Extracting leaf nodes from nested structures</li>
 *   <li>Formatting paths as strings</li>
 *   <li>Rebuilding nested structures from path-value pairs</li>
 * </ul>
 *
 * @see PathValuePair
 */
public final class DictUtils {

    // Private constructor to prevent instantiation
    private DictUtils() {
    }

    /**
     * Create a nested dictionary from a dotted path string.
     *
     * @param path      Dotted path (for example "a.b.c"). If falsy, return the value itself.
     * @param value     Value to place at the leaf.
     * @param separator Path separator (default '.')
     * @param <T>       The type of the value
     * @return A nested dictionary representing the path with the value at the final key.
     *         If path is empty or null, returns the value itself.
     * @see #createNestedDict(String, Object)
     */
    @SuppressWarnings("unchecked")
    public static <T> Map<String, Object> createNestedDict(String path, T value, String separator) {
        // 忠实于 Python: if not path: return value
        if (path == null || path.isEmpty()) {
            // 返回一个包含单个条目的 map，键为空字符串，值为 value
            // 这样可以保持返回类型为 Map<String, Object>
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("", value);
            return result;
        }

        String[] keys = path.split(java.util.regex.Pattern.quote(separator));
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> current = result;

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            if (i == keys.length - 1) {
                current.put(key, value);
            } else {
                Map<String, Object> next = new LinkedHashMap<>();
                current.put(key, next);
                current = next;
            }
        }

        return result;
    }

    /**
     * Create a nested dictionary from a dotted path string using default separator '.'.
     *
     * @param path  Dotted path (for example "a.b.c"). If falsy, return the value itself.
     * @param value Value to place at the leaf.
     * @return A nested dictionary representing the path with the value at the final key.
     *         If path is empty or null, returns the value itself.
     */
    public static Object createNestedDict(String path, Object value) {
        if (path == null || path.isEmpty()) {
            return value;
        }

        Map<String, Object> result = createNestedDict(path, value, ".");
        return result;
    }

    /**
     * Flatten a nested dictionary into a single-level dictionary with dotted keys.
     *
     * @param data The nested dictionary to flatten.
     * @return A flattened dictionary with dotted path keys.
     */
    public static Map<String, Object> flattenDict(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return new HashMap<>();
        }

        List<PathValuePair> nodes = extractLeafNodes(data);
        Map<String, Object> result = new LinkedHashMap<>();
        for (PathValuePair node : nodes) {
            result.put(formatPath(node.path()), node.value());
        }
        return result;
    }

    /**
     * Extract all leaf nodes from a nested structure of dicts/lists.
     *
     * <p>This function walks nested dictionaries and lists and returns a list
     * of tuples where each tuple contains the path (as a list of keys/indexes)
     * and the leaf value.
     *
     * @param data The nested data structure (dicts/lists/values).
     * @return A list of (path, value) tuples. Paths are represented as lists of
     *         strings; list indices are formatted as "[index]".
     */
    public static List<PathValuePair> extractLeafNodes(Object data) {
        return extractLeafNodes(data, null);
    }

    /**
     * Extract all leaf nodes from a nested structure of dicts/lists.
     *
     * <p>This function walks nested dictionaries and lists and returns a list
     * of tuples where each tuple contains the path (as a list of keys/indexes)
     * and the leaf value.
     *
     * @param data        The nested data structure (dicts/lists/values).
     * @param currentPath Internal use only. The current traversal path.
     * @return A list of (path, value) tuples. Paths are represented as lists of
     *         strings; list indices are formatted as "[index]".
     */
    public static List<PathValuePair> extractLeafNodes(Object data, List<String> currentPath) {
        // Python: if not data: return [] - 处理 null、空 dict、空 list、False、0 等
        if (data == null) {
            return new ArrayList<>();
        }
        if (data instanceof Map<?, ?> && ((Map<?, ?>) data).isEmpty()) {
            return new ArrayList<>();
        }
        if (data instanceof List<?> && ((List<?>) data).isEmpty()) {
            return new ArrayList<>();
        }
        if (data instanceof Boolean && !(Boolean) data) {
            return new ArrayList<>();
        }
        if (data instanceof Number && ((Number) data).intValue() == 0) {
            return new ArrayList<>();
        }

        if (currentPath == null) {
            currentPath = new ArrayList<>();
        }

        List<PathValuePair> results = new ArrayList<>();

        // If dict, iterate keys and recurse
        if (data instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                List<String> newPath = new ArrayList<>(currentPath);
                newPath.add(entry.getKey());
                results.addAll(extractLeafNodes(entry.getValue(), newPath));
            }
        }
        // If list, handle each element and include the index in path
        else if (data instanceof List<?>) {
            List<?> list = (List<?>) data;
            for (int index = 0; index < list.size(); index++) {
                List<String> newPath = new ArrayList<>(currentPath);
                newPath.add("[" + index + "]");
                results.addAll(extractLeafNodes(list.get(index), newPath));
            }
        }
        // Otherwise it's a leaf node
        else {
            results.add(new PathValuePair(currentPath, data));
        }

        return results;
    }

    /**
     * Format a path list into a dotted string representation.
     *
     * <p>List indices (like "[0]") are appended directly. Dictionary keys are
     * joined with a dot separator.
     *
     * @param path Iterable of path elements (strings).
     * @return A formatted path string.
     */
    public static String formatPath(Iterable<String> path) {
        StringBuilder pathStr = new StringBuilder();
        for (String key : path) {
            // Use equality comparison for empty string checks
            if (pathStr.isEmpty() || key.startsWith("[")) {
                pathStr.append(key);
            } else {
                pathStr.append(".").append(key);
            }
        }
        return pathStr.toString();
    }

    /**
     * Rebuild a nested dict from (path, value) pairs.
     *
     * <p>This function assumes paths are lists of keys (no list-index handling).
     *
     * @param pathValuePairs Iterable of (path, value) tuples where path is a
     *                       list of keys.
     * @return A nested dictionary reconstructed from paths.
     */
    public static Map<String, Object> rebuildDictFromPaths(Iterable<PathValuePair> pathValuePairs) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (PathValuePair pair : pathValuePairs) {
            List<String> path = pair.path();
            Object value = pair.value();
            Map<String, Object> current = result;

            // Traverse the path except the last key
            for (int i = 0; i < path.size() - 1; i++) {
                String key = path.get(i);
                // Create a dict if missing and move deeper
                if (!current.containsKey(key)) {
                    current.put(key, new LinkedHashMap<String, Object>());
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> next = (Map<String, Object>) current.get(key);
                current = next;
            }

            // Set the final value
            String lastKey = path.get(path.size() - 1);
            current.put(lastKey, value);
        }

        return result;
    }

    /**
     * Rebuild a nested structure (dicts/lists) from path-value pairs.
     *
     * <p>This function supports list-index path elements formatted as "[index]".
     * It attempts to create lists when an index element appears in the path.
     *
     * @param pathValuePairs Iterable of (path, value) tuples. Path elements are
     *                       strings; list indices must be formatted as "[index]".
     * @return The reconstructed nested structure (usually a dict).
     */
    @SuppressWarnings("unchecked")
    public static Object rebuildDict(Iterable<PathValuePair> pathValuePairs) {
        // 使用一个特殊的包装器来跟踪当前对象及其父级引用
        // 由于 Java 的类型限制，我们使用 Object 类型
        Object result = new LinkedHashMap<String, Object>();

        for (PathValuePair pair : pathValuePairs) {
            List<String> path = pair.path();
            Object value = pair.value();
            List<Object> currentStack = new ArrayList<>();
            currentStack.add(result);

            // 遍历路径，维护一个栈来跟踪当前对象
            for (int i = 0; i < path.size(); i++) {
                String key = path.get(i);
                boolean isLast = (i == path.size() - 1);
                boolean nextIsList = !isLast && path.get(i + 1).startsWith("[");
                Object current = currentStack.get(currentStack.size() - 1);

                if (key.startsWith("[") && key.endsWith("]")) {
                    // 处理 list 索引
                    int index = Integer.parseInt(key.substring(1, key.length() - 1));

                    // 如果当前不是 list，需要转换
                    if (!(current instanceof List<?>)) {
                        List<Object> newList = new ArrayList<>();
                        // 如果当前是 Map 且不是根节点，需要更新父级引用
                        if (currentStack.size() > 1) {
                            Object parent = currentStack.get(currentStack.size() - 2);
                            String parentKey = path.get(i - 1);
                            if (parentKey.startsWith("[") && parentKey.endsWith("]")) {
                                // 父级也是 list
                                int parentIndex = Integer.parseInt(parentKey.substring(1, parentKey.length() - 1));
                                @SuppressWarnings("unchecked")
                                List<Object> parentList = (List<Object>) parent;
                                parentList.set(parentIndex, newList);
                            } else {
                                // 父级是 Map
                                @SuppressWarnings("unchecked")
                                Map<String, Object> parentMap = (Map<String, Object>) parent;
                                parentMap.put(parentKey, newList);
                            }
                        } else {
                            // 根节点
                            result = newList;
                        }
                        current = newList;
                    }

                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) current;

                    // 扩展列表
                    while (list.size() <= index) {
                        list.add(isLast ? null : (nextIsList ? new ArrayList<>() : new LinkedHashMap<String, Object>()));
                    }

                    if (isLast) {
                        list.set(index, value);
                    } else {
                        currentStack.add(list.get(index));
                    }
                } else {
                    // 处理 dict 键
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) current;

                    if (!map.containsKey(key)) {
                        map.put(key, nextIsList ? new ArrayList<>() : new LinkedHashMap<String, Object>());
                    }

                    if (isLast) {
                        map.put(key, value);
                    } else {
                        currentStack.add(map.get(key));
                    }
                }
            }
        }

        return result;
    }

    /**
     * A record representing a path-value pair.
     *
     * @param path  The path as a list of strings (including list indices like "[0]")
     * @param value The leaf value
     */
    public record PathValuePair(List<String> path, Object value) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PathValuePair that)) return false;
            return path.equals(that.path) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, value);
        }
    }
}