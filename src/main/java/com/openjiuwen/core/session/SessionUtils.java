/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Session utility functions for nested path operations and state management.
 * 
 * <p>Provides utilities for:
 * <ul>
 *   <li>Nested path parsing and navigation (e.g., "a.b.c", "a[0].b")</li>
 *   <li>Reference path extraction (e.g., "${path}" → "path")</li>
 *   <li>Dictionary update with nested key support</li>
 *   <li>Schema-based value extraction</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public final class SessionUtils {
    
    private SessionUtils() {
        // Utility class, prevent instantiation
    }
    
    /**
     * Maximum length for regex pattern matching.
     */
    public static final int REGEX_MAX_LENGTH = 1000;
    
    /**
     * Nested path separator.
     */
    public static final String NESTED_PATH_SPLIT = ".";
    
    /**
     * Nested path list index separator.
     */
    public static final String NESTED_PATH_LIST_SPLIT = "[";
    
    /**
     * Pattern for matching list index in nested path.
     */
    private static final Pattern NESTED_PATH_LIST_PATTERN = Pattern.compile("^([\\w]+)((?:\\[\\d+\\])*)$");
    
    /**
     * Pattern for extracting reference path.
     */
    private static final Pattern REF_PATH_PATTERN = Pattern.compile("\\$\\{([^{}]*)\\}");
    
    /**
     * Pattern for extracting array indexes and string keys.
     */
    private static final Pattern INDEX_PATTERN = Pattern.compile("\\[(-?\\d+)\\]|\\['([^']+)'\\]");
    
    /**
     * Checks if a path is a reference path (format: ${...}).
     * 
     * @param path the path to check
     * @return true if the path is a reference path
     */
    public static boolean isRefPath(String path) {
        return path != null && path.length() > 3 && path.startsWith("${") && path.endsWith("}");
    }
    
    /**
     * Extracts the origin key from a reference path.
     * 
     * <p>Example: "${start123.p2}" → "start123.p2"
     * 
     * @param key the reference key
     * @return the origin key, or the input if not a reference path
     */
    public static String extractOriginKey(String key) {
        if (key == null || !key.contains("$")) {
            return key;
        }
        Matcher matcher = REF_PATH_PATTERN.matcher(key);
        if (matcher.find() && matcher.start() < REGEX_MAX_LENGTH) {
            return matcher.group(1);
        }
        return key;
    }
    
    /**
     * Splits a nested path into components.
     * 
     * <p>Examples:
     * <ul>
     *   <li>"a_1.b.c[1].d" → ["a_1", "b", "c", 1, "d"]</li>
     *   <li>"a.b[0]['key']" → ["a", "b", 0, "key"]</li>
     * </ul>
     * 
     * @param nestedKey the nested path to split
     * @return list of path components (String for keys, Integer for indexes)
     */
    public static List<Object> splitNestedPath(String nestedKey) {
        if (nestedKey == null) {
            return List.of();
        }
        
        boolean hasNestedSplit = nestedKey.contains(NESTED_PATH_SPLIT);
        boolean hasListSplit = nestedKey.contains(NESTED_PATH_LIST_SPLIT);
        boolean hasStringKey = nestedKey.contains("['");
        
        if (!hasNestedSplit && !hasListSplit && !hasStringKey) {
            return List.of();
        }
        
        List<Object> finalList = new ArrayList<>();
        String[] params = nestedKey.split(Pattern.quote(NESTED_PATH_SPLIT), -1);
        
        for (String param : params) {
            if (param.contains("[")) {
                // Extract base part
                int bracketIndex = param.indexOf('[');
                String basePart = param.substring(0, bracketIndex);
                if (!basePart.isEmpty()) {
                    finalList.add(basePart);
                }
                
                // Extract indexes
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
     * Gets value from data using a nested path.
     * 
     * @param nestedKey the nested path key
     * @param source the source map
     * @return the value at the path, or null if not found
     */
    @SuppressWarnings("unchecked")
    public static Object getValueByNestedPath(String nestedKey, Map<String, Object> source) {
        if (source == null || nestedKey == null) {
            return null;
        }
        
        PathResult result = rootToPath(nestedKey, source, false);
        if (result.container() == null) {
            return null;
        }
        
        Object key = result.key();
        Object container = result.container();
        
        try {
            if (container instanceof List<?> list) {
                int index = (Integer) key;
                if (index < 0) {
                    int adjustedIndex = index + list.size();
                    if (adjustedIndex >= 0 && adjustedIndex < list.size()) {
                        return list.get(adjustedIndex);
                    }
                    return null;
                } else {
                    if (index < list.size()) {
                        return list.get(index);
                    }
                    return null;
                }
            } else if (container instanceof Map<?, ?> map) {
                return map.get(key);
            }
        } catch (ClassCastException | IndexOutOfBoundsException e) {
            return null;
        }
        
        return null;
    }
    
    /**
     * Gets value from data using a schema definition.
     * 
     * <p>Schema can be:
     * <ul>
     *   <li>String: direct path or reference path (${...})</li>
     *   <li>Map: mapping of target keys to source paths</li>
     *   <li>List: list of schemas</li>
     * </ul>
     * 
     * @param schema the schema definition
     * @param data the source data
     * @return the extracted value according to schema
     */
    public static Object getBySchema(Object schema, Map<String, Object> data) {
        return getBySchema(schema, data, null, true);
    }
    
    /**
     * Gets value from data using a schema definition with nested prefix.
     * 
     * @param schema the schema definition
     * @param data the source data
     * @param nestedPrefix optional prefix path
     * @return the extracted value according to schema
     */
    public static Object getBySchema(Object schema, Map<String, Object> data, String nestedPrefix) {
        return getBySchema(schema, data, nestedPrefix, true);
    }
    
    @SuppressWarnings("unchecked")
    private static Object getBySchema(Object schema, Map<String, Object> data, String nestedPrefix, boolean isRoot) {
        if (nestedPrefix != null && !nestedPrefix.isEmpty()) {
            Object prefixData = getValueByNestedPath(nestedPrefix, data);
            if (prefixData instanceof Map) {
                data = (Map<String, Object>) prefixData;
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
                String targetKey = entry.getKey().toString();
                Object targetSchema = entry.getValue();
                if (targetSchema instanceof List || targetSchema instanceof Map || 
                    (targetSchema instanceof String s && isRefPath(s))) {
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
     * Updates a source map with values from an update map.
     * 
     * <p>Keys in update map can be nested paths. Values of null will delete the key.
     * 
     * @param update the update map with nested keys
     * @param source the source map to update
     */
    public static void updateDict(Map<String, Object> update, Map<String, Object> source) {
        updateDict(update, source, false);
    }
    
    /**
     * Updates a source map with values from an update map.
     * 
     * @param update the update map with nested keys
     * @param source the source map to update
     * @param ignoreDelete if true, null values won't delete keys
     */
    @SuppressWarnings("unchecked")
    public static void updateDict(Map<String, Object> update, Map<String, Object> source, boolean ignoreDelete) {
        if (update == null || source == null) {
            return;
        }
        
        List<Map.Entry<Object, Object>> removed = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : update.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            PathResult result = rootToPath(key, source, true);
            Object currentKey = result.key();
            Object container = result.container();
            
            if (container instanceof Map<?, ?> currentMap) {
                Map<String, Object> mapContainer = (Map<String, Object>) currentMap;
                if (value == null && !ignoreDelete) {
                    removed.add(Map.entry(currentKey, container));
                } else {
                    updateByKey(currentKey, value, mapContainer);
                }
            }
        }
        
        if (!ignoreDelete) {
            for (Map.Entry<Object, Object> entry : removed) {
                deleteByKey(entry.getKey(), entry.getValue());
            }
        }
    }
    
    /**
     * Navigates to a path in the source map.
     * 
     * @param nestedPath the nested path
     * @param source the source map
     * @param createIfAbsent whether to create missing intermediate containers
     * @return PathResult containing the final key and container
     */
    @SuppressWarnings("unchecked")
    public static PathResult rootToPath(String nestedPath, Map<String, Object> source, boolean createIfAbsent) {
        List<Object> paths = splitNestedPath(nestedPath);
        if (paths.isEmpty()) {
            return new PathResult(nestedPath, source);
        }
        
        Object current = source;
        for (int i = 0; i < paths.size(); i++) {
            Object path = paths.get(i);
            boolean isLast = (i == paths.size() - 1);
            
            if (path instanceof String strPath) {
                if (current instanceof Map<?, ?> map) {
                    Map<String, Object> mapCurrent = (Map<String, Object>) map;
                    
                    if (!mapCurrent.containsKey(strPath)) {
                        if (!createIfAbsent) {
                            return new PathResult(null, null);
                        }
                        // Check if next path is an integer (array index)
                        if (!isLast && i + 1 < paths.size() && paths.get(i + 1) instanceof Integer) {
                            mapCurrent.put(strPath, new ArrayList<>());
                        } else {
                            mapCurrent.put(strPath, new HashMap<>());
                        }
                    }
                    
                    if (isLast) {
                        return new PathResult(strPath, mapCurrent);
                    }
                    
                    Object next = mapCurrent.get(strPath);
                    if (!createIfAbsent && next == null) {
                        return new PathResult(null, null);
                    }
                    current = next;
                } else {
                    return new PathResult(null, null);
                }
            } else if (path instanceof Integer intPath) {
                if (current instanceof List<?> list) {
                    List<Object> listCurrent = (List<Object>) list;
                    
                    if (intPath >= listCurrent.size()) {
                        if (!createIfAbsent) {
                            return new PathResult(null, null);
                        }
                        while (listCurrent.size() <= intPath) {
                            listCurrent.add(null);
                        }
                    }
                    
                    if (isLast) {
                        return new PathResult(intPath, listCurrent);
                    }
                    
                    Object next = listCurrent.get(intPath);
                    if (!createIfAbsent && next == null) {
                        return new PathResult(null, null);
                    }
                    current = next;
                } else {
                    return new PathResult(null, null);
                }
            }
        }
        
        return new PathResult(null, null);
    }
    
    /**
     * Updates a value at a key in a map.
     * 
     * @param key the key
     * @param newValue the new value
     * @param source the source map
     */
    @SuppressWarnings("unchecked")
    private static void updateByKey(Object key, Object newValue, Map<String, Object> source) {
        String strKey = key.toString();
        if (!source.containsKey(strKey)) {
            source.put(strKey, expandNestedStructure(newValue));
            return;
        }
        
        Object existing = source.get(strKey);
        if (existing instanceof Map && newValue instanceof Map) {
            updateDict((Map<String, Object>) newValue, (Map<String, Object>) existing, true);
        } else {
            source.put(strKey, expandNestedStructure(newValue));
        }
    }
    
    /**
     * Deletes a key from a container.
     * 
     * @param key the key to delete
     * @param container the container
     */
    @SuppressWarnings("unchecked")
    private static void deleteByKey(Object key, Object container) {
        if (key instanceof Integer) {
            return; // Don't delete from list by index
        }
        if (container instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).remove(key.toString());
        }
    }
    
    /**
     * Expands a nested structure (deep copy with nested key expansion).
     * 
     * @param data the data to expand
     * @return the expanded data
     */
    @SuppressWarnings("unchecked")
    public static Object expandNestedStructure(Object data) {
        if (data instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(expandNestedStructure(item));
            }
            return result;
        } else if (data instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> mapData = (Map<String, Object>) map;
            for (Map.Entry<String, Object> entry : mapData.entrySet()) {
                PathResult pathResult = rootToPath(entry.getKey(), result, true);
                if (pathResult.container() instanceof Map) {
                    Map<String, Object> container = (Map<String, Object>) pathResult.container();
                    container.put(pathResult.key().toString(), expandNestedStructure(entry.getValue()));
                }
            }
            return result;
        }
        return data;
    }
    
    /**
     * Navigates to an index path in a list/array structure.
     * 
     * @param indexes list of integer indexes
     * @param source the source list
     * @param createIfAbsent whether to create missing elements
     * @return PathResult containing the final index and container
     */
    @SuppressWarnings("unchecked")
    public static PathResult rootToIndex(List<Integer> indexes, List<Object> source, boolean createIfAbsent) {
        if (indexes == null) {
            throw new IllegalArgumentException("indexes must be a list");
        }
        if (!indexes.stream().allMatch(idx -> idx instanceof Integer)) {
            throw new IllegalArgumentException("all elements in indexes must be integers");
        }
        
        if (source == null || indexes.isEmpty()) {
            return new PathResult(null, null);
        }
        
        if (indexes.size() > 10) {
            throw new IllegalArgumentException("Nesting level too deep, level limit is 10");
        }
        
        Object current = source;
        
        // Process intermediate indexes
        if (indexes.size() > 1) {
            for (int i = 0; i < indexes.size() - 1; i++) {
                int idx = indexes.get(i);
                
                // Handle negative indexes
                int adjustedIdx;
                if (idx < 0) {
                    if (!(current instanceof List<?> currentList)) {
                        return new PathResult(null, null);
                    }
                    adjustedIdx = idx + currentList.size();
                    if (adjustedIdx < 0) {
                        return new PathResult(null, null);
                    }
                } else {
                    adjustedIdx = idx;
                    if (adjustedIdx > 10000) {
                        throw new IllegalArgumentException("Index must be between [0,10000]");
                    }
                }
                
                if (!(current instanceof List<?> currentList)) {
                    return new PathResult(null, null);
                }
                
                List<Object> listCurrent = (List<Object>) currentList;
                
                // Check bounds
                if (adjustedIdx >= listCurrent.size()) {
                    if (!createIfAbsent) {
                        return new PathResult(null, null);
                    }
                    if (!safeExtendContainer(listCurrent, adjustedIdx, false)) {
                        return new PathResult(null, null);
                    }
                }
                
                try {
                    current = listCurrent.get(adjustedIdx);
                } catch (IndexOutOfBoundsException e) {
                    return new PathResult(null, null);
                }
                
                if (current != null && !(current instanceof List)) {
                    return new PathResult(null, null);
                }
            }
        }
        
        // Process final index
        if (!(current instanceof List<?> currentList)) {
            return new PathResult(null, null);
        }
        
        List<Object> finalContainer = (List<Object>) currentList;
        int finalIdx = indexes.get(indexes.size() - 1);
        int adjustedFinalIdx;
        
        if (finalIdx < 0) {
            adjustedFinalIdx = finalIdx + finalContainer.size();
            if (adjustedFinalIdx < 0) {
                return new PathResult(null, null);
            }
        } else {
            adjustedFinalIdx = finalIdx;
            if (adjustedFinalIdx > 10000) {
                throw new IllegalArgumentException("Index must be between [0,10000]");
            }
        }
        
        // Check final index bounds
        if (adjustedFinalIdx >= finalContainer.size()) {
            if (!createIfAbsent) {
                return new PathResult(null, null);
            }
            if (!safeExtendContainer(finalContainer, adjustedFinalIdx, true)) {
                return new PathResult(null, null);
            }
        }
        
        return new PathResult(adjustedFinalIdx, finalContainer);
    }
    
    /**
     * Safely extends a list container to accommodate a target index.
     * 
     * @param container the list to extend
     * @param targetIndex the target index
     * @param isFinalIndex whether this is the final index (determines fill value)
     * @return true if extension was successful
     */
    private static boolean safeExtendContainer(List<Object> container, int targetIndex, boolean isFinalIndex) {
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
            // Fill intermediate positions with null
            while (container.size() < targetIndex) {
                container.add(null);
            }
            
            // Append appropriate value at target position
            if (isFinalIndex) {
                container.add(new HashMap<>());
            } else {
                container.add(new ArrayList<>());
            }
            
            return true;
        } catch (OutOfMemoryError e) {
            return false;
        }
    }
    
    /**
     * Creates a deep copy of a Map.
     * 
     * @param source the source map
     * @return deep copy of the map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                copy.put(entry.getKey(), deepCopyMap((Map<String, Object>) value));
            } else if (value instanceof List) {
                copy.put(entry.getKey(), deepCopyList((List<Object>) value));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }
    
    /**
     * Creates a deep copy of a List.
     * 
     * @param source the source list
     * @return deep copy of the list
     */
    @SuppressWarnings("unchecked")
    public static List<Object> deepCopyList(List<Object> source) {
        if (source == null) {
            return null;
        }
        List<Object> copy = new ArrayList<>();
        for (Object item : source) {
            if (item instanceof Map) {
                copy.add(deepCopyMap((Map<String, Object>) item));
            } else if (item instanceof List) {
                copy.add(deepCopyList((List<Object>) item));
            } else {
                copy.add(item);
            }
        }
        return copy;
    }
    
    /**
     * Result of a path navigation operation.
     * 
     * @param key the final key (String or Integer)
     * @param container the container holding the key
     */
    public record PathResult(Object key, Object container) {
    }
}

