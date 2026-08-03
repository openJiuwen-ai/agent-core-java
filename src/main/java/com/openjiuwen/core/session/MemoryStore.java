/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.utils.SessionUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code MemoryStore} in
 * {@code openjiuwen/core/session/store.py}.
 */
public class MemoryStore implements Store {
    private final Map<String, Object> data = new LinkedHashMap<>();

    @Override
    public Object read(Object key) {
        return SessionUtils.getBySchema(key, data);
    }

    @Override
    public void write(Map<String, Object> value) {
        SessionUtils.updateDict(value, data);
    }
}
