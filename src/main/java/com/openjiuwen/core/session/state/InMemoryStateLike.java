/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import com.openjiuwen.core.session.utils.SessionUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Mirrors Python's {@code InMemoryStateLike} in
 * {@code openjiuwen/core/session/state/base.py}.
 */
public class InMemoryStateLike implements StateLike {

    private Map<String, Object> state;

    public InMemoryStateLike() {
        this.state = new LinkedHashMap<>();
    }

    public InMemoryStateLike(Map<String, Object> initialState) {
        this.state = initialState == null ? new LinkedHashMap<>() : deepCopyMap(initialState);
    }

    @Override
    public synchronized Object get(Object key) {
        return deepCopy(SessionUtils.getBySchema(key, state));
    }

    @Override
    public synchronized Object getByPrefix(Object key, String nestedPrefix) {
        return deepCopy(SessionUtils.getBySchema(key, state, nestedPrefix, true));
    }

    @Override
    public synchronized Object getByTransformer(Function<Object, Object> transformer) {
        return transformer.apply(state);
    }

    @Override
    public synchronized void update(Map<String, Object> data) {
        SessionUtils.updateDict(deepCopyMap(data), state);
    }

    @Override
    public synchronized Map<String, Object> getState() {
        return deepCopyMap(state);
    }

    @Override
    public synchronized void setState(Map<String, Object> newState) {
        if (newState != null && !newState.isEmpty()) {
            this.state = newState;
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        return (Map<String, Object>) deepCopy(source);
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopy(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return value;
    }
}
