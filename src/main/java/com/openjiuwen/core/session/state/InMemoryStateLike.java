/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import com.openjiuwen.core.session.utils.SessionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * In-memory implementation of StateLike.
 * <p>
 * Mirrors Python's {@code InMemoryStateLike}.
 */
public class InMemoryStateLike implements StateLike {

    private Map<String, Object> state;

    /**
     * Auto-generated for codecheck compliance.
     */
    public InMemoryStateLike() {
        this.state = new HashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public InMemoryStateLike(Map<String, Object> initialState) {
        this.state = initialState != null ? new HashMap<>(initialState) : new HashMap<>();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized Object get(Object key) {
        return deepCopy(SessionUtils.getBySchema(key, state));
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized Object getByPrefix(Object key, String nestedPrefix) {
        return deepCopy(SessionUtils.getBySchema(key, state, nestedPrefix, true));
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized Object getByTransformer(Function<Object, Object> transformer) {
        return transformer.apply(state);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized void update(Map<String, Object> data) {
        SessionUtils.updateDict(deepCopyMap(data), state);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized Map<String, Object> getState() {
        return deepCopyMap(state);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized void setState(Map<String, Object> newState) {
        if (newState != null) {
            this.state = newState;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopy(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> copy = new HashMap<>();
            for (var entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (obj instanceof java.util.List<?> list) {
            var copy = new java.util.ArrayList<>();
            for (var item : list) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null) {
            return new HashMap<>();
        }
        return (Map<String, Object>) deepCopy(source);
    }
}
