/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import com.openjiuwen.core.session.utils.SessionUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * In-memory implementation of StateLike.
 * <p>
 * Mirrors Python's {@code InMemoryStateLike}.
 * 
 * @since 0.1.7
 */
public class InMemoryStateLike implements StateLike {
    private Map<String, Object> state;

    /**
     * InMemoryStateLike.
     * 
     * @since 0.1.7
     */
    public InMemoryStateLike() {
        this.state = new LinkedHashMap<>();
    }

    /**
     * InMemoryStateLike.
     * 
     * @param initialState initialState
     * @since 0.1.7
     */
    public InMemoryStateLike(Map<String, Object> initialState) {
        this.state = initialState != null ? new LinkedHashMap<>(initialState) : new LinkedHashMap<>();
    }

    /**
     * get.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public synchronized Object get(Object key) {
        return deepCopy(SessionUtils.getBySchema(key, state));
    }

    /**
     * getByPrefix.
     * 
     * @param key key
     * @param nestedPrefix nestedPrefix
     * @return the result
     * @since 0.1.7
     */
    @Override
    public synchronized Object getByPrefix(Object key, String nestedPrefix) {
        return deepCopy(SessionUtils.getBySchema(key, state, nestedPrefix, true));
    }

    /**
     * getByTransformer.
     * 
     * @param transformer transformer
     * @return the result
     * @since 0.1.7
     */
    @Override
    public synchronized Object getByTransformer(Function<Object, Object> transformer) {
        return transformer.apply(state);
    }

    /**
     * update.
     * 
     * @param data data
     * @since 0.1.7
     */
    @Override
    public synchronized void update(Map<String, Object> data) {
        SessionUtils.updateDict(deepCopyMap(data), state);
    }

    /**
     * getState.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public synchronized Map<String, Object> getState() {
        return deepCopyMap(state);
    }

    /**
     * setState.
     * 
     * @param newState newState
     * @since 0.1.7
     */
    @Override
    public synchronized void setState(Map<String, Object> newState) {
        if (newState != null) {
            this.state = newState;
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * deepCopy.
     * 
     * @param obj obj
     * @return the result
     * @since 0.1.7
     */
    private static Object deepCopy(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
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
            return new LinkedHashMap<>();
        }
        return (Map<String, Object>) deepCopy(source);
    }
}
