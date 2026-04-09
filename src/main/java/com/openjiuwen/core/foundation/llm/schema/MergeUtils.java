/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.foundation.llm.schema;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for merging streaming message chunks and parser content.
 * <p>
 * Mirrors Python's merge helper functions in {@code message_chunk.py}.
 */
public final class MergeUtils {

    private MergeUtils() {
        // Utility class
    }

    /**
     * Interface for objects that support merging (Java equivalent of Python's {@code __add__}).
     *
     * @param <T> the concrete type
     */
    public interface Mergeable<T> {
        /**
         * Merges this object with another of the same type.
         *
         * @param other the other object to merge with
         * @return the merged result
         */
        T mergeWith(T other);
    }

    /**
     * Intelligently merge parser_content fields.
     * <p>
     * Merge strategy:
     * <ul>
     *   <li>If right is null, return left</li>
     *   <li>If left is null, return right</li>
     *   <li>If both are strings, concatenate</li>
     *   <li>If both are lists, concatenate</li>
     *   <li>If both are maps, recursively merge</li>
     *   <li>If both implement {@link Mergeable} and are the same type, delegate to {@code mergeWith}</li>
     *   <li>If both are same-type POJOs, merge field-by-field via {@link #mergeObjects}</li>
     *   <li>Otherwise, return right (keep latest)</li>
     * </ul>
     *
     * @param left  the left content to merge
     * @param right the right content to merge
     * @return the merged content
     */
    @SuppressWarnings("unchecked")
    public static Object mergeParserContent(Object left, Object right) {
        if (right == null) {
            return left;
        }
        if (left == null) {
            return right;
        }

        // String concatenation
        if (left instanceof String ls && right instanceof String rs) {
            return ls + rs;
        }

        // List concatenation
        if (left instanceof List<?> ll && right instanceof List<?> rl) {
            var merged = new java.util.ArrayList<Object>(ll);
            merged.addAll(rl);
            return merged;
        }

        // Map recursive merge
        if (left instanceof Map<?, ?> lm && right instanceof Map<?, ?> rm) {
            return mergeMaps((Map<String, Object>) lm, (Map<String, Object>) rm);
        }

        // Mergeable interface (Java equivalent of Python __add__)
        if (left instanceof Mergeable && left.getClass() == right.getClass()) {
            return ((Mergeable<Object>) left).mergeWith(right);
        }

        // Same-type POJO field-level merge (Java equivalent of merge_pydantic_models)
        if (left.getClass() == right.getClass()
                && !isPrimitiveOrWrapper(left.getClass())) {
            Object merged = mergeObjects(left, right);
            if (merged != null) {
                return merged;
            }
        }

        // Otherwise keep the latest value
        return right;
    }

    /**
     * Recursively merge two maps.
     * <p>
     * For the same key: strings are concatenated, lists are concatenated,
     * maps are recursively merged, otherwise the right value wins.
     *
     * @param left  the left map to merge
     * @param right the right map to merge
     * @return the merged map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeMaps(Map<String, Object> left, Map<String, Object> right) {
        var result = new LinkedHashMap<>(left);
        for (var entry : right.entrySet()) {
            String key = entry.getKey();
            Object rightValue = entry.getValue();

            if (result.containsKey(key)) {
                Object leftValue = result.get(key);

                if (leftValue instanceof String ls && rightValue instanceof String rs) {
                    result.put(key, ls + rs);
                } else if (leftValue instanceof List<?> ll && rightValue instanceof List<?> rl) {
                    var merged = new java.util.ArrayList<Object>(ll);
                    merged.addAll(rl);
                    result.put(key, merged);
                } else if (leftValue instanceof Map<?, ?> lm && rightValue instanceof Map<?, ?> rm) {
                    result.put(key, mergeMaps((Map<String, Object>) lm, (Map<String, Object>) rm));
                } else {
                    result.put(key, rightValue);
                }
            } else {
                result.put(key, rightValue);
            }
        }
        return result;
    }

    /**
     * Merge two same-type POJO instances field-by-field using JavaBeans introspection.
     * <p>
     * Mirrors Python's {@code merge_pydantic_models}: iterates all readable/writable
     * properties, and for each field applies {@link #mergeParserContent} to recursively
     * merge strings, lists, maps, and nested objects.
     *
     * @param left  the base object
     * @param right the object whose non-null fields override/merge into left
     * @param <T>   the object type
     * @return new merged instance, or {@code null} if merge is not possible
     */
    @SuppressWarnings("unchecked")
    public static <T> T mergeObjects(T left, T right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        if (left.getClass() != right.getClass()) {
            return right;
        }

        try {
            Class<?> clazz = left.getClass();
            T result = (T) clazz.getDeclaredConstructor().newInstance();
            PropertyDescriptor[] descriptors = Introspector.getBeanInfo(clazz, Object.class)
                    .getPropertyDescriptors();

            for (PropertyDescriptor pd : descriptors) {
                Method getter = pd.getReadMethod();
                Method setter = pd.getWriteMethod();
                if (getter == null || setter == null) {
                    continue;
                }

                Object leftValue = getter.invoke(left);
                Object rightValue = getter.invoke(right);
                Object mergedValue = mergeParserContent(leftValue, rightValue);
                setter.invoke(result, mergedValue);
            }
            return result;
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // If reflective merge fails, fall back to returning right
            return right;
        } catch (Exception e) {
            return right;
        }
    }

    private static boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == Boolean.class
                || clazz == Byte.class
                || clazz == Character.class
                || clazz == Short.class
                || clazz == Integer.class
                || clazz == Long.class
                || clazz == Float.class
                || clazz == Double.class
                || clazz == String.class
                || Number.class.isAssignableFrom(clazz);
    }
}
