/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mirrors Python's dict utility helpers in
 * {@code openjiuwen/core/common/utils/dict_utils.py}.
 */
public final class DictUtils {

    public record PathValuePair(List<String> path, Object value) {
    }

    private DictUtils() {
    }

    public static Object createNestedDict(String path, Object value) {
        return createNestedDict(path, value, ".");
    }

    public static Object createNestedDict(String path, Object value, String separator) {
        if (path == null || path.isEmpty()) {
            return value;
        }
        String[] keys = path.split(Pattern.quote(separator), -1);
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

    public static Map<String, Object> flattenDict(Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (PathValuePair node : extractLeafNodes(data)) {
            result.put(formatPath(node.path()), node.value());
        }
        return result;
    }

    public static List<PathValuePair> extractLeafNodes(Object data) {
        return extractLeafNodes(data, new ArrayList<>());
    }

    public static String formatPath(Iterable<String> path) {
        StringBuilder builder = new StringBuilder();
        for (String key : path) {
            if (builder.isEmpty() || key.startsWith("[")) {
                builder.append(key);
            } else {
                builder.append('.').append(key);
            }
        }
        return builder.toString();
    }

    public static Map<String, Object> rebuildDictFromPaths(Iterable<PathValuePair> pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (PathValuePair pair : pairs) {
            Map<String, Object> current = result;
            List<String> path = pair.path();
            for (int i = 0; i < path.size() - 1; i++) {
                String key = path.get(i);
                current = (Map<String, Object>) current.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            }
            current.put(path.get(path.size() - 1), pair.value());
        }
        return result;
    }

    public static Object rebuildDict(Iterable<PathValuePair> pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (PathValuePair pair : pairs) {
            putValue(result, pair.path(), 0, pair.value());
        }
        return result;
    }

    private static List<PathValuePair> extractLeafNodes(Object data, List<String> currentPath) {
        List<PathValuePair> results = new ArrayList<>();
        if (isFalsy(data)) {
            return results;
        }
        if (data instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                List<String> newPath = new ArrayList<>(currentPath);
                newPath.add(String.valueOf(entry.getKey()));
                results.addAll(extractLeafNodes(entry.getValue(), newPath));
            }
            return results;
        }
        if (data instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                List<String> newPath = new ArrayList<>(currentPath);
                newPath.add("[" + i + "]");
                results.addAll(extractLeafNodes(list.get(i), newPath));
            }
            return results;
        }
        results.add(new PathValuePair(new ArrayList<>(currentPath), data));
        return results;
    }

    @SuppressWarnings("unchecked")
    private static void putValue(Object current, List<String> path, int index, Object value) {
        String key = path.get(index);
        boolean last = index == path.size() - 1;
        if (isIndexKey(key)) {
            List<Object> list = (List<Object>) current;
            int listIndex = parseIndex(key);
            ensureListSize(list, listIndex + 1);
            if (last) {
                list.set(listIndex, value);
                return;
            }
            Object next = list.get(listIndex);
            if (next == null) {
                next = isIndexKey(path.get(index + 1)) ? new ArrayList<>() : new LinkedHashMap<String, Object>();
                list.set(listIndex, next);
            }
            putValue(next, path, index + 1, value);
            return;
        }

        Map<String, Object> map = (Map<String, Object>) current;
        if (last) {
            map.put(key, value);
            return;
        }
        Object next = map.get(key);
        if (next == null) {
            next = isIndexKey(path.get(index + 1)) ? new ArrayList<>() : new LinkedHashMap<String, Object>();
            map.put(key, next);
        }
        putValue(next, path, index + 1, value);
    }

    private static void ensureListSize(List<Object> list, int size) {
        while (list.size() < size) {
            list.add(null);
        }
    }

    private static boolean isIndexKey(String key) {
        return key != null && key.startsWith("[") && key.endsWith("]");
    }

    private static int parseIndex(String key) {
        return Integer.parseInt(key.substring(1, key.length() - 1));
    }

    private static boolean isFalsy(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean bool) {
            return !bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() == 0.0d;
        }
        if (value instanceof CharSequence text) {
            return text.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }
}
