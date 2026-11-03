/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.utils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code openjiuwen.core.session.utils} in
 * {@code openjiuwen/core/session/utils.py}.
 */
public final class SessionUtils {
    private static final int REGEX_MAX_LENGTH = 1000;
    private static final Pattern REF_PATTERN = Pattern.compile("\\$\\{([^{}]*)}");
    private static final Pattern INDEX_PATTERN = Pattern.compile("\\[(-?\\d+)]|\\['([^']+)'\\]");

    public static final String NESTED_PATH_SPLIT = ".";
    public static final String NESTED_PATH_LIST_SPLIT = "[";

    private SessionUtils() {
    }

    /**
     * Marker interface for proxy wrappers returned by {@link #createWrapperClass(Object, String)}.
     *
     * @param <T> wrapped type
     */
    public interface WrappedObject<T> {
        T getWrapped();
    }

    /**
     * Mirrors Python's wrapper helper. When the wrapped object exposes interfaces, this returns a
     * delegating proxy that also exposes {@link WrappedObject#getWrapped()}. Objects without
     * interfaces are returned unchanged because Java cannot synthesize concrete classes at runtime
     * without an extra bytecode library.
     *
     * @param originalObj object to wrap
     * @param wrapperName logical wrapper name from Python, kept for parity
     * @param <T> wrapped type
     * @return proxy wrapper or the original object when proxying is not possible
     */
    public static <T> T createWrapperClass(T originalObj, String wrapperName) {
        if (originalObj == null) {
            return null;
        }
        Set<Class<?>> interfaces = collectInterfaces(originalObj.getClass());
        if (interfaces.isEmpty()) {
            return originalObj;
        }
        interfaces.add(WrappedObject.class);
        InvocationHandler handler = (proxy, method, args) -> invokeWrapped(originalObj, method, args);
        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(
                originalObj.getClass().getClassLoader(),
                interfaces.toArray(new Class<?>[0]),
                handler
        );
        return proxy;
    }

    /**
     * Convenience overload with Python's default wrapper name.
     *
     * @param originalObj object to wrap
     * @param <T> wrapped type
     * @return proxy wrapper or original object
     */
    public static <T> T createWrapperClass(T originalObj) {
        return createWrapperClass(originalObj, "WrappedObject");
    }

    /**
     * Updates an unnested source dictionary with potentially nested update keys.
     *
     * @param update update entries
     * @param source destination structure
     * @param ignoreDelete whether {@code null} means delete
     */
    public static void updateDict(Map<String, Object> update, Map<String, Object> source, boolean ignoreDelete) {
        List<Object[]> removed = new ArrayList<>();
        for (Map.Entry<String, Object> entry : update.entrySet()) {
            Object[] result = rootToPath(entry.getKey(), source, true);
            Object currentKey = result[0];
            Object current = result[1];
            if (entry.getValue() == null && !ignoreDelete) {
                removed.add(new Object[]{currentKey, current});
            } else {
                updateByKey(currentKey, entry.getValue(), current);
            }
        }
        if (!ignoreDelete) {
            for (Object[] removedEntry : removed) {
                deleteByKey(removedEntry[0], removedEntry[1]);
            }
        }
    }

    /**
     * Default overload that keeps Python's delete-on-null behavior.
     *
     * @param update update entries
     * @param source destination structure
     */
    public static void updateDict(Map<String, Object> update, Map<String, Object> source) {
        updateDict(update, source, false);
    }

    /**
     * Resolves a schema against a nested source structure.
     *
     * @param schema schema object
     * @param data source data
     * @return resolved structure
     */
    public static Object getBySchema(Object schema, Map<String, Object> data) {
        return getBySchema(schema, data, null, true);
    }

