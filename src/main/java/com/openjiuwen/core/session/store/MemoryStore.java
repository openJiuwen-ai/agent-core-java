/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.store;

import com.openjiuwen.core.session.utils.SessionUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory store backed by a HashMap.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.store.MemoryStore}.
 * 
 * @since 0.1.7
 */
public class MemoryStore extends Store {
    private Map<String, Object> data = new HashMap<>();

    /**
     * read.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object read(Object key) {
        return SessionUtils.getBySchema(key, data);
    }

    /**
     * write.
     * 
     * @param value value
     * @since 0.1.7
     */
    @Override
    public void write(Map<String, Object> value) {
        SessionUtils.updateDict(value, data);
    }

    /**
     * Get the underlying data map.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getData() {
        return data;
    }
}
