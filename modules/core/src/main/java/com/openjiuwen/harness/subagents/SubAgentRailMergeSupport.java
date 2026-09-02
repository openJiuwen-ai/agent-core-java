/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * SubAgentRailMergeSupport
 *
 * @since 0.1.7
 */
final class SubAgentRailMergeSupport {
    /**
     * SubAgentRailMergeSupport.
     * 
     * @since 0.1.7
     */
    private SubAgentRailMergeSupport() {
    }

    static List<Object> mergeRails(List<Object> requiredRails, Map<String, Object> factoryKwargs) {
        List<Object> required = copy(requiredRails);
        List<Object> custom = customRails(factoryKwargs);
        if (custom.isEmpty()) {
            return required;
        }
        return switch (mergeMode(factoryKwargs)) {
            case REPLACE -> custom;
            case APPEND -> dedupeByClass(concat(required, custom));
            case PREPEND, REQUIRED -> dedupeByClass(concat(custom, required));
        };
    }

    /**
     * mergeMode.
     * 
     * @param factoryKwargs factoryKwargs
     * @return the result
     * @since 0.1.7
     */
    private static MergeMode mergeMode(Map<String, Object> factoryKwargs) {
        Object raw = first(factoryKwargs, new String[]{"rails_merge_mode", "rail_merge_mode", "custom_rails_merge"});
        if (!(raw instanceof String text) || text.isBlank()) {
            return MergeMode.REQUIRED;
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "replace", "override", "custom_only" -> MergeMode.REPLACE;
            case "append", "required_first" -> MergeMode.APPEND;
            case "prepend", "custom_first" -> MergeMode.PREPEND;
            case "required", "python_required", "merge_required" -> MergeMode.REQUIRED;
            default -> MergeMode.REQUIRED;
        };
    }

    /**
     * customRails.
     * 
     * @param factoryKwargs factoryKwargs
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> customRails(Map<String, Object> factoryKwargs) {
        Object raw = first(factoryKwargs, new String[]{"custom_rails", "rails", "extra_rails"});
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).map(Object.class::cast).toList();
        }
        if (raw.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(raw);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                Object item = java.lang.reflect.Array.get(raw, i);
                if (item != null) {
                    result.add(item);
                }
            }
            return result;
        }
        return List.of(raw);
    }

    /**
     * first.
     * 
     * @param values values
     * @param keys keys
     * @return the result
     * @since 0.1.7
     */
    private static Object first(Map<String, Object> values, String[] keys) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return "";
    }

    /**
     * concat.
     * 
     * @param first first
     * @param second second
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> concat(List<Object> first, List<Object> second) {
        List<Object> merged = new ArrayList<>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return merged;
    }

    /**
     * copy.
     * 
     * @param items items
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> copy(List<Object> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        return items.stream().filter(Objects::nonNull).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * dedupeByClass.
     * 
     * @param rails rails
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> dedupeByClass(List<Object> rails) {
        Map<Class<?>, Object> deduped = new LinkedHashMap<>();
        for (Object rail : rails) {
            if (rail != null) {
                deduped.putIfAbsent(rail.getClass(), rail);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private enum MergeMode {
        REQUIRED,
        APPEND,
        PREPEND,
        REPLACE
    }
}
