  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.common.utils;

import java.util.*;

/**
 * Utility functions for nested Map (dictionary) manipulation.
 * <p>
 * Supports creating nested maps from dotted paths, flattening, extracting
 * leaf nodes, and rebuilding from path-value pairs.
 */
public final class DictUtils {

    private DictUtils() {
    }

    // ==================== Create ====================

    /**
     * Create a nested Map from a dotted path string.
     * <p>
     * Example: {@code createNestedMap("a.b.c", 1)} → {@code {a={b={c=1}}}}
     *
     * @param path      dotted path, e.g. "a.b.c"
     * @param value     leaf value
     * @param separator path separator (default ".")
     * @return nested Map, or the value itself if path is null/empty
     */
    public static Object createNestedMap(String path, Object value, String separator) {
        if (path == null || path.isEmpty()) {
            return value;
        }
        String[] keys = path.split(java.util.regex.Pattern.quote(separator));
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> current = result;
        for (int i = 0; i < keys.length; i++) {
            if (i == keys.length - 1) {
                current.put(keys[i], value);
            } else {
                Map<String, Object> next = new LinkedHashMap<>();
                current.put(keys[i], next);
                current = next;
            }
        }
        return result;
    }

    /** Overload with default "." separator. */
    public static Object createNestedMap(String path, Object value) {
        return createNestedMap(path, value, ".");
    }

    // ==================== Flatten ====================

    /**
     * Flatten a nested map into a single-level map with dotted-path keys.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> flattenMap(Map<String, Object> data) {
        List<Map.Entry<List<String>, Object>> leaves = extractLeafNodes(data, null);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<List<String>, Object> entry : leaves) {
            result.put(formatPath(entry.getKey()), entry.getValue());
        }
        return result;
    }

    // ==================== Extract leaf nodes ====================

    /**
     * Extract all leaf nodes from a nested dict/list structure.
     *
     * @param data        nested data
     * @param currentPath internal traversal path (pass null initially)
     * @return list of (path, value) entries
     */
    @SuppressWarnings("unchecked")
    public static List<Map.Entry<List<String>, Object>> extractLeafNodes(Object data, List<String> currentPath) {
        if (data == null) {
            return Collections.emptyList();
        }
        if (currentPath == null) {
            currentPath = new ArrayList<>();
        }
        List<Map.Entry<List<String>, Object>> results = new ArrayList<>();

        if (data instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                List<String> newPath = new ArrayList<>(currentPath);
                newPath.add(String.valueOf(entry.getKey()));
                results.addAll(extractLeafNodes(entry.getValue(), newPath));
            }
        } else if (data instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                List<String> newPath = new ArrayList<>(currentPath);
                newPath.add("[" + i + "]");
                results.addAll(extractLeafNodes(list.get(i), newPath));
            }
        } else {
            results.add(Map.entry(new ArrayList<>(currentPath), data));
        }
        return results;
    }

    // ==================== Path formatting ====================

    /**
     * Format a path list into a dotted string.
     * List indices (e.g. "[0]") are appended directly; dict keys are separated by ".".
     */
    public static String formatPath(List<String> path) {
        StringBuilder sb = new StringBuilder();
        for (String key : path) {
            if (sb.isEmpty() || key.startsWith("[")) {
                sb.append(key);
            } else {
                sb.append('.').append(key);
            }
        }
        return sb.toString();
    }

    // ==================== Rebuild ====================

    /**
     * Rebuild a nested Map from (path, value) pairs (dict-keys only, no list indices).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> rebuildMapFromPaths(
            Iterable<Map.Entry<List<String>, Object>> pathValuePairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<List<String>, Object> entry : pathValuePairs) {
            List<String> path = entry.getKey();
            Object value = entry.getValue();
            Map<String, Object> current = result;
            for (int i = 0; i < path.size() - 1; i++) {
                String key = path.get(i);
                current.computeIfAbsent(key, k -> new LinkedHashMap<>());
                Object next = current.get(key);
                if (next instanceof Map) {
                    current = (Map<String, Object>) next;
                }
            }
            if (!path.isEmpty()) {
                current.put(path.get(path.size() - 1), value);
            }
        }
        return result;
    }

    /**
     * Rebuild a nested structure (Maps/Lists) from path-value pairs.
     * Supports list-index path elements formatted as "[index]".
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> rebuildDict(
            Iterable<Map.Entry<List<String>, Object>> pathValuePairs) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<List<String>, Object> entry : pathValuePairs) {
            List<String> path = entry.getKey();
            Object value = entry.getValue();
            Object current = result;

            for (int i = 0; i < path.size() - 1; i++) {
                String key = path.get(i);
                if (isListIndex(key)) {
                    int idx = parseIndex(key);
                    List<Object> list = (List<Object>) current;
                    while (list.size() <= idx) {
                        list.add(new LinkedHashMap<>());
                    }
                    current = list.get(idx);
                } else {
                    Map<String, Object> map = (Map<String, Object>) current;
                    String nextKey = path.get(i + 1);
                    if (!map.containsKey(key)) {
                        map.put(key, isListIndex(nextKey) ? new ArrayList<>() : new LinkedHashMap<>());
                    }
                    current = map.get(key);
                }
            }

            if (!path.isEmpty()) {
                String lastKey = path.get(path.size() - 1);
                if (isListIndex(lastKey)) {
                    int idx = parseIndex(lastKey);
                    List<Object> list = (List<Object>) current;
                    while (list.size() <= idx) {
                        list.add(null);
                    }
                    list.set(idx, value);
                } else {
                    ((Map<String, Object>) current).put(lastKey, value);
                }
            }
        }
        return result;
    }

    private static boolean isListIndex(String key) {
        return key != null && key.startsWith("[") && key.endsWith("]");
    }

    private static int parseIndex(String key) {
        return Integer.parseInt(key.substring(1, key.length() - 1));
    }
}
