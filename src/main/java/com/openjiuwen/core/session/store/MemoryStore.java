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
 */
public class MemoryStore extends Store {

    private Map<String, Object> data = new HashMap<>();

    @Override
    public Object read(Object key) {
        return SessionUtils.getBySchema(key, data);
    }

    @Override
    public void write(Map<String, Object> value) {
        SessionUtils.updateDict(value, data);
    }

    /**
     * Get the underlying data map.
     */
    public Map<String, Object> getData() {
        return data;
    }
}
