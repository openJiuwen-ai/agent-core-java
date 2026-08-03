/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime helpers for the Python {@code JSONLike} union alias.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.graph.extraction.custom_types}
 * in {@code openjiuwen/core/memory/graph/extraction/custom_types.py}.</p>
 */
public final class JsonLike {

    private JsonLike() {
    }

    public static boolean isJsonLike(Object value) {
        return value instanceof Map<?, ?> || value instanceof List<?>;
    }

    @SuppressWarnings("unchecked")
    public static Optional<Map<String, Object>> asObject(Object value) {
        if (value instanceof Map<?, ?>) {
            return Optional.of((Map<String, Object>) value);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public static Optional<List<Object>> asList(Object value) {
        if (value instanceof List<?>) {
            return Optional.of((List<Object>) value);
        }
        return Optional.empty();
    }
}