    /**
     * Resolves a schema against a nested source structure.
     *
     * @param schema schema object
     * @param data source data
     * @param nestedPath optional nested prefix
     * @param isRoot whether this is the root schema call
     * @return resolved structure
     */
    @SuppressWarnings("unchecked")
    public static Object getBySchema(Object schema, Map<String, Object> data, String nestedPath, boolean isRoot) {
        if (nestedPath != null && !nestedPath.isEmpty()) {
            Object nested = getValueByNestedPath(nestedPath, data);
            if (!(nested instanceof Map<?, ?>)) {
                return null;
            }
            data = (Map<String, Object>) nested;
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
        }
        if (schema instanceof Map<?, ?> mapSchema) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapSchema.entrySet()) {
                String targetKey = String.valueOf(entry.getKey());
                Object targetSchema = entry.getValue();
                if (targetSchema instanceof List<?>
                        || targetSchema instanceof Map<?, ?>
                        || (targetSchema instanceof String str && isRefPath(str))) {
                    result.put(targetKey, getBySchema(targetSchema, data, null, false));
                } else {
                    result.put(targetKey, targetSchema);
                }
            }
            return result;
        }
        if (schema instanceof List<?> listSchema) {
            List<Object> result = new ArrayList<>(listSchema.size());
            for (Object item : listSchema) {
                result.add(getBySchema(item, data, null, false));
            }
            return result;
        }
        return schema;
    }

    /**
     * Resolves a nested path against map/list data.
     *
     * @param nestedKey dotted or indexed path
     * @param source source data
     * @return resolved value or {@code null}
     */
    public static Object getValueByNestedPath(String nestedKey, Object source) {
        Object[] result = rootToPath(nestedKey, source, false);
        Object key = result[0];
        Object container = result[1];
        if (container == null) {
            return null;
        }
        try {
            if (container instanceof List<?> list && key instanceof Number number) {
                int index = number.intValue();
                int adjustedIndex = index < 0 ? list.size() + index : index;
                if (adjustedIndex < 0 || adjustedIndex >= list.size()) {
                    return null;
                }
                return list.get(adjustedIndex);
            }
            if (container instanceof Map<?, ?> map) {
                return map.get(key);
            }
            return null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Splits a nested path such as {@code a.b[0]['key']} into a token list.
     *
     * @param nestedKey nested path
     * @return path tokens
     */
    public static List<Object> splitNestedPath(String nestedKey) {
        if (nestedKey == null) {
            return List.of();
        }
        if (!nestedKey.contains(NESTED_PATH_SPLIT)
                && !nestedKey.contains(NESTED_PATH_LIST_SPLIT)
                && !nestedKey.contains("['")) {
            return List.of();
        }
        List<Object> finalList = new ArrayList<>();
        String[] params = nestedKey.split("\\.", -1);
        for (String param : params) {
            if (param.contains("[")) {
                String basePart = param.split("\\[", -1)[0];
                if (!basePart.isEmpty()) {
                    finalList.add(basePart);
                }
                Matcher matcher = INDEX_PATTERN.matcher(param);
                while (matcher.find()) {
                    if (matcher.group(1) != null) {
                        finalList.add(Integer.parseInt(matcher.group(1)));
                    } else if (matcher.group(2) != null) {
                        finalList.add(matcher.group(2));
                    }
                }
            } else {
                finalList.add(param);
            }
        }
        return finalList;
    }

    /**
     * Matches Python's {@code ${...}} reference detection.
     *
     * @param path candidate path
     * @return whether the path is a reference
     */
    public static boolean isRefPath(String path) {
        return path != null && path.length() > 3 && path.startsWith("${") && path.endsWith("}");
    }

    /**
     * Extracts the inner path from a reference expression.
     *
     * @param key candidate key
     * @return extracted key or the original input
     */
    public static String extractOriginKey(String key) {
        if (key == null || !key.contains("$")) {
            return key;
        }
        String input = key.length() > REGEX_MAX_LENGTH ? key.substring(0, REGEX_MAX_LENGTH) : key;
        Matcher matcher = REF_PATTERN.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return key;
    }

    /**
     * Deletes a key from a dictionary container. List/index deletions stay no-op to mirror the
     * Python implementation.
     *
     * @param key key to delete
     * @param source container
     */
    public static void deleteByKey(Object key, Object source) {
        if (source instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> writableMap = (Map<Object, Object>) map;
            if (writableMap.containsKey(key) && !(key instanceof Integer)) {
                writableMap.remove(key);
            }
        }
    }

    /**
     * Updates a single key on a map/list container.
     *
     * @param key key or index
     * @param newValue replacement value
     * @param source destination container
     */
    @SuppressWarnings("unchecked")
    public static void updateByKey(Object key, Object newValue, Object source) {
        if (source instanceof Map<?, ?> map) {
            Map<Object, Object> writableMap = (Map<Object, Object>) map;
            if (!writableMap.containsKey(key)) {
                writableMap.put(key, expandNestedStructure(newValue));
                return;
            }
            Object existing = writableMap.get(key);
            Object expandedValue = expandNestedStructure(newValue);
            if (existing instanceof Map<?, ?> && expandedValue instanceof Map<?, ?>) {
                updateDict((Map<String, Object>) expandedValue, (Map<String, Object>) existing, false);
            } else {
                writableMap.put(key, expandedValue);
            }
            return;
        }
        if (source instanceof List<?> list && key instanceof Number number) {
            List<Object> writableList = (List<Object>) list;
            int adjustedIndex = normalizeListIndex(number.intValue(), writableList.size());
            if (adjustedIndex < 0 || adjustedIndex >= writableList.size()) {
                return;
            }
            Object existing = writableList.get(adjustedIndex);
            Object expandedValue = expandNestedStructure(newValue);
            if (existing instanceof Map<?, ?> && expandedValue instanceof Map<?, ?>) {
                updateDict((Map<String, Object>) expandedValue, (Map<String, Object>) existing, false);
            } else {
                writableList.set(adjustedIndex, expandedValue);
            }
        }
    }

    /**
     * Expands nested maps/lists so dot-path keys become real nested structures.
     *
     * @param data input data
     * @return expanded data
     */
    @SuppressWarnings("unchecked")
    public static Object expandNestedStructure(Object data) {
        if (data instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(expandNestedStructure(item));
            }
            return result;
        }
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String nestedKey = String.valueOf(entry.getKey());
                Object[] pathResult = rootToPath(nestedKey, result, true);
                Object expandedValue = expandNestedStructure(entry.getValue());
                if (pathResult[1] instanceof Map<?, ?> pathMap) {
                    ((Map<Object, Object>) pathMap).put(pathResult[0], expandedValue);
                } else if (pathResult[1] instanceof List<?> pathList && pathResult[0] instanceof Number number) {
                    List<Object> writableList = (List<Object>) pathList;
                    writableList.set(normalizeListIndex(number.intValue(), writableList.size()), expandedValue);
                }
            }
            return result;
        }
        return data;
    }

    /**
     * Navigates a nested map/list structure and returns the final key with its container.
     *
     * @param nestedPath path to navigate
     * @param source source structure
     * @param createIfAbsent whether to materialize missing containers
     * @return {@code [key, container]} or {@code [null, null]}
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
            boolean isLast = i == paths.size() - 1;
            if (path instanceof String strPath) {
                if (!(current instanceof Map<?, ?> map)) {
                    return new Object[]{null, null};
                }
                Map<Object, Object> writableMap = (Map<Object, Object>) map;
                if (!writableMap.containsKey(strPath)) {
                    if (!createIfAbsent) {
                        return new Object[]{null, null};
                    }
                    if (!isLast && i + 1 < paths.size() && paths.get(i + 1) instanceof Integer) {
                        writableMap.put(strPath, new ArrayList<>());
                    } else {
                        writableMap.put(strPath, new LinkedHashMap<>());
                    }
                }
                if (isLast) {
                    return new Object[]{strPath, current};
                }
                Object next = writableMap.get(strPath);
                if (!createIfAbsent && next == null) {
                    return new Object[]{null, null};
                }
                current = next;
                continue;
            }

            if (path instanceof Integer indexPath) {
                if (!(current instanceof List<?> list)) {
                    return new Object[]{null, null};
                }
                List<Object> writableList = (List<Object>) list;
                int adjustedIndex = normalizeListIndex(indexPath, writableList.size());
                if (indexPath >= 0 && adjustedIndex >= writableList.size()) {
                    if (!createIfAbsent) {
                        return new Object[]{null, null};
                    }
                    while (writableList.size() <= adjustedIndex) {
                        writableList.add(null);
                    }
                }
                if (adjustedIndex < 0 || adjustedIndex >= writableList.size()) {
                    return new Object[]{null, null};
                }
                if (isLast) {
                    return new Object[]{indexPath, current};
                }
                Object next = writableList.get(adjustedIndex);
                if (!createIfAbsent && next == null) {
                    return new Object[]{null, null};
                }
                current = next;
                continue;
            }

            return new Object[]{null, null};
        }
        return new Object[]{null, null};
    }

    /**
     * Convenience overload that mirrors Python's default {@code create_if_absent=False}.
     *
     * @param nestedPath path to navigate
     * @param source source structure
     * @return {@code [key, container]} or {@code [null, null]}
     */
    public static Object[] rootToPath(String nestedPath, Object source) {
        return rootToPath(nestedPath, source, false);
    }

    /**
     * Safely extends a list container up to the target index.
     *
     * @param container container to extend
     * @param targetIndex destination index
     * @param isFinalIndex whether the destination should hold an empty map
     * @return whether the extension succeeded
     */
    @SuppressWarnings("unchecked")
    public static boolean safeExtendContainer(List<?> container, int targetIndex, boolean isFinalIndex) {
        if (container == null || targetIndex < 0 || targetIndex > 10000) {
            return false;
        }
        List<Object> writableList = (List<Object>) container;
        int currentLength = writableList.size();
        if (targetIndex < currentLength) {
            return true;
        }
        int expansionNeeded = targetIndex - currentLength + 1;
        if (expansionNeeded > 10000) {
            return false;
        }
        try {
            if (targetIndex > currentLength) {
                writableList.addAll(java.util.Collections.nCopies(targetIndex - currentLength, null));
            }
            writableList.add(isFinalIndex ? new LinkedHashMap<>() : new ArrayList<>());
            return true;
        } catch (UnsupportedOperationException | IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Mirrors Python's list/tuple index navigation helper.
     *
     * @param indexes index path
     * @param source source container
     * @param createIfAbsent whether to materialize missing list nodes
     * @return {@code [index, container]} or {@code [null, null]}
     */
    @SuppressWarnings("unchecked")
    public static Object[] rootToIndex(List<Integer> indexes, List<?> source, boolean createIfAbsent) {
        if (indexes == null) {
            throw new IllegalArgumentException("indexes must be a list");
        }
        if (indexes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("all elements in indexes must be integers");
        }
        if (source == null || indexes.isEmpty()) {
            return new Object[]{null, null};
        }
        if (indexes.size() > 10) {
            throw new IllegalArgumentException("Nesting level too deep, level limit is 10");
        }

        Object current = source;
        if (indexes.size() > 1) {
            for (int i = 0; i < indexes.size() - 1; i++) {
                if (!(current instanceof List<?> list)) {
                    return new Object[]{null, null};
                }
                List<Object> currentList = (List<Object>) list;
                int adjustedIndex = adjustIndex(indexes.get(i), currentList.size());
                if (adjustedIndex < 0) {
                    return new Object[]{null, null};
                }
                if (adjustedIndex > 10000) {
                    throw new IllegalArgumentException("Index must be between [0,10000]");
                }
                if (adjustedIndex >= currentList.size()) {
                    if (!createIfAbsent || !safeExtendContainer(currentList, adjustedIndex, false)) {
                        return new Object[]{null, null};
                    }
                }
                try {
                    current = currentList.get(adjustedIndex);
                } catch (RuntimeException exception) {
                    return new Object[]{null, null};
                }
                if (current != null && !(current instanceof List<?>)) {
                    return new Object[]{null, null};
                }
            }
        }

        if (!(current instanceof List<?> finalList)) {
            return new Object[]{null, null};
        }
        List<Object> writableFinalList = (List<Object>) finalList;
        int adjustedFinalIndex = adjustIndex(indexes.get(indexes.size() - 1), writableFinalList.size());
        if (adjustedFinalIndex < 0) {
            return new Object[]{null, null};
        }
        if (adjustedFinalIndex > 10000) {
            throw new IllegalArgumentException("Index must be between [0,10000]");
        }
        if (adjustedFinalIndex >= writableFinalList.size()) {
            if (!createIfAbsent || !safeExtendContainer(writableFinalList, adjustedFinalIndex, true)) {
                return new Object[]{null, null};
            }
        }
        return new Object[]{adjustedFinalIndex, writableFinalList};
    }

    /**
     * Convenience overload that mirrors Python's default {@code create_if_absent=False}.
     *
     * @param indexes index path
     * @param source source container
     * @return {@code [index, container]} or {@code [null, null]}
     */
    public static Object[] rootToIndex(List<Integer> indexes, List<?> source) {
        return rootToIndex(indexes, source, false);
    }

    /**
     * Dataclass mirror for the Python end-frame marker.
     */
    public static final class EndFrame {
        private final String source;

        public EndFrame(String source) {
            this.source = source;
        }

        public String source() {
            return source;
        }

        public String getSource() {
            return source;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof EndFrame endFrame)) {
                return false;
            }
            return Objects.equals(source, endFrame.source);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source);
        }

        @Override
        public String toString() {
            return "EndFrame{source='" + source + "'}";
        }
    }

    private static int normalizeListIndex(int index, int size) {
        return index < 0 ? size + index : index;
    }

    private static int adjustIndex(int index, int size) {
        return index < 0 ? size + index : index;
    }

    private static Set<Class<?>> collectInterfaces(Class<?> type) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        Class<?> current = type;
        while (current != null) {
            for (Class<?> iface : current.getInterfaces()) {
                interfaces.add(iface);
            }
            current = current.getSuperclass();
        }
        return interfaces;
    }

    private static Object invokeWrapped(Object originalObj, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == WrappedObject.class && "getWrapped".equals(method.getName())) {
            return originalObj;
        }
        try {
            Method targetMethod = originalObj.getClass().getMethod(method.getName(), method.getParameterTypes());
            targetMethod.setAccessible(true);
            return targetMethod.invoke(originalObj, args);
        } catch (NoSuchMethodException exception) {
            return method.invoke(originalObj, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
