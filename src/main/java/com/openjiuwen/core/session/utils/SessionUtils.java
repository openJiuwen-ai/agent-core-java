/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Session utility methods for nested path operations and dict manipulation.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.utils}.
 */
public final class SessionUtils {

    private static final int REGEX_MAX_LENGTH = 1000;
    private static final Pattern REF_PATTERN = Pattern.compile("\\$\\{([^{}]*)}");
    public static final String NESTED_PATH_SPLIT = ".";
    public static final String NESTED_PATH_LIST_SPLIT = "[";

    private SessionUtils() {
    }

    /**
     * Check if a string is a reference path like "${xxx.yyy}".
     */
    public static boolean isRefPath(String path) {
        return path != null && path.length() > 3 && path.startsWith("${") && path.endsWith("}");
    }

    /**
     * Extract the origin key from a reference structure.
     * e.g. "${start123.p2}" → "start123.p2"
     */
    public static String extractOriginKey(String key) {
        if (key == null || !key.contains("$")) {
            return key;
        }
        String sub = key.length() > REGEX_MAX_LENGTH ? key.substring(0, REGEX_MAX_LENGTH) : key;
        Matcher matcher = REF_PATTERN.matcher(sub);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return key;
    }

    /**
     * Split a nested path into components.
     * e.g. "a.b.c[1].d" → ["a", "b", "c", 1, "d"]
     */
    @SuppressWarnings("unchecked")
    public static List<Object> splitNestedPath(String nestedKey) {
        if (nestedKey == null || !(nestedKey instanceof String)) {
            return List.of();
        }
        if (!nestedKey.contains(NESTED_PATH_SPLIT)
                && !nestedKey.contains(NESTED_PATH_LIST_SPLIT)
                && !nestedKey.contains("['")) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        String[] parts = nestedKey.split("\\.", -1);
        Pattern indexPattern = Pattern.compile("\\[(-?\\d+)]|\\['([^']*)']");
        for (String part : parts) {
            if (part.contains("[")) {
                String basePart = part.split("\\[")[0];
                if (!basePart.isEmpty()) {
                    result.add(basePart);
                }
                Matcher m = indexPattern.matcher(part);
                while (m.find()) {
                    if (m.group(1) != null) {
                        result.add(Integer.parseInt(m.group(1)));
                    } else if (m.group(2) != null) {
                        result.add(m.group(2));
                    }
                }
            } else {
                result.add(part);
            }
        }
        return result;
    }

