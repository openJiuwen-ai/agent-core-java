/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import java.util.Map;

/**
 * Mirrors Python's {@code FileStore} in
 * {@code openjiuwen/core/session/store.py}.
 *
 * <p>The current Python implementation is still a stub, so read/write remain
 * intentionally non-operative here as well.</p>
 */
public class FileStore implements Store {

    @Override
    public Object read(Object key) {
        return null;
    }

    @Override
    public void write(Map<String, Object> value) {
        // Intentionally no-op to mirror the current Python stub.
    }
}
