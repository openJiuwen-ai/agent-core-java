/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import java.util.*;

/**
 * Agent builder utility methods.
 * <p>
 * Mirrors Python's {@code utils} in
 * {@code openjiuwen.dev_tools.agent_builder.utils.utils}.
 */
public final class AgentBuilderUtils {

    private AgentBuilderUtils() {
    }

    /** Merge two lists of dicts by key. */
    public static List<Map<String, Object>> mergeDictLists(List<Map<String, Object>> a,
                                                            List<Map<String, Object>> b,
                                                            String keyField) {
        if (a == null || a.isEmpty()) return b != null ? b : Collections.emptyList();
        if (b == null || b.isEmpty()) return new ArrayList<>(a);

        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> item : a) {
            Object key = item.get(keyField);
            if (key != null) merged.put(key.toString(), new LinkedHashMap<>(item));
        }
        for (Map<String, Object> item : b) {
            Object key = item.get(keyField);
            if (key != null) {
                merged.merge(key.toString(), new LinkedHashMap<>(item), (existing, newVal) -> {
                    existing.putAll(newVal);
                    return existing;
                });
            }
        }
        return new ArrayList<>(merged.values());
    }
}