    /**
     * Get a value by nested path from a source map.
     */
    @SuppressWarnings("unchecked")
    public static Object getValueByNestedPath(String nestedKey, Map<String, Object> source) {
        Object[] result = rootToPath(nestedKey, source, false);
        if (result[1] == null) {
            return null;
        }
        Object container = result[1];
        Object key = result[0];
        try {
            if (container instanceof List<?> list) {
                int index = ((Number) key).intValue();
                if (index < 0) {
                    index = list.size() + index;
                }
                if (index >= 0 && index < list.size()) {
                    return list.get(index);
                }
                return null;
            } else if (container instanceof Map<?, ?> map) {
                return map.get(key);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Navigate from root to the final path position.
     * Returns [key, container] where container[key] is the target.
     */
    @SuppressWarnings("unchecked")
    public static Object[] rootToPath(String nestedPath, Object source, boolean createIfAbsent) {
        List<Object> paths = splitNestedPath(nestedPath);
        if (paths.isEmpty()) {
            return new Object[]{nestedPath, source};
        }
        Object current = source;
        for (int i = 0; i < paths.size(); i++) {
            Object path = paths.get(i);
            boolean isLast = (i == paths.size() - 1);
            if (path instanceof String strPath) {
                if (current instanceof Map map) {
                    if (!map.containsKey(strPath)) {
                        if (!createIfAbsent) {
                            return new Object[]{null, null};
                        }
                        if (!isLast && i + 1 < paths.size() && paths.get(i + 1) instanceof Integer) {
                            map.put(strPath, new ArrayList<>());
                        } else {
                            map.put(strPath, new HashMap<>());
                        }
                    }
                    if (isLast) {
                        return new Object[]{strPath, current};
                    }
                    Object next = map.get(strPath);
                    if (!createIfAbsent && next == null) {
                        return new Object[]{null, null};
                    }
                    current = next;
                } else {
                    return new Object[]{null, null};
                }
            } else if (path instanceof Integer intPath) {
                if (current instanceof List list) {
                    if (intPath >= list.size()) {
                        if (!createIfAbsent) {
                            return new Object[]{null, null};
                        }
                        while (list.size() <= intPath) {
                            list.add(null);
                        }
                    }
                    if (isLast) {
                        return new Object[]{intPath, current};
                    }
                    Object next = list.get(intPath);
                    if (!createIfAbsent && next == null) {
                        return new Object[]{null, null};
                    }
                    current = next;
                } else {
                    return new Object[]{null, null};
                }
            }
        }
        return new Object[]{null, null};
    }

    /**
     * Update source dict by update dict.
     * Note: source is unnested structure, update keys may be nested.
     */
    @SuppressWarnings("unchecked")
    public static void updateDict(Map<String, Object> update, Map<String, Object> source, boolean ignoreDelete) {
        List<Object[]> removed = new ArrayList<>();
        for (Map.Entry<String, Object> entry : update.entrySet()) {
            Object[] result = rootToPath(entry.getKey(), source, true);
            Object currentKey = result[0];
            Object container = result[1];
            if (entry.getValue() == null && !ignoreDelete) {
                removed.add(new Object[]{currentKey, container});
            } else {
                updateByKey(currentKey, entry.getValue(), container);
            }
        }
        if (!ignoreDelete) {
            for (Object[] pair : removed) {
                deleteByKey(pair[0], pair[1]);
            }
        }
    }

    /**
     * Update source dict by update dict (default: don't ignore delete).
     */
    public static void updateDict(Map<String, Object> update, Map<String, Object> source) {
        updateDict(update, source, false);
    }

    @SuppressWarnings("unchecked")
    public static void updateByKey(Object key, Object newValue, Object source) {
        if (source instanceof Map map) {
            Object existing = map.get(key);
            if (existing instanceof Map && newValue instanceof Map) {
                updateDict((Map<String, Object>) newValue, (Map<String, Object>) existing, false);
            } else {
                map.put(key, expandNestedStructure(newValue));
            }
        } else if (source instanceof List<?> list && key instanceof Integer index) {
            @SuppressWarnings("unchecked")
            List<Object> writableList = (List<Object>) list;
            if (index >= 0 && index < writableList.size()) {
                Object existing = writableList.get(index);
                if (existing instanceof Map && newValue instanceof Map) {
                    updateDict((Map<String, Object>) newValue, (Map<String, Object>) existing, false);
                } else {
                    writableList.set(index, expandNestedStructure(newValue));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void deleteByKey(Object key, Object source) {
        if (source instanceof Map map) {
            map.remove(key);
        } else if (source instanceof List<?> list && key instanceof Integer index) {
            @SuppressWarnings("unchecked")
            List<Object> writableList = (List<Object>) list;
            if (index >= 0 && index < writableList.size()) {
                writableList.set(index, null);
            }
        }
    }

    /**
     * Expand nested structure.
     */
    @SuppressWarnings("unchecked")
    public static Object expandNestedStructure(Object data) {
        if (data instanceof List list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(expandNestedStructure(item));
            }
            return result;
        } else if (data instanceof Map map) {
            Map<String, Object> result = new HashMap<>();
            for (Object entry : map.entrySet()) {
                Map.Entry<String, Object> e = (Map.Entry<String, Object>) entry;
                Object[] pathResult = rootToPath(e.getKey(), result, true);
                Object expandedValue = expandNestedStructure(e.getValue());
                if (pathResult[1] instanceof Map m) {
                    m.put(pathResult[0], expandedValue);
                } else if (pathResult[1] instanceof List<?> list && pathResult[0] instanceof Integer index) {
                    @SuppressWarnings("unchecked")
                    List<Object> writableList = (List<Object>) list;
                    writableList.set(index, expandedValue);
                }
            }
            return result;
        }
        return data;
    }

    /**
     * Get value by schema (supports str, list, dict schemas).
     */
    @SuppressWarnings("unchecked")
    public static Object getBySchema(Object schema, Map<String, Object> data) {
        return getBySchema(schema, data, null, true);
    }

    /**
     * Get value by schema with optional nested path prefix.
     */
    @SuppressWarnings("unchecked")
    public static Object getBySchema(Object schema, Map<String, Object> data, String nestedPath, boolean isRoot) {
        if (nestedPath != null && !nestedPath.isEmpty()) {
            Object nested = getValueByNestedPath(nestedPath, data);
            if (nested instanceof Map) {
                data = (Map<String, Object>) nested;
            } else {
                return null;
            }
        }
        if (schema == null || data == null) {
            return null;
        }
        if (schema instanceof String strSchema) {
            String originKey = extractOriginKey(strSchema);
            if (originKey.equals(strSchema) && !isRoot) {
                return strSchema;
            }
            return getValueByNestedPath(originKey, data);
        } else if (schema instanceof Map<?, ?> mapSchema) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : mapSchema.entrySet()) {
                String targetKey = (String) entry.getKey();
                Object targetSchema = entry.getValue();
                if (targetSchema instanceof List || targetSchema instanceof Map
                        || (targetSchema instanceof String && isRefPath((String) targetSchema))) {
                    result.put(targetKey, getBySchema(targetSchema, data, null, false));
                } else {
                    result.put(targetKey, targetSchema);
                }
            }
            return result;
        } else if (schema instanceof List<?> listSchema) {
            List<Object> result = new ArrayList<>();
            for (Object item : listSchema) {
                result.add(getBySchema(item, data, null, false));
            }
            return result;
        }
        return schema;
    }

    /**
     * Safely extend a list container to accommodate a target index.
     *
     * @param container   the list to extend
     * @param targetIndex the target index that must be reachable
     * @param isFinalIndex if true, fills the target position with an empty map; otherwise empty list
     * @return true if extension succeeded or was not needed
     */
    public static boolean safeExtendContainer(List<Object> container, int targetIndex, boolean isFinalIndex) {
        if (container == null) {
            return false;
        }
        if (targetIndex < 0 || targetIndex > 10000) {
            return false;
        }
        int currentLength = container.size();
        if (targetIndex < currentLength) {
            return true;
        }
        int expansionNeeded = targetIndex - currentLength + 1;
        if (expansionNeeded > 10000) {
            return false;
        }
        try {
            for (int i = currentLength; i < targetIndex; i++) {
                container.add(null);
            }
            container.add(isFinalIndex ? new HashMap<>() : new ArrayList<>());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Navigate through a nested list structure using a path of indexes.
     * Returns [adjustedIndex, container] or [null, null] on failure.
     *
     * @param indexes       list of integer indexes
     * @param source        the root list
     * @param createIfAbsent if true, extend lists to reach target
     * @return Object array [Integer index, List container]
     */
    @SuppressWarnings("unchecked")
    public static Object[] rootToIndex(List<Integer> indexes, List<Object> source, boolean createIfAbsent) {
        if (indexes == null) {
            throw new IllegalArgumentException("indexes must be a list");
        }
        for (Object idx : indexes) {
            if (!(idx instanceof Integer)) {
                throw new IllegalArgumentException("all elements in indexes must be integers");
            }
        }
        if (source == null || indexes.isEmpty()) {
            return new Object[]{null, null};
        }
        if (indexes.size() > 10) {
            throw new IllegalArgumentException("Nesting level too deep, level limit is 10");
        }

        Object current = source;

        // Process intermediate indexes
        if (indexes.size() > 1) {
            for (int i = 0; i < indexes.size() - 1; i++) {
                int idx = indexes.get(i);
                if (!(current instanceof List)) {
                    return new Object[]{null, null};
                }
                List<Object> currentList = (List<Object>) current;
                int adjustedIdx;
                if (idx < 0) {
                    adjustedIdx = idx + currentList.size();
                    if (adjustedIdx < 0) {
                        return new Object[]{null, null};
                    }
                } else {
                    adjustedIdx = idx;
                    if (adjustedIdx > 10000) {
                        throw new IllegalArgumentException("Index must be between [0,10000]");
                    }
                }

                if (adjustedIdx >= currentList.size()) {
                    if (!createIfAbsent) {
                        return new Object[]{null, null};
                    }
                    if (!safeExtendContainer(currentList, adjustedIdx, false)) {
                        return new Object[]{null, null};
                    }
                }

                try {
                    current = currentList.get(adjustedIdx);
                } catch (Exception e) {
                    return new Object[]{null, null};
                }

                if (current != null && !(current instanceof List)) {
                    return new Object[]{null, null};
                }
            }
        }

        // Process final index
        if (!(current instanceof List)) {
            return new Object[]{null, null};
        }
        List<Object> finalList = (List<Object>) current;
        int finalIdx = indexes.get(indexes.size() - 1);
        int adjustedFinalIdx;
        if (finalIdx < 0) {
            adjustedFinalIdx = finalIdx + finalList.size();
            if (adjustedFinalIdx < 0) {
                return new Object[]{null, null};
            }
        } else {
            adjustedFinalIdx = finalIdx;
            if (adjustedFinalIdx > 10000) {
                throw new IllegalArgumentException("Index must be between [0,10000]");
            }
        }

        if (adjustedFinalIdx >= finalList.size()) {
            if (!createIfAbsent) {
                return new Object[]{null, null};
            }
            if (!safeExtendContainer(finalList, adjustedFinalIdx, true)) {
                return new Object[]{null, null};
            }
        }

        return new Object[]{adjustedFinalIdx, finalList};
    }

    /**
     * Sentinel class for representing end frame markers.
     */
    public static final class EndFrame {
        public static final String MESSAGE = "all streaming outputs finish";

        private EndFrame() {
        }
    }
}
